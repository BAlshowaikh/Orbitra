/*
  BookingService.java
  Business logic for creating and cancelling bookings - calls Hotel/Flight
  Service's reserve/release endpoints (via HotelServiceClient/
  FlightServiceClient) before ever writing a local Booking row, so a
  PENDING booking only ever exists here if the other service actually
  confirmed the reservation.
*/
package com.orbitra.booking_service.service;

// --------------- IMPORTS ---------------
import com.orbitra.booking_service.client.FlightServiceClient;
import com.orbitra.booking_service.client.HotelServiceClient;
import com.orbitra.booking_service.dto.BookingResponse;
import com.orbitra.booking_service.dto.FlightBookingRequest;
import com.orbitra.booking_service.dto.FlightBookingResponse;
import com.orbitra.booking_service.dto.HotelBookingRequest;
import com.orbitra.booking_service.dto.HotelBookingResponse;
import com.orbitra.booking_service.dto.PagedResponse;
import com.orbitra.booking_service.exception.BookingNotFoundException;
import com.orbitra.booking_service.exception.InvalidBookingStateException;
import com.orbitra.booking_service.model.Booking;
import com.orbitra.booking_service.model.BookingStatus;
import com.orbitra.booking_service.model.BookingType;
import com.orbitra.booking_service.model.FlightBooking;
import com.orbitra.booking_service.model.HotelBooking;
import com.orbitra.booking_service.repository.BookingRepository;
import com.orbitra.booking_service.repository.FlightBookingRepository;
import com.orbitra.booking_service.repository.HotelBookingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final HotelBookingRepository hotelBookingRepository;
    private final FlightBookingRepository flightBookingRepository;
    private final HotelServiceClient hotelServiceClient;
    private final FlightServiceClient flightServiceClient;

    public BookingService(BookingRepository bookingRepository, HotelBookingRepository hotelBookingRepository,
                           FlightBookingRepository flightBookingRepository, HotelServiceClient hotelServiceClient,
                           FlightServiceClient flightServiceClient) {
        this.bookingRepository = bookingRepository;
        this.hotelBookingRepository = hotelBookingRepository;
        this.flightBookingRepository = flightBookingRepository;
        this.hotelServiceClient = hotelServiceClient;
        this.flightServiceClient = flightServiceClient;
    }

    // ---------------- METHOD 1: Book a hotel room (TRAVELER) ----------------
    @Transactional
    public HotelBookingResponse createHotelBooking(Long travelerId, HotelBookingRequest request, String authorizationHeader) {
        // External call first - nothing gets saved locally unless Hotel
        // Service actually confirmed the reservation.
        BigDecimal basePricePerNight = hotelServiceClient.reserve(
                request.hotelId(), request.roomId(), request.checkInDate(), request.checkOutDate(), authorizationHeader
        );

        long nights = ChronoUnit.DAYS.between(request.checkInDate(), request.checkOutDate());
        BigDecimal totalPrice = basePricePerNight.multiply(BigDecimal.valueOf(nights));

        HotelBooking booking = HotelBooking.builder()
                .travelerId(travelerId)
                .status(BookingStatus.PENDING)
                .totalPrice(totalPrice)
                .hotelId(request.hotelId())
                .roomId(request.roomId())
                .checkInDate(request.checkInDate())
                .checkOutDate(request.checkOutDate())
                .build();

        hotelBookingRepository.save(booking);
        return toHotelResponse(booking);
    }

    // ---------------- METHOD 2: Book a flight seat (TRAVELER) ----------------
    @Transactional
    public FlightBookingResponse createFlightBooking(Long travelerId, FlightBookingRequest request, String authorizationHeader) {
        BigDecimal totalPrice = flightServiceClient.reserve(request.flightId(), request.flightSeatId(), authorizationHeader);

        FlightBooking booking = FlightBooking.builder()
                .travelerId(travelerId)
                .status(BookingStatus.PENDING)
                .totalPrice(totalPrice)
                .flightId(request.flightId())
                .flightSeatId(request.flightSeatId())
                .build();

        flightBookingRepository.save(booking);
        return toFlightResponse(booking);
    }

    // ---------------- METHOD 3: Cancel a booking, either type (TRAVELER, owner only) ----------------
    @Transactional
    public void cancel(Long travelerId, Long bookingId, String authorizationHeader) {
        Booking booking = bookingRepository.findByIdAndTravelerId(bookingId, travelerId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException("Only a PENDING booking can be cancelled");
        }

        // Release first, same "external call before local write" ordering as
        // create - only mark CANCELLED once the other service actually
        // confirmed the inventory was given back. Known gap: if release()
        // succeeds but the save below then fails, the booking is stuck
        // PENDING while its inventory has already been released elsewhere -
        // a real distributed-consistency issue that this project's Saga work
        // (Phase 4) is what eventually closes, not something solved here.
        if (booking instanceof HotelBooking hotelBooking) {
            hotelServiceClient.release(
                    hotelBooking.getHotelId(), hotelBooking.getRoomId(),
                    hotelBooking.getCheckInDate(), hotelBooking.getCheckOutDate(), authorizationHeader
            );
        } else if (booking instanceof FlightBooking flightBooking) {
            flightServiceClient.release(flightBooking.getFlightId(), flightBooking.getFlightSeatId(), authorizationHeader);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    // ---------------- METHOD 4: Traveler's own booking history - unified, or filtered by type ----------------
    // typeFilter == null -> one query across both types via BookingRepository
    // (JOINED inheritance resolves each row's concrete class automatically).
    // typeFilter set -> reuses the type-specific repository directly, same
    // query either of the old separate endpoints used.
    @Transactional(readOnly = true)
    public PagedResponse<BookingResponse> getMyBookings(Long travelerId, BookingType typeFilter, Pageable pageable) {
        if (typeFilter == BookingType.HOTEL) {
            Page<HotelBooking> bookings = hotelBookingRepository.findByTravelerId(travelerId, pageable);
            return PagedResponse.from(bookings.map(this::toHotelResponse));
        }
        if (typeFilter == BookingType.FLIGHT) {
            Page<FlightBooking> bookings = flightBookingRepository.findByTravelerId(travelerId, pageable);
            return PagedResponse.from(bookings.map(this::toFlightResponse));
        }

        Page<Booking> bookings = bookingRepository.findByTravelerId(travelerId, pageable);
        return PagedResponse.from(bookings.map(this::toResponse));
    }

    // ---------------- Helpers ----------------

    // ------------- HELPER 0: Convert any Booking to the right sealed BookingResponse variant -------------
    // Booking itself isn't sealed (unlike BookingResponse) - JPA entities
    // aren't sealed here to avoid conflicts with Hibernate's runtime proxy
    // generation - so this switch still needs a default, even though only
    // two concrete subclasses actually exist in practice.
    private BookingResponse toResponse(Booking booking) {
        return switch (booking) {
            case HotelBooking hotelBooking -> toHotelResponse(hotelBooking);
            case FlightBooking flightBooking -> toFlightResponse(flightBooking);
            default -> throw new IllegalStateException("Unknown Booking subtype: " + booking.getClass());
        };
    }

    // ------------- HELPER 1: Convert HotelBooking to HotelBookingResponse -------------
    private HotelBookingResponse toHotelResponse(HotelBooking booking) {
        return new HotelBookingResponse(
                booking.getId(), booking.getTravelerId(), booking.getStatus(), booking.getTotalPrice(),
                booking.getHotelId(), booking.getRoomId(), booking.getCheckInDate(), booking.getCheckOutDate(),
                booking.getCreatedAt()
        );
    }

    // ------------- HELPER 2: Convert FlightBooking to FlightBookingResponse -------------
    private FlightBookingResponse toFlightResponse(FlightBooking booking) {
        return new FlightBookingResponse(
                booking.getId(), booking.getTravelerId(), booking.getStatus(), booking.getTotalPrice(),
                booking.getFlightId(), booking.getFlightSeatId(), booking.getCreatedAt()
        );
    }
}

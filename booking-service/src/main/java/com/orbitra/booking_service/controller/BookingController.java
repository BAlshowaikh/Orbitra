/*
  BookingController.java
  Thin HTTP layer for bookings - every route requires ROLE_TRAVELER
  (SecurityConfig), no ownership header/param needed since travelerId always
  comes from the caller's own JWT. Business logic lives entirely in
  BookingService.
*/
package com.orbitra.booking_service.controller;

// ----------- IMPORTS -----------
import com.orbitra.booking_service.dto.BookingResponse;
import com.orbitra.booking_service.dto.FlightBookingRequest;
import com.orbitra.booking_service.dto.FlightBookingResponse;
import com.orbitra.booking_service.dto.HotelBookingRequest;
import com.orbitra.booking_service.dto.HotelBookingResponse;
import com.orbitra.booking_service.dto.PagedResponse;
import com.orbitra.booking_service.model.BookingType;
import com.orbitra.booking_service.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    // ------------------ Endpoint 1: Book a hotel room -----------------
    @PostMapping("/hotel-rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public HotelBookingResponse createHotelBooking(
            Authentication authentication, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @Valid @RequestBody HotelBookingRequest request
    ) {
        return bookingService.createHotelBooking(extractAccountId(authentication), request, authorizationHeader);
    }

    // ------------------ Endpoint 2: Book a flight seat -----------------
    @PostMapping("/flight-seats")
    @ResponseStatus(HttpStatus.CREATED)
    public FlightBookingResponse createFlightBooking(
            Authentication authentication, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @Valid @RequestBody FlightBookingRequest request
    ) {
        return bookingService.createFlightBooking(extractAccountId(authentication), request, authorizationHeader);
    }

    // ------------------ Endpoint 3: Cancel a booking, either type (owner only) -----------------
    @PatchMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            Authentication authentication, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @PathVariable Long id
    ) {
        bookingService.cancel(extractAccountId(authentication), id, authorizationHeader);
    }

    // ------------------ Endpoint 4: My booking history - unified, optionally filtered by type -----------------
    // No type param = every booking, mixed hotel + flight, sorted however
    // Pageable's sort says. ?type=HOTEL or ?type=FLIGHT narrows to one kind.
    @GetMapping("/mine")
    public PagedResponse<BookingResponse> getMyBookings(
            Authentication authentication, @RequestParam(required = false) BookingType type,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return bookingService.getMyBookings(extractAccountId(authentication), type, pageable);
    }

    private Long extractAccountId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}

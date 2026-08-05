/*
  FlightService.java
  Business logic for flight listings: create/update/status by the owning
  partner (or admin for status), the partner's own listing view, public
  search, and the public detail view.
*/
package com.orbitra.flight_service.service;

// --------------- IMPORTS ---------------
import com.orbitra.flight_service.dto.FlightDetailResponse;
import com.orbitra.flight_service.dto.FlightRequest;
import com.orbitra.flight_service.dto.FlightResponse;
import com.orbitra.flight_service.dto.FlightSearchResultResponse;
import com.orbitra.flight_service.dto.FlightSeatResponse;
import com.orbitra.flight_service.dto.PagedResponse;
import com.orbitra.flight_service.exception.DuplicateFlightNumberException;
import com.orbitra.flight_service.exception.FlightNotFoundException;
import com.orbitra.flight_service.exception.ForbiddenException;
import com.orbitra.flight_service.exception.InvalidRequestException;
import com.orbitra.flight_service.model.Flight;
import com.orbitra.flight_service.model.FlightSeat;
import com.orbitra.flight_service.repository.FlightRepository;
import com.orbitra.flight_service.repository.FlightSeatRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FlightService {

    private final FlightRepository flightRepository;
    private final FlightSeatRepository flightSeatRepository;

    public FlightService(FlightRepository flightRepository, FlightSeatRepository flightSeatRepository) {
        this.flightRepository = flightRepository;
        this.flightSeatRepository = flightSeatRepository;
    }

    // ---------------- METHOD 1: Create a flight (PARTNER_FLIGHT) ----------------
    @Transactional
    public FlightResponse create(Long ownerId, FlightRequest request) {
        if (flightRepository.existsByFlightNumber(request.flightNumber())) {
            throw new DuplicateFlightNumberException("Flight number already exists: " + request.flightNumber());
        }

        Flight flight = Flight.builder()
                .ownerId(ownerId)
                .flightNumber(request.flightNumber())
                .originCode(request.originCode())
                .destinationCode(request.destinationCode())
                .departureTime(request.departureTime())
                .arrivalTime(request.arrivalTime())
                .durationMinutes(request.durationMinutes())
                .seatCount(request.seatCount())
                .active(true)
                .build();

        flightRepository.save(flight);
        return toResponse(flight);
    }

    // ---------------- METHOD 2: Update a flight (owner only, partial update) ----------------
    @Transactional
    public FlightResponse update(Long callerId, Long flightId, JsonNode body) {
        Flight flight = getOwnedFlight(flightId, callerId);

        if (body.has("flightNumber")) {
            String flightNumber = requireNonBlank(body, "flightNumber");
            if (!flightNumber.equals(flight.getFlightNumber()) && flightRepository.existsByFlightNumber(flightNumber)) {
                throw new DuplicateFlightNumberException("Flight number already exists: " + flightNumber);
            }
            flight.setFlightNumber(flightNumber);
        }
        if (body.has("originCode")) {
            flight.setOriginCode(requireNonBlank(body, "originCode"));
        }
        if (body.has("destinationCode")) {
            flight.setDestinationCode(requireNonBlank(body, "destinationCode"));
        }
        if (body.has("departureTime")) {
            flight.setDepartureTime(LocalDateTime.parse(requireNonBlank(body, "departureTime")));
        }
        if (body.has("arrivalTime")) {
            flight.setArrivalTime(LocalDateTime.parse(requireNonBlank(body, "arrivalTime")));
        }
        if (body.has("durationMinutes")) {
            flight.setDurationMinutes(requirePositiveInt(body, "durationMinutes"));
        }
        if (body.has("seatCount")) {
            int seatCount = requirePositiveInt(body, "seatCount");
            // Can't shrink below what's already allocated across this flight's
            // seat classes - same ceiling FlightSeatService checks from the
            // other direction (a new/updated seat vs. a fixed seatCount).
            int allocated = flightSeatRepository.sumTotalInventoryByFlightIdExcluding(flightId, null);
            if (seatCount < allocated) {
                throw new InvalidRequestException(
                        "seatCount cannot be less than " + allocated + " seats already allocated to this flight's seat classes"
                );
            }
            flight.setSeatCount(seatCount);
        }

        flightRepository.save(flight);
        return toResponse(flight);
    }

    // ---------------- METHOD 3: Activate/deactivate (owner or admin) ----------------
    @Transactional
    public FlightResponse updateStatus(Long callerId, boolean isAdmin, Long flightId, boolean active) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new FlightNotFoundException("Flight not found: " + flightId));

        if (!isAdmin && !flight.getOwnerId().equals(callerId)) {
            throw new ForbiddenException("You do not own this flight");
        }

        flight.setActive(active);
        flightRepository.save(flight);
        return toResponse(flight);
    }

    // ---------------- METHOD 4: Partner's own listings (GET /flights/mine) ----------------
    @Transactional(readOnly = true)
    public PagedResponse<FlightResponse> getMyFlights(Long ownerId, Pageable pageable) {
        Page<Flight> flights = flightRepository.findByOwnerId(ownerId, pageable);
        return PagedResponse.from(flights.map(this::toResponse));
    }

    // ---------------- METHOD 5: Public search (GET /flights) ----------------
    @Transactional(readOnly = true)
    public PagedResponse<FlightSearchResultResponse> search(
            String originCode, String destinationCode, LocalDate travelDate,
            Integer passengers, String seatClassName, BigDecimal minPrice, BigDecimal maxPrice,
            Pageable pageable
    ) {
        // A single day's range, not a checkIn/checkOut pair - a Flight is
        // already one specific dated departure, so there's no ordering to
        // validate here, unlike HotelService.search()'s date-range check.
        LocalDateTime dayStart = travelDate != null ? travelDate.atStartOfDay() : null;
        LocalDateTime dayEnd = travelDate != null ? travelDate.plusDays(1).atStartOfDay() : null;

        Page<Flight> flights = flightRepository.search(
                originCode, destinationCode, dayStart, dayEnd, passengers, seatClassName, minPrice, maxPrice, pageable
        );
        return PagedResponse.from(flights.map(this::toSearchResult));
    }

    // ---------------- METHOD 6: Public detail view (GET /flights/{id}) ----------------
    @Transactional(readOnly = true)
    public FlightDetailResponse getDetail(Long flightId) {
        Flight flight = flightRepository.findById(flightId)
                .filter(Flight::isActive)
                .orElseThrow(() -> new FlightNotFoundException("Flight not found: " + flightId));

        List<FlightSeatResponse> seats = flightSeatRepository.findByFlightIdAndActiveTrue(flightId).stream()
                .map(this::toFlightSeatResponse)
                .toList();

        return new FlightDetailResponse(
                flight.getId(), flight.getFlightNumber(), flight.getOriginCode(), flight.getDestinationCode(),
                flight.getDepartureTime(), flight.getArrivalTime(), flight.getDurationMinutes(), seats
        );
    }

    // ---------------- Helpers ----------------

    // ------------- HELPER 1: Get a flight and ensure the caller owns it (or throw ForbiddenException) -------------
    private Flight getOwnedFlight(Long flightId, Long callerId) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new FlightNotFoundException("Flight not found: " + flightId));

        if (!flight.getOwnerId().equals(callerId)) {
            throw new ForbiddenException("You do not own this flight");
        }
        return flight;
    }

    // ------------- HELPER 2: Require a non-blank field in update()'s body -------------
    private String requireNonBlank(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || node.asString().isBlank()) {
            throw new InvalidRequestException(field + " must not be blank");
        }
        return node.asString();
    }

    // ------------- HELPER 3: Require a positive integer field in update()'s body -------------
    private Integer requirePositiveInt(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || !node.isNumber() || node.asInt() <= 0) {
            throw new InvalidRequestException(field + " must be a positive integer");
        }
        return node.asInt();
    }

    // ------------- HELPER 4: Convert Flight to FlightResponse -------------
    private FlightResponse toResponse(Flight flight) {
        return new FlightResponse(
                flight.getId(), flight.getOwnerId(), flight.getFlightNumber(), flight.getOriginCode(),
                flight.getDestinationCode(), flight.getDepartureTime(), flight.getArrivalTime(),
                flight.getDurationMinutes(), flight.getSeatCount(), flight.isActive(), flight.getCreatedAt()
        );
    }

    // ------------- HELPER 5: Convert Flight to FlightSearchResultResponse -------------
    private FlightSearchResultResponse toSearchResult(Flight flight) {
        BigDecimal minPrice = flightSeatRepository.findMinActivePriceByFlightId(flight.getId()).orElse(null);
        return new FlightSearchResultResponse(
                flight.getId(), flight.getFlightNumber(), flight.getOriginCode(), flight.getDestinationCode(),
                flight.getDepartureTime(), flight.getArrivalTime(), minPrice
        );
    }

    // ------------- HELPER 6: Convert FlightSeat to FlightSeatResponse -------------
    private FlightSeatResponse toFlightSeatResponse(FlightSeat flightSeat) {
        return new FlightSeatResponse(
                flightSeat.getId(), flightSeat.getFlight().getId(), flightSeat.getSeatClass().getId(),
                flightSeat.getSeatClass().getName(), flightSeat.getBasePricePerSeat(), flightSeat.getTotalInventory(),
                flightSeat.getAvailableCount(), flightSeat.getFacilities(), flightSeat.isActive()
        );
    }
}

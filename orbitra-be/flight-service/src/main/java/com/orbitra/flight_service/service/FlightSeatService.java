/*
  FlightSeatService.java
  Business logic for a flight's seats - owner-only CRUD, enforcing the
  seatCount ceiling (sum of totalInventory across a flight's seats must not
  exceed Flight.seatCount) and initializing/capping availableCount, which is
  otherwise booking-driven only (Booking Service, not built yet) and never
  partner-editable directly.
*/
package com.orbitra.flight_service.service;

// --------------- IMPORTS ---------------
import com.orbitra.flight_service.dto.FlightSeatRequest;
import com.orbitra.flight_service.dto.FlightSeatResponse;
import com.orbitra.flight_service.exception.DuplicateFlightSeatException;
import com.orbitra.flight_service.exception.FlightNotFoundException;
import com.orbitra.flight_service.exception.FlightSeatNotFoundException;
import com.orbitra.flight_service.exception.ForbiddenException;
import com.orbitra.flight_service.exception.InvalidRequestException;
import com.orbitra.flight_service.exception.SeatClassNotFoundException;
import com.orbitra.flight_service.exception.SeatUnavailableException;
import com.orbitra.flight_service.model.Flight;
import com.orbitra.flight_service.model.FlightSeat;
import com.orbitra.flight_service.model.SeatClass;
import com.orbitra.flight_service.repository.FlightRepository;
import com.orbitra.flight_service.repository.FlightSeatRepository;
import com.orbitra.flight_service.repository.SeatClassRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class FlightSeatService {

    private final FlightSeatRepository flightSeatRepository;
    private final SeatClassRepository seatClassRepository;
    private final FlightRepository flightRepository;

    public FlightSeatService(FlightSeatRepository flightSeatRepository, SeatClassRepository seatClassRepository,
                              FlightRepository flightRepository) {
        this.flightSeatRepository = flightSeatRepository;
        this.seatClassRepository = seatClassRepository;
        this.flightRepository = flightRepository;
    }

    // ---------------- METHOD 1: Add a seat class to a flight (owner only) ----------------
    @Transactional
    public FlightSeatResponse create(Long callerId, Long flightId, FlightSeatRequest request) {
        Flight flight = getOwnedFlight(flightId, callerId);

        SeatClass seatClass = seatClassRepository.findById(request.seatClassId())
                .filter(SeatClass::isActive)
                .orElseThrow(() -> new SeatClassNotFoundException("Seat class not found: " + request.seatClassId()));

        if (flightSeatRepository.existsByFlightIdAndSeatClassId(flightId, request.seatClassId())) {
            throw new DuplicateFlightSeatException("This flight already has a seat class for that seat class");
        }

        requireWithinSeatCount(flight, request.totalInventory(), null);

        FlightSeat flightSeat = FlightSeat.builder()
                .flight(flight)
                .seatClass(seatClass)
                .basePricePerSeat(request.basePricePerSeat())
                .totalInventory(request.totalInventory())
                // availableCount starts equal to totalInventory - booking-driven
                // only from this point on, never set directly by a partner.
                .availableCount(request.totalInventory())
                .facilities(request.facilities() != null ? request.facilities() : List.of())
                .active(true)
                .build();

        flightSeatRepository.save(flightSeat);
        return toResponse(flightSeat);
    }

    // ---------------- METHOD 2: Update a seat (owner only, partial update) ----------------
    @Transactional
    public FlightSeatResponse update(Long callerId, Long flightId, Long seatId, JsonNode body) {
        FlightSeat flightSeat = getOwnedFlightSeat(flightId, seatId, callerId);

        if (body.has("basePricePerSeat")) {
            flightSeat.setBasePricePerSeat(requirePositiveDecimal(body, "basePricePerSeat"));
        }
        if (body.has("totalInventory")) {
            int totalInventory = requireNonNegativeInt(body, "totalInventory");
            requireWithinSeatCount(flightSeat.getFlight(), totalInventory, seatId);
            flightSeat.setTotalInventory(totalInventory);
            // Never let availableCount exceed the new (possibly shrunk) total -
            // it's booking-driven, not directly editable, but still has to stay
            // a sane number relative to totalInventory.
            if (flightSeat.getAvailableCount() > totalInventory) {
                flightSeat.setAvailableCount(totalInventory);
            }
        }
        if (body.has("facilities")) {
            flightSeat.setFacilities(toStringList(body.get("facilities")));
        }

        flightSeatRepository.save(flightSeat);
        return toResponse(flightSeat);
    }

    // ---------------- METHOD 3: Activate/deactivate a seat (owner only) ----------------
    @Transactional
    public FlightSeatResponse updateStatus(Long callerId, Long flightId, Long seatId, boolean active) {
        FlightSeat flightSeat = getOwnedFlightSeat(flightId, seatId, callerId);
        flightSeat.setActive(active);
        flightSeatRepository.save(flightSeat);
        return toResponse(flightSeat);
    }

    // ---------------- METHOD 4: List a flight's seats (public, owner sees inactive too) ----------------
    @Transactional(readOnly = true)
    public List<FlightSeatResponse> getSeats(Long callerId, Long flightId) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new FlightNotFoundException("Flight not found: " + flightId));

        boolean isOwner = callerId != null && flight.getOwnerId().equals(callerId);
        List<FlightSeat> seats = isOwner
                ? flightSeatRepository.findByFlightId(flightId)
                : flightSeatRepository.findByFlightIdAndActiveTrue(flightId);
        return seats.stream().map(this::toResponse).toList();
    }

    // ---------------- METHOD 5: Reserve one seat (called by Booking Service, any TRAVELER) ----------------
    @Transactional
    public FlightSeatResponse reserve(Long flightId, Long seatId) {
        FlightSeat flightSeat = getReservableFlightSeat(flightId, seatId);

        int updated = flightSeatRepository.decrementAvailableCount(seatId);
        if (updated == 0) {
            throw new SeatUnavailableException("No seats left for flight seat: " + seatId);
        }

        // Re-fetch to get the post-decrement availableCount for the response -
        // flightSeat above is now stale (the UPDATE ran outside its managed state).
        return toResponse(flightSeatRepository.findById(seatId).orElseThrow());
    }

    // ---------------- METHOD 6: Release one seat (called by Booking Service, on cancellation) ----------------
    @Transactional
    public FlightSeatResponse release(Long flightId, Long seatId) {
        getReservableFlightSeat(flightId, seatId);

        int updated = flightSeatRepository.incrementAvailableCount(seatId);
        if (updated == 0) {
            // Shouldn't happen if Booking Service only releases what it
            // actually reserved - a safety net against a double-release bug,
            // not an expected user-facing case.
            throw new InvalidRequestException("Flight seat " + seatId + " is already at full availability");
        }

        return toResponse(flightSeatRepository.findById(seatId).orElseThrow());
    }

    // ---------------- Helpers ----------------

    // ------------- HELPER 0: Get a seat under the given flight, both must be active -------------
    private FlightSeat getReservableFlightSeat(Long flightId, Long seatId) {
        FlightSeat flightSeat = flightSeatRepository.findById(seatId)
                .filter(s -> s.getFlight().getId().equals(flightId))
                .orElseThrow(() -> new FlightSeatNotFoundException("Flight seat not found: " + seatId));
        if (!flightSeat.isActive() || !flightSeat.getFlight().isActive()) {
            throw new FlightSeatNotFoundException("Flight seat not found: " + seatId);
        }
        return flightSeat;
    }

    // ------------- HELPER 1: Get a flight and ensure the caller owns it -------------
    private Flight getOwnedFlight(Long flightId, Long callerId) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new FlightNotFoundException("Flight not found: " + flightId));
        if (!flight.getOwnerId().equals(callerId)) {
            throw new ForbiddenException("You do not own this flight");
        }
        return flight;
    }

    // ------------- HELPER 2: Get a seat under the caller's own flight -------------
    private FlightSeat getOwnedFlightSeat(Long flightId, Long seatId, Long callerId) {
        getOwnedFlight(flightId, callerId);
        return flightSeatRepository.findById(seatId)
                .filter(s -> s.getFlight().getId().equals(flightId))
                .orElseThrow(() -> new FlightSeatNotFoundException("Flight seat not found: " + seatId));
    }

    // ------------- HELPER 3: Enforce the seatCount ceiling for a create/update -------------
    // excludeSeatId is null on create (nothing to exclude yet), or the seat
    // being updated (so its own current totalInventory isn't double-counted
    // against the new value being validated).
    private void requireWithinSeatCount(Flight flight, int totalInventory, Long excludeSeatId) {
        int allocated = flightSeatRepository.sumTotalInventoryByFlightIdExcluding(flight.getId(), excludeSeatId);
        if (allocated + totalInventory > flight.getSeatCount()) {
            throw new InvalidRequestException(
                    "totalInventory would exceed this flight's seatCount (" + flight.getSeatCount()
                            + "): " + allocated + " already allocated to other seat classes"
            );
        }
    }

    // ------------- HELPER 4: Numeric field checks for update()'s body -------------
    private Integer requireNonNegativeInt(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || !node.isNumber() || node.asInt() < 0) {
            throw new InvalidRequestException(field + " must be zero or a positive integer");
        }
        return node.asInt();
    }

    private BigDecimal requirePositiveDecimal(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || !node.isNumber() || node.decimalValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException(field + " must be a positive number");
        }
        return node.decimalValue();
    }

    // ------------- HELPER 5: Convert a JSON array node to a List<String> -------------
    private List<String> toStringList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(n -> values.add(n.asString()));
        return values;
    }

    // ------------- HELPER 6: Convert FlightSeat to FlightSeatResponse -------------
    private FlightSeatResponse toResponse(FlightSeat flightSeat) {
        return new FlightSeatResponse(
                flightSeat.getId(), flightSeat.getFlight().getId(), flightSeat.getSeatClass().getId(),
                flightSeat.getSeatClass().getName(), flightSeat.getBasePricePerSeat(), flightSeat.getTotalInventory(),
                flightSeat.getAvailableCount(), flightSeat.getFacilities(), flightSeat.isActive()
        );
    }
}

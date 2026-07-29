/*
  FlightSeatController.java
  Thin HTTP layer for a flight's seats - listing is public (owner sees
  inactive too), everything else is owner-only. No availability-range
  endpoints, unlike RoomController - availableCount is booking-driven only,
  never partner-submitted. Business logic lives entirely in FlightSeatService.
*/
package com.orbitra.flight_service.controller;

// ----------- IMPORTS -----------
import com.orbitra.flight_service.dto.FlightSeatRequest;
import com.orbitra.flight_service.dto.FlightSeatResponse;
import com.orbitra.flight_service.dto.UpdateActiveRequest;
import com.orbitra.flight_service.service.FlightSeatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.List;

@RestController
@RequestMapping("/flights/{flightId}/seats")
public class FlightSeatController {

    private final FlightSeatService flightSeatService;

    public FlightSeatController(FlightSeatService flightSeatService) {
        this.flightSeatService = flightSeatService;
    }

    // ------------------ Endpoint 1: List a flight's seats (public, owner sees inactive too) -----------------
    @GetMapping
    public List<FlightSeatResponse> getSeats(Authentication authentication, @PathVariable Long flightId) {
        return flightSeatService.getSeats(extractAccountIdOrNull(authentication), flightId);
    }

    // ------------------ Endpoint 2: Add a flight seat class -----------------
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlightSeatResponse create(Authentication authentication, @PathVariable Long flightId, @Valid @RequestBody FlightSeatRequest request) {
        return flightSeatService.create(extractAccountId(authentication), flightId, request);
    }

    // ------------------ Endpoint 3: Update a seat (partial update) -----------------
    @PatchMapping("/{seatId}")
    public FlightSeatResponse update(Authentication authentication, @PathVariable Long flightId, @PathVariable Long seatId, @RequestBody JsonNode body) {
        return flightSeatService.update(extractAccountId(authentication), flightId, seatId, body);
    }

    // ------------------ Endpoint 4: Activate/deactivate a seat -----------------
    @PatchMapping("/{seatId}/status")
    public FlightSeatResponse updateStatus(
            Authentication authentication, @PathVariable Long flightId, @PathVariable Long seatId,
            @Valid @RequestBody UpdateActiveRequest request
    ) {
        return flightSeatService.updateStatus(extractAccountId(authentication), flightId, seatId, request.active());
    }

    private Long extractAccountId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }

    // Anonymous callers (this route is public) have a non-Long principal - return null instead of a ClassCastException.
    private Long extractAccountIdOrNull(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            return null;
        }
        return id;
    }
}

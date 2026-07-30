/*
  RoomController.java
  Thin HTTP layer for a hotel's rooms and their availability - listing rooms
  is public (owner sees inactive too), everything else is owner-only.
  Business logic lives entirely in RoomService.
*/
package com.orbitra.hotel_service.controller;

// ----------- IMPORTS -----------
import com.orbitra.hotel_service.dto.AvailabilityRangeRequest;
import com.orbitra.hotel_service.dto.AvailabilityResponse;
import com.orbitra.hotel_service.dto.ReserveRoomRequest;
import com.orbitra.hotel_service.dto.ReserveRoomResponse;
import com.orbitra.hotel_service.dto.RoomRequest;
import com.orbitra.hotel_service.dto.RoomResponse;
import com.orbitra.hotel_service.dto.UpdateActiveRequest;
import com.orbitra.hotel_service.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/hotels/{hotelId}/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    // ------------------ Endpoint 1: List a hotel's rooms (public, owner sees inactive too) -----------------
    @GetMapping
    public List<RoomResponse> getRooms(Authentication authentication, @PathVariable Long hotelId) {
        return roomService.getRooms(extractAccountIdOrNull(authentication), hotelId);
    }

    // ------------------ Endpoint 2: Add a room -----------------
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(Authentication authentication, @PathVariable Long hotelId, @Valid @RequestBody RoomRequest request) {
        return roomService.create(extractAccountId(authentication), hotelId, request);
    }

    // ------------------ Endpoint 3: Update a room (partial update) -----------------
    @PatchMapping("/{roomId}")
    public RoomResponse update(Authentication authentication, @PathVariable Long hotelId, @PathVariable Long roomId, @RequestBody JsonNode body) {
        return roomService.update(extractAccountId(authentication), hotelId, roomId, body);
    }

    // ------------------ Endpoint 4: Activate/deactivate a room -----------------
    @PatchMapping("/{roomId}/status")
    public RoomResponse updateStatus(
            Authentication authentication, @PathVariable Long hotelId, @PathVariable Long roomId,
            @Valid @RequestBody UpdateActiveRequest request
    ) {
        return roomService.updateStatus(extractAccountId(authentication), hotelId, roomId, request.active());
    }

    // ------------------ Endpoint 5: Get availability calendar -----------------
    @GetMapping("/{roomId}/availability")
    public List<AvailabilityResponse> getAvailability(
            Authentication authentication, @PathVariable Long hotelId, @PathVariable Long roomId,
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate
    ) {
        return roomService.getAvailability(extractAccountId(authentication), hotelId, roomId, startDate, endDate);
    }

    // ------------------ Endpoint 6: Set availability for a date range -----------------
    // Full-replace semantics (not merge-patch) - every field on
    // AvailabilityRangeRequest is required, so there's no omitted-field risk.
    @PutMapping("/{roomId}/availability")
    public List<AvailabilityResponse> setAvailability(
            Authentication authentication, @PathVariable Long hotelId, @PathVariable Long roomId,
            @Valid @RequestBody AvailabilityRangeRequest request
    ) {
        return roomService.setAvailability(extractAccountId(authentication), hotelId, roomId, request);
    }

    // ------------------ Endpoint 7: Reserve a room for a stay (TRAVELER, called by Booking Service) -----------------
    @PostMapping("/{roomId}/reserve")
    public ReserveRoomResponse reserve(
            @PathVariable Long hotelId, @PathVariable Long roomId, @Valid @RequestBody ReserveRoomRequest request
    ) {
        return roomService.reserve(hotelId, roomId, request.checkInDate(), request.checkOutDate());
    }

    // ------------------ Endpoint 8: Release a room for a stay (TRAVELER, called by Booking Service on cancel) -----------------
    @PostMapping("/{roomId}/release")
    public List<AvailabilityResponse> release(
            @PathVariable Long hotelId, @PathVariable Long roomId, @Valid @RequestBody ReserveRoomRequest request
    ) {
        return roomService.release(hotelId, roomId, request.checkInDate(), request.checkOutDate());
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

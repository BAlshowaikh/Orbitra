/*
  RoomController.java
  Thin HTTP layer for a hotel's rooms and their availability - all
  owner-only. Business logic lives entirely in RoomService.
*/
package com.orbitra.hotel_service.controller;

// ----------- IMPORTS -----------
import com.orbitra.hotel_service.dto.AvailabilityRangeRequest;
import com.orbitra.hotel_service.dto.AvailabilityResponse;
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

    // ------------------ Endpoint 1: Add a room -----------------
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(Authentication authentication, @PathVariable Long hotelId, @Valid @RequestBody RoomRequest request) {
        return roomService.create(extractAccountId(authentication), hotelId, request);
    }

    // ------------------ Endpoint 2: Update a room (partial update) -----------------
    @PatchMapping("/{roomId}")
    public RoomResponse update(Authentication authentication, @PathVariable Long hotelId, @PathVariable Long roomId, @RequestBody JsonNode body) {
        return roomService.update(extractAccountId(authentication), hotelId, roomId, body);
    }

    // ------------------ Endpoint 3: Activate/deactivate a room -----------------
    @PatchMapping("/{roomId}/status")
    public RoomResponse updateStatus(
            Authentication authentication, @PathVariable Long hotelId, @PathVariable Long roomId,
            @Valid @RequestBody UpdateActiveRequest request
    ) {
        return roomService.updateStatus(extractAccountId(authentication), hotelId, roomId, request.active());
    }

    // ------------------ Endpoint 4: Get availability calendar -----------------
    @GetMapping("/{roomId}/availability")
    public List<AvailabilityResponse> getAvailability(
            Authentication authentication, @PathVariable Long hotelId, @PathVariable Long roomId,
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate
    ) {
        return roomService.getAvailability(extractAccountId(authentication), hotelId, roomId, startDate, endDate);
    }

    // ------------------ Endpoint 5: Set availability for a date range -----------------
    // Full-replace semantics (not merge-patch) - every field on
    // AvailabilityRangeRequest is required, so there's no omitted-field risk.
    @PutMapping("/{roomId}/availability")
    public List<AvailabilityResponse> setAvailability(
            Authentication authentication, @PathVariable Long hotelId, @PathVariable Long roomId,
            @Valid @RequestBody AvailabilityRangeRequest request
    ) {
        return roomService.setAvailability(extractAccountId(authentication), hotelId, roomId, request);
    }

    private Long extractAccountId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}

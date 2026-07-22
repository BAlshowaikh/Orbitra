/*
  RoomTypeController.java
  Thin HTTP layer for the admin-managed RoomType catalog: public list (with
  extra inactive entries for ADMIN callers), admin create/update/status.
  Business logic lives entirely in RoomTypeService.
*/
package com.orbitra.hotel_service.controller;

// ----------- IMPORTS -----------
import com.orbitra.hotel_service.dto.RoomTypeRequest;
import com.orbitra.hotel_service.dto.RoomTypeResponse;
import com.orbitra.hotel_service.dto.UpdateActiveRequest;
import com.orbitra.hotel_service.service.RoomTypeService;
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
@RequestMapping("/room-types")
public class RoomTypeController {

    private final RoomTypeService roomTypeService;

    public RoomTypeController(RoomTypeService roomTypeService) {
        this.roomTypeService = roomTypeService;
    }

    // ------------------ Endpoint 1: List (public, extra entries for ADMIN) -----------------
    @GetMapping
    public List<RoomTypeResponse> list(Authentication authentication) {
        return roomTypeService.list(isAdmin(authentication));
    }

    // ------------------ Endpoint 2: Create (ADMIN) -----------------
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomTypeResponse create(@Valid @RequestBody RoomTypeRequest request) {
        return roomTypeService.create(request);
    }

    // ------------------ Endpoint 3: Update (ADMIN, partial update) -----------------
    @PatchMapping("/{id}")
    public RoomTypeResponse update(@PathVariable Long id, @RequestBody JsonNode body) {
        return roomTypeService.update(id, body);
    }

    // ------------------ Endpoint 4: Activate/deactivate (ADMIN) -----------------
    @PatchMapping("/{id}/status")
    public RoomTypeResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateActiveRequest request) {
        return roomTypeService.updateStatus(id, request.active());
    }

    // Anonymous callers still reach this (GET is permitAll) with a non-null,
    // non-admin Authentication - Spring Security's default anonymous token.
    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}

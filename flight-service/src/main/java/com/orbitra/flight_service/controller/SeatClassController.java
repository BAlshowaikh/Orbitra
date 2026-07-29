/*
  SeatClassController.java
  Thin HTTP layer for the admin-managed SeatClass catalog: public list (with
  extra inactive entries for ADMIN callers), admin create/update/status.
  Business logic lives entirely in SeatClassService.
*/
package com.orbitra.flight_service.controller;

// ----------- IMPORTS -----------
import com.orbitra.flight_service.dto.SeatClassRequest;
import com.orbitra.flight_service.dto.SeatClassResponse;
import com.orbitra.flight_service.dto.UpdateActiveRequest;
import com.orbitra.flight_service.service.SeatClassService;
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
@RequestMapping("/seat-classes")
public class SeatClassController {

    private final SeatClassService seatClassService;

    public SeatClassController(SeatClassService seatClassService) {
        this.seatClassService = seatClassService;
    }

    // ------------------ Endpoint 1: List (public, extra entries for ADMIN) -----------------
    @GetMapping
    public List<SeatClassResponse> list(Authentication authentication) {
        return seatClassService.list(isAdmin(authentication));
    }

    // ------------------ Endpoint 2: Create (ADMIN) -----------------
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SeatClassResponse create(@Valid @RequestBody SeatClassRequest request) {
        return seatClassService.create(request);
    }

    // ------------------ Endpoint 3: Update (ADMIN, partial update) -----------------
    @PatchMapping("/{id}")
    public SeatClassResponse update(@PathVariable Long id, @RequestBody JsonNode body) {
        return seatClassService.update(id, body);
    }

    // ------------------ Endpoint 4: Activate/deactivate (ADMIN) -----------------
    @PatchMapping("/{id}/status")
    public SeatClassResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateActiveRequest request) {
        return seatClassService.updateStatus(id, request.active());
    }

    // Anonymous callers still reach this (GET is permitAll) with a non-null,
    // non-admin Authentication - Spring Security's default anonymous token.
    private boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}

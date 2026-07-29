/*
  FlightController.java
  Thin HTTP layer for flight listings: public search/detail, partner
  create/update/status, and the partner's own listing view. Business logic
  lives entirely in FlightService.
*/
package com.orbitra.flight_service.controller;

// ----------- IMPORTS -----------
import com.orbitra.flight_service.dto.FlightDetailResponse;
import com.orbitra.flight_service.dto.FlightRequest;
import com.orbitra.flight_service.dto.FlightResponse;
import com.orbitra.flight_service.dto.FlightSearchResultResponse;
import com.orbitra.flight_service.dto.PagedResponse;
import com.orbitra.flight_service.dto.UpdateActiveRequest;
import com.orbitra.flight_service.service.FlightService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/flights")
public class FlightController {

    private final FlightService flightService;

    public FlightController(FlightService flightService) {
        this.flightService = flightService;
    }

    // ------------------ Endpoint 1: Search (public) -----------------
    @GetMapping
    public PagedResponse<FlightSearchResultResponse> search(
            @RequestParam(required = false) String originCode,
            @RequestParam(required = false) String destinationCode,
            @RequestParam(required = false) LocalDate travelDate,
            @RequestParam(required = false) Integer passengers,
            @RequestParam(required = false) String seatClassName,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return flightService.search(originCode, destinationCode, travelDate, passengers, seatClassName, minPrice, maxPrice, pageable);
    }

    // ------------------ Endpoint 2: My own listings (PARTNER_FLIGHT) -----------------
    @GetMapping("/mine")
    public PagedResponse<FlightResponse> getMyFlights(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        return flightService.getMyFlights(extractAccountId(authentication), pageable);
    }

    // ------------------ Endpoint 3: Detail view (public) -----------------
    @GetMapping("/{id}")
    public FlightDetailResponse getDetail(@PathVariable Long id) {
        return flightService.getDetail(id);
    }

    // ------------------ Endpoint 4: Create (PARTNER_FLIGHT) -----------------
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlightResponse create(Authentication authentication, @Valid @RequestBody FlightRequest request) {
        return flightService.create(extractAccountId(authentication), request);
    }

    // ------------------ Endpoint 5: Update (owner only, partial update) -----------------
    @PatchMapping("/{id}")
    public FlightResponse update(Authentication authentication, @PathVariable Long id, @RequestBody JsonNode body) {
        return flightService.update(extractAccountId(authentication), id, body);
    }

    // ------------------ Endpoint 6: Activate/deactivate (owner or admin) -----------------
    @PatchMapping("/{id}/status")
    public FlightResponse updateStatus(Authentication authentication, @PathVariable Long id, @Valid @RequestBody UpdateActiveRequest request) {
        return flightService.updateStatus(extractAccountId(authentication), isAdmin(authentication), id, request.active());
    }

    private Long extractAccountId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}

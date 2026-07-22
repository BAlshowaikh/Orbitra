/*
  HotelController.java
  Thin HTTP layer for hotel listings: public search/detail, partner
  create/update/status, and the partner's own listing view. Business logic
  lives entirely in HotelService.
*/
package com.orbitra.hotel_service.controller;

// ----------- IMPORTS -----------
import com.orbitra.hotel_service.dto.HotelDetailResponse;
import com.orbitra.hotel_service.dto.HotelRequest;
import com.orbitra.hotel_service.dto.HotelResponse;
import com.orbitra.hotel_service.dto.HotelSearchResultResponse;
import com.orbitra.hotel_service.dto.PagedResponse;
import com.orbitra.hotel_service.dto.UpdateActiveRequest;
import com.orbitra.hotel_service.service.HotelService;
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
@RequestMapping("/hotels")
public class HotelController {

    private final HotelService hotelService;

    public HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    // ------------------ Endpoint 1: Search (public) -----------------
    @GetMapping
    public PagedResponse<HotelSearchResultResponse> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) LocalDate checkIn,
            @RequestParam(required = false) LocalDate checkOut,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return hotelService.search(city, checkIn, checkOut, guests, minPrice, maxPrice, pageable);
    }

    // ------------------ Endpoint 2: My own listings (PARTNER_HOTEL) -----------------
    @GetMapping("/mine")
    public PagedResponse<HotelResponse> getMyHotels(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        return hotelService.getMyHotels(extractAccountId(authentication), pageable);
    }

    // ------------------ Endpoint 3: Detail view (public) -----------------
    @GetMapping("/{id}")
    public HotelDetailResponse getDetail(@PathVariable Long id) {
        return hotelService.getDetail(id);
    }

    // ------------------ Endpoint 4: Create (PARTNER_HOTEL) -----------------
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HotelResponse create(Authentication authentication, @Valid @RequestBody HotelRequest request) {
        return hotelService.create(extractAccountId(authentication), request);
    }

    // ------------------ Endpoint 5: Update (owner only, partial update) -----------------
    @PatchMapping("/{id}")
    public HotelResponse update(Authentication authentication, @PathVariable Long id, @RequestBody JsonNode body) {
        return hotelService.update(extractAccountId(authentication), id, body);
    }

    // ------------------ Endpoint 6: Activate/deactivate (owner or admin) -----------------
    @PatchMapping("/{id}/status")
    public HotelResponse updateStatus(Authentication authentication, @PathVariable Long id, @Valid @RequestBody UpdateActiveRequest request) {
        return hotelService.updateStatus(extractAccountId(authentication), isAdmin(authentication), id, request.active());
    }

    private Long extractAccountId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}

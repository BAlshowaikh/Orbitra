/*
  HotelRequest.java
  Request body for POST/PUT /hotels (PARTNER_HOTEL only) - creates or updates
  a hotel listing. ownerId/active/createdAt are never part of this - they're
  set by HotelService, not the caller.
*/
package com.orbitra.hotel_service.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record HotelRequest(
        @NotBlank String name,
        String description,
        @NotBlank String address,
        @NotBlank String city,
        @NotBlank String country,
        List<String> amenities
) {
}

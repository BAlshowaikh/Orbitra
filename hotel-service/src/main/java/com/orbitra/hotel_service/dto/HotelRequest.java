/*
  HotelRequest.java
  Request body for POST /hotels (PARTNER_HOTEL only) - creates a hotel
  listing. ownerId/active/createdAt are set by HotelService, not the caller.
  PUT /hotels/{id} reads a raw JsonNode instead (see HotelService.update) so
  an omitted field leaves its current value - this record just documents
  update's field shape too.
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

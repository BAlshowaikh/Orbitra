/*
  HotelSearchResultResponse.java
  Lightweight response body for one hotel in GET /hotels/search results -
  intentionally thinner than HotelResponse since a search result list doesn't
  need every field (description, amenities, etc.), just enough to render a
  result card and link into GET /hotels/{id} for the full detail view.
*/
package com.orbitra.hotel_service.dto;

import java.math.BigDecimal;

public record HotelSearchResultResponse(
        Long id,
        String name,
        String city,
        String country,
        // Cheapest active Room's basePricePerNight at this hotel - null if the
        // hotel has no active rooms yet.
        BigDecimal minNightlyPrice
) {
}

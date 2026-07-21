/*
  HotelResponse.java
  Response body for a single Hotel - returned by create/update and by
  GET /hotels/mine (the partner's own view, including inactive listings).
*/
package com.orbitra.hotel_service.dto;

import java.time.Instant;
import java.util.List;

public record HotelResponse(
        Long id,
        Long ownerId,
        String name,
        String description,
        String address,
        String city,
        String country,
        List<String> amenities,
        boolean active,
        Instant createdAt
) {
}

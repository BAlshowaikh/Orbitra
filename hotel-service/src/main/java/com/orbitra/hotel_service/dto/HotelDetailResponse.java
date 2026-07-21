/*
  HotelDetailResponse.java
  Response body for the public GET /hotels/{id} - full hotel details plus its
  active rooms (with resolved RoomType names and base prices). Distinct from
  HotelResponse (partner's own create/update/list view) since the public
  detail view never needs to show inactive rooms.
*/
package com.orbitra.hotel_service.dto;

import java.util.List;

public record HotelDetailResponse(
        Long id,
        String name,
        String description,
        String address,
        String city,
        String country,
        List<String> amenities,
        List<RoomResponse> rooms
) {
}

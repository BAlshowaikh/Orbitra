/*
  RoomTypeResponse.java
  Response body for a RoomType catalog entry - returned by the public
  GET /room-types list (the partner's "choose a room type" dropdown source)
  and by admin CRUD responses.
*/
package com.orbitra.hotel_service.dto;

public record RoomTypeResponse(
        Long id,
        String name,
        String description,
        boolean active
) {
}

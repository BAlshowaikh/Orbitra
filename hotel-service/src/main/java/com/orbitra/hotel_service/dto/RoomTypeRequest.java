/*
  RoomTypeRequest.java
  Request body for POST/PUT /room-types (ADMIN only) - creates or updates a
  catalog entry hotel partners later pick from when adding a Room.
*/
package com.orbitra.hotel_service.dto;

import jakarta.validation.constraints.NotBlank;

public record RoomTypeRequest(
        @NotBlank String name,
        String description
) {
}

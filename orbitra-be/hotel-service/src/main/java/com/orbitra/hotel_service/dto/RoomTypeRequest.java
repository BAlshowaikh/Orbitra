/*
  RoomTypeRequest.java
  Request body for POST /room-types (ADMIN only) - creates a catalog entry
  hotel partners later pick from when adding a Room. PUT /room-types/{id}
  reads a raw JsonNode instead (see RoomTypeService.update), so this record
  just documents update's field shape too.
*/
package com.orbitra.hotel_service.dto;

import jakarta.validation.constraints.NotBlank;

public record RoomTypeRequest(
        @NotBlank String name,
        String description
) {
}

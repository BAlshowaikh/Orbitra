/*
  SeatClassRequest.java
  Request body for POST /seat-classes (ADMIN only) - creates a catalog entry
  flight partners later pick from when adding a FlightSeat. PUT
  /seat-classes/{id} reads a raw JsonNode instead (see SeatClassService.update),
  so this record just documents update's field shape too.
*/
package com.orbitra.flight_service.dto;

import jakarta.validation.constraints.NotBlank;

public record SeatClassRequest(
        @NotBlank String name,
        String description
) {
}

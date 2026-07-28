/*
  SeatClassResponse.java
  Response body for a SeatClass catalog entry - returned by the public
  GET /seat-classes list (the partner's "choose a seat class" dropdown
  source) and by admin CRUD responses.
*/
package com.orbitra.flight_service.dto;

public record SeatClassResponse(
        Long id,
        String name,
        String description,
        boolean active
) {
}

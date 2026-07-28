/*
  FlightResponse.java
  Response body for a single Flight - returned by create/update and by
  GET /flights/mine (the partner's own view, including inactive listings).
*/
package com.orbitra.flight_service.dto;

import java.time.Instant;
import java.time.LocalDateTime;

public record FlightResponse(
        Long id,
        Long ownerId,
        String flightNumber,
        String originCode,
        String destinationCode,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        Integer durationMinutes,
        Integer seatCount,
        boolean active,
        Instant createdAt
) {
}

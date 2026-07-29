/*
  FlightRequest.java
  Request body for POST /flights (PARTNER_FLIGHT only) - creates a single
  dated flight departure. ownerId/active/createdAt are set by FlightService,
  not the caller. PUT /flights/{id} reads a raw JsonNode instead (see
  FlightService.update), so this record just documents update's field shape
  too.
*/
package com.orbitra.flight_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record FlightRequest(
        @NotBlank String flightNumber,
        @NotBlank String originCode,
        @NotBlank String destinationCode,
        @NotNull LocalDateTime departureTime,
        @NotNull LocalDateTime arrivalTime,
        @NotNull @Positive Integer durationMinutes,
        @NotNull @Positive Integer seatCount
) {
}

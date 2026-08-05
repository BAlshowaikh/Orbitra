/*
  FlightSeatRequest.java
  Request body for POST /flights/{flightId}/seats (PARTNER_FLIGHT, owner
  only) - adds a flight's own instance of a SeatClass, picked by id (the
  catalog dropdown), never free-text. No capacity field, unlike RoomRequest -
  a seat always holds exactly one passenger. No availableCount field either -
  FlightSeatService sets it equal to totalInventory at creation; it's
  booking-driven only from then on, never partner-supplied. PATCH
  .../seats/{seatId} reads a raw JsonNode instead (see FlightSeatService.update),
  so this record just documents that endpoint's field shape too.
*/
package com.orbitra.flight_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record FlightSeatRequest(
        @NotNull Long seatClassId,
        @NotNull @Positive BigDecimal basePricePerSeat,
        @NotNull @PositiveOrZero Integer totalInventory,
        List<String> facilities
) {
}

/*
  ReserveRoomRequest.java
  Request body for POST .../rooms/{roomId}/reserve and .../release
  (TRAVELER, called by Booking Service) - checkOut is exclusive, same
  semantics as GET /hotels' search filters (a stay covers every night in
  [checkInDate, checkOutDate)).
*/
package com.orbitra.hotel_service.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ReserveRoomRequest(
        @NotNull LocalDate checkInDate,
        @NotNull LocalDate checkOutDate
) {
}

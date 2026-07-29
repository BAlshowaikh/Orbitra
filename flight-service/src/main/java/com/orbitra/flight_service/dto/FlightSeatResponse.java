/*
  FlightSeatResponse.java
  Response body for a single FlightSeat - a flight's own priced instance of a
  SeatClass. Includes the resolved SeatClass name for display convenience, so
  callers don't need a second lookup just to show "Business" instead of a
  bare id. Includes availableCount (booking-driven remaining seats) alongside
  totalInventory (fixed partner-set capacity) - unlike RoomResponse, which
  has no availableCount field since that lives in a separate Availability
  entity for hotel-service.
*/
package com.orbitra.flight_service.dto;

import java.math.BigDecimal;
import java.util.List;

public record FlightSeatResponse(
        Long id,
        Long flightId,
        Long seatClassId,
        String seatClassName,
        BigDecimal basePricePerSeat,
        Integer totalInventory,
        Integer availableCount,
        List<String> facilities,
        boolean active
) {
}

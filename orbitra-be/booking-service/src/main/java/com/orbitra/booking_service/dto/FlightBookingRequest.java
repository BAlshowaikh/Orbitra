/*
  FlightBookingRequest.java
  Request body for POST /bookings/flight-seats (TRAVELER) - reserves one
  seat. flightId/flightSeatId identify which Flight Service endpoint to call
  (POST /flights/{flightId}/seats/{seatId}/reserve).
*/
package com.orbitra.booking_service.dto;

import jakarta.validation.constraints.NotNull;

public record FlightBookingRequest(
        @NotNull Long flightId,
        @NotNull Long flightSeatId
) {
}

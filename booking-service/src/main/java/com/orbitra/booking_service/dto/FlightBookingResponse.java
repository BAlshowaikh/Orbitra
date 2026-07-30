/*
  FlightBookingResponse.java
  Response body for a single FlightBooking - returned by create/cancel and
  appears (mixed with HotelBookingResponse) in GET /bookings/mine's unified
  list, via the BookingResponse sealed interface.
*/
package com.orbitra.booking_service.dto;

import com.orbitra.booking_service.model.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record FlightBookingResponse(
        Long id,
        Long travelerId,
        BookingStatus status,
        BigDecimal totalPrice,
        Long flightId,
        Long flightSeatId,
        Instant createdAt
) implements BookingResponse {
}

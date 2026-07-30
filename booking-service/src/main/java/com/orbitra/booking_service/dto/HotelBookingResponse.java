/*
  HotelBookingResponse.java
  Response body for a single HotelBooking - returned by create/cancel and
  appears (mixed with FlightBookingResponse) in GET /bookings/mine's unified
  list, via the BookingResponse sealed interface.
*/
package com.orbitra.booking_service.dto;

import com.orbitra.booking_service.model.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record HotelBookingResponse(
        Long id,
        Long travelerId,
        BookingStatus status,
        BigDecimal totalPrice,
        Long hotelId,
        Long roomId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Instant createdAt
) implements BookingResponse {
}

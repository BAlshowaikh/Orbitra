/*
  HotelBookingRequest.java
  Request body for POST /bookings/hotel-rooms (TRAVELER) - reserves a room
  for a stay. hotelId/roomId identify which Hotel Service endpoint to call
  (POST /hotels/{hotelId}/rooms/{roomId}/reserve); checkOutDate is exclusive,
  same semantics as Hotel Service's own ReserveRoomRequest.
*/
package com.orbitra.booking_service.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record HotelBookingRequest(
        @NotNull Long hotelId,
        @NotNull Long roomId,
        @NotNull LocalDate checkInDate,
        @NotNull LocalDate checkOutDate
) {
}

/*
  RoomRequest.java
  Request body for POST/PUT /hotels/{hotelId}/rooms (PARTNER_HOTEL, owner
  only) - adds or updates a hotel's own instance of a RoomType. Picks the
  RoomType by id (the catalog dropdown), never by free-text name.
*/
package com.orbitra.hotel_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record RoomRequest(
        @NotNull Long roomTypeId,
        @NotNull @Positive Integer capacity,
        @NotNull @Positive BigDecimal basePricePerNight,
        @NotNull @PositiveOrZero Integer totalInventory,
        List<String> facilities
) {
}

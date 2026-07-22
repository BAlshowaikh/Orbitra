/*
  RoomRequest.java
  Request body for POST /hotels/{hotelId}/rooms (PARTNER_HOTEL, owner only) -
  adds a hotel's own instance of a RoomType, picked by id (the catalog
  dropdown), never free-text. PATCH .../rooms/{roomId} reads a raw JsonNode
  instead (see RoomService.update), so this record just documents that
  endpoint's field shape too.
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

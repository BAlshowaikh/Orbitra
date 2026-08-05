/*
  RoomResponse.java
  Response body for a single Room - a hotel's own priced/staffed instance of
  a RoomType. Includes the resolved RoomType name for display convenience, so
  callers don't need a second lookup just to show "Deluxe King" instead of a
  bare id.
*/
package com.orbitra.hotel_service.dto;

import java.math.BigDecimal;
import java.util.List;

public record RoomResponse(
        Long id,
        Long hotelId,
        Long roomTypeId,
        String roomTypeName,
        Integer capacity,
        BigDecimal basePricePerNight,
        Integer totalInventory,
        List<String> facilities,
        boolean active
) {
}

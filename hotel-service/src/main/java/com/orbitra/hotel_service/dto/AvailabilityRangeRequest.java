/*
  AvailabilityRangeRequest.java
  Request body for PUT /hotels/{hotelId}/rooms/{roomId}/availability
  (PARTNER_HOTEL, owner only) - upserts one Availability row per date in
  [startDate, endDate] with the given count. Only ever used to override
  specific dates - see RoomService for the row-absence-means-default-
  available fallback this is paired with.
*/
package com.orbitra.hotel_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record AvailabilityRangeRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @PositiveOrZero Integer availableCount
) {
}

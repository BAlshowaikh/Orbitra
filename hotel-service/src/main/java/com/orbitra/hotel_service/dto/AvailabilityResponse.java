/*
  AvailabilityResponse.java
  Response body for one date's effective availability - the count returned
  here already has the row-absence-means-default-available fallback applied
  (RoomService), so callers never need to know whether a given date had an
  explicit override row or not.
*/
package com.orbitra.hotel_service.dto;

import java.time.LocalDate;

public record AvailabilityResponse(
        LocalDate date,
        Integer availableCount
) {
}

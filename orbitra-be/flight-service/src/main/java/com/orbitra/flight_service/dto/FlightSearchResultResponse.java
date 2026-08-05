/*
  FlightSearchResultResponse.java
  Lightweight response body for one flight in GET /flights search results -
  intentionally thinner than FlightResponse since a search result list
  doesn't need every field, just enough to render a result card and link into
  GET /flights/{id} for the full detail view.
*/
package com.orbitra.flight_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FlightSearchResultResponse(
        Long id,
        String flightNumber,
        String originCode,
        String destinationCode,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        // Cheapest active FlightSeat's basePricePerSeat on this flight - null
        // if the flight has no active seats yet.
        BigDecimal minPrice
) {
}

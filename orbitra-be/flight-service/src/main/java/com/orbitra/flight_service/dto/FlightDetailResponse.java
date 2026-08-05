/*
  FlightDetailResponse.java
  Response body for the public GET /flights/{id} - full flight details plus
  its active seats (with resolved SeatClass names and prices). Distinct from
  FlightResponse (partner's own create/update/list view) since the public
  detail view never needs to show inactive seats. No amenities field, unlike
  HotelDetailResponse - Flight Service has no flight-level amenities table.
*/
package com.orbitra.flight_service.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FlightDetailResponse(
        Long id,
        String flightNumber,
        String originCode,
        String destinationCode,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        Integer durationMinutes,
        List<FlightSeatResponse> seats
) {
}

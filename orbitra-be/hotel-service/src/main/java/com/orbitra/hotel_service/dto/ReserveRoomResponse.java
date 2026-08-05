/*
  ReserveRoomResponse.java
  Response body for POST .../rooms/{roomId}/reserve - the per-night
  AvailabilityResponse list plus basePricePerNight, so the caller (Booking
  Service) can compute a total price without a second call back to this
  service. release() doesn't need this - only reserve needs to communicate
  price back for billing purposes.
*/
package com.orbitra.hotel_service.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReserveRoomResponse(
        BigDecimal basePricePerNight,
        List<AvailabilityResponse> nights
) {
}

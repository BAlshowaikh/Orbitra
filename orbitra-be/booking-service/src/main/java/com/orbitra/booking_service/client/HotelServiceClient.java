/*
  HotelServiceClient.java
  Thin wrapper around HotelServiceFeignClient - same public method
  signatures as before the Feign rewrite, so BookingService needed zero
  changes. All this class does now is delegate the actual call and
  translate Feign's exceptions into this service's own (see
  docs/inter-service-http-calls.md for the general concept; CLAUDE.md/
  docs/architecture&logic.md for why this stayed a wrapper instead of a
  global Feign ErrorDecoder).
*/
package com.orbitra.booking_service.client;

// ----------- IMPORTS -----------
import com.orbitra.booking_service.exception.InsufficientAvailabilityException;
import com.orbitra.booking_service.exception.InventoryServiceUnavailableException;
import feign.FeignException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class HotelServiceClient {

    private final HotelServiceFeignClient feignClient;

    public HotelServiceClient(HotelServiceFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    // ------------ METHOD 1: Reserve a room for a stay, returns its per-night price ------------
    public BigDecimal reserve(Long hotelId, Long roomId, LocalDate checkInDate, LocalDate checkOutDate, String authorizationHeader) {
        try {
            HotelServiceFeignClient.ReserveResponse response = feignClient.reserve(
                    hotelId, roomId, authorizationHeader,
                    new HotelServiceFeignClient.ReserveRequest(checkInDate, checkOutDate)
            );

            if (response == null) {
                throw new InventoryServiceUnavailableException("Hotel Service returned an empty reserve response for room " + roomId);
            }
            return response.basePricePerNight();
        } catch (FeignException.Conflict e) {
            // Hotel Service's own 409 - a real "sold out" outcome, not a failure.
            throw new InsufficientAvailabilityException("Room " + roomId + " is not available for the requested dates");
        } catch (FeignException e) {
            // Network failure, timeout, or any other non-409 error status.
            throw new InventoryServiceUnavailableException("Hotel Service is unreachable or returned an unexpected error", e);
        }
    }

    // ------------ METHOD 2: Release a previously reserved stay ------------
    public void release(Long hotelId, Long roomId, LocalDate checkInDate, LocalDate checkOutDate, String authorizationHeader) {
        try {
            feignClient.release(hotelId, roomId, authorizationHeader, new HotelServiceFeignClient.ReserveRequest(checkInDate, checkOutDate));
        } catch (FeignException e) {
            throw new InventoryServiceUnavailableException("Hotel Service is unreachable or returned an unexpected error releasing room " + roomId, e);
        }
    }
}

/*
  FlightServiceClient.java
  Thin wrapper around FlightServiceFeignClient - same public method
  signatures as before the Feign rewrite, so BookingService needed zero
  changes. Same pattern as HotelServiceClient.
*/
package com.orbitra.booking_service.client;

// ----------- IMPORTS -----------
import com.orbitra.booking_service.exception.InsufficientAvailabilityException;
import com.orbitra.booking_service.exception.InventoryServiceUnavailableException;
import feign.FeignException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FlightServiceClient {

    private final FlightServiceFeignClient feignClient;

    public FlightServiceClient(FlightServiceFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    // ------------ METHOD 1: Reserve one seat, returns its price ------------
    public BigDecimal reserve(Long flightId, Long flightSeatId, String authorizationHeader) {
        try {
            // Call the Feign client to reserve a seat on the flight. The Feign client will handle the HTTP request and response.
            FlightServiceFeignClient.SeatResponse response = feignClient.reserve(flightId, flightSeatId, authorizationHeader);

            if (response == null) {
                throw new InventoryServiceUnavailableException("Flight Service returned an empty reserve response for seat " + flightSeatId);
            }
            return response.basePricePerSeat();
        } catch (FeignException.Conflict e) {
            // Flight Service's own 409 - a real "sold out" outcome, not a failure.
            throw new InsufficientAvailabilityException("Flight seat " + flightSeatId + " is not available");
        } catch (FeignException e) {
            // Network failure, timeout, or any other non-409 error status.
            throw new InventoryServiceUnavailableException("Flight Service is unreachable or returned an unexpected error", e);
        }
    }

    // ------------ METHOD 2: Release a previously reserved seat ------------
    public void release(Long flightId, Long flightSeatId, String authorizationHeader) {
        try {
            feignClient.release(flightId, flightSeatId, authorizationHeader);
        } catch (FeignException e) {
            throw new InventoryServiceUnavailableException("Flight Service is unreachable or returned an unexpected error releasing seat " + flightSeatId, e);
        }
    }
}

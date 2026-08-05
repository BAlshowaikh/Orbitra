/*
  FlightServiceFeignClient.java
  Declarative HTTP client for Flight Service's reserve/release endpoints -
  same pattern as HotelServiceFeignClient, but simpler (no request body,
  a seat is identified entirely by the path). "flight-service" is resolved
  by Eureka at call time. Wrapped by FlightServiceClient.
*/
package com.orbitra.booking_service.client;

// ----------- IMPORTS -----------
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;

@FeignClient(name = "flight-service") // This connects to eureka and asks it to resolve the service name to a actual URL (hostname+port) at call time.
public interface FlightServiceFeignClient {

    @PostMapping("/flights/{flightId}/seats/{seatId}/reserve")
    SeatResponse reserve(
            @PathVariable("flightId") Long flightId, @PathVariable("seatId") Long flightSeatId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader
    );

    @PostMapping("/flights/{flightId}/seats/{seatId}/release")
    void release(
            @PathVariable("flightId") Long flightId, @PathVariable("seatId") Long flightSeatId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader
    );

    record SeatResponse(BigDecimal basePricePerSeat) {
    }
}

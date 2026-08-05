/*
  HotelServiceFeignClient.java
  Declarative HTTP client for Hotel Service's reserve/release endpoints -
  just method declarations, Spring Cloud OpenFeign generates the real
  implementation. "hotel-service" is resolved to an actual host:port by
  Eureka at call time, not a hardcoded URL. Wrapped by HotelServiceClient,
  which is what the rest of this service actually calls - error translation
  and the public API stay there, unchanged from before this rewrite.
*/
package com.orbitra.booking_service.client;

// ----------- IMPORTS -----------
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;
import java.time.LocalDate;

@FeignClient(name = "hotel-service")
public interface HotelServiceFeignClient {

    @PostMapping("/hotels/{hotelId}/rooms/{roomId}/reserve")
    ReserveResponse reserve(
            @PathVariable("hotelId") Long hotelId, @PathVariable("roomId") Long roomId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader, @RequestBody ReserveRequest request
    );

    @PostMapping("/hotels/{hotelId}/rooms/{roomId}/release")
    void release(
            @PathVariable("hotelId") Long hotelId, @PathVariable("roomId") Long roomId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader, @RequestBody ReserveRequest request
    );

    // Same shapes HotelServiceClient's private nested records used before -
    // nested here instead since both Feign methods above need them too.
    record ReserveRequest(LocalDate checkInDate, LocalDate checkOutDate) {
    }

    record ReserveResponse(BigDecimal basePricePerNight) {
    }
}

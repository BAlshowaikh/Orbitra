/*
  HotelServiceClient.java
  Wraps synchronous HTTP calls to Hotel Service's reserve/release endpoints -
  the first inter-service HTTP client in this project (see
  docs/inter-service-http-calls.md for the general concept). Forwards the
  caller's own JWT (not a special service-to-service credential) as the
  outgoing Authorization header, exactly as it arrived - see hotel-service's
  SecurityConfig, which grants these routes to any authenticated TRAVELER.
*/
package com.orbitra.booking_service.client;

// ----------- IMPORTS -----------
import com.orbitra.booking_service.exception.InsufficientAvailabilityException;
import com.orbitra.booking_service.exception.InventoryServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class HotelServiceClient {

    // Reusable HTTP client pointed at hotel-service's base URL - built once here, reused for every call below.
    private final RestClient restClient;

    public HotelServiceClient(@Value("${app.hotel-service.url}") String hotelServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(hotelServiceUrl).build();
    }

    // Request/response shapes scoped to this class only - not shared public
    // DTOs, since nothing else in this service needs to reference them.
    // Mirrors hotel-service's own ReserveRoomRequest/ReserveRoomResponse,
    // trimmed to only the fields this client actually uses.
    private record ReserveRequest(LocalDate checkInDate, LocalDate checkOutDate) {
    }

    private record ReserveResponse(BigDecimal basePricePerNight) {
    }

    // ------------ METHOD 1: Reserve a room for a stay, returns its per-night price ------------
    public BigDecimal reserve(Long hotelId, Long roomId, LocalDate checkInDate, LocalDate checkOutDate, String authorizationHeader) {
        try {
            ReserveResponse response = restClient.post()
                    .uri("/hotels/{hotelId}/rooms/{roomId}/reserve", hotelId, roomId) // fills in the path
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader) // token relay - forwards the traveler's own JWT
                    .body(new ReserveRequest(checkInDate, checkOutDate)) // outgoing JSON request body
                    .retrieve() // actually sends the request, blocks for the response
                    .body(ReserveResponse.class); // deserializes the JSON response

            if (response == null) {
                throw new InventoryServiceUnavailableException("Hotel Service returned an empty reserve response for room " + roomId);
            }
            return response.basePricePerNight();
        } catch (HttpClientErrorException.Conflict e) {
            // Hotel Service's own 409 - a real "sold out" outcome, not a failure.
            throw new InsufficientAvailabilityException("Room " + roomId + " is not available for the requested dates");
        } catch (RestClientException e) {
            // Network failure, timeout, or any other non-409 error status.
            throw new InventoryServiceUnavailableException("Hotel Service is unreachable or returned an unexpected error", e);
        }
    }

    // ------------ METHOD 2: Release a previously reserved stay ------------
    public void release(Long hotelId, Long roomId, LocalDate checkInDate, LocalDate checkOutDate, String authorizationHeader) {
        try {
            restClient.post()
                    .uri("/hotels/{hotelId}/rooms/{roomId}/release", hotelId, roomId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .body(new ReserveRequest(checkInDate, checkOutDate))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new InventoryServiceUnavailableException("Hotel Service is unreachable or returned an unexpected error releasing room " + roomId, e);
        }
    }
}

/*
  FlightServiceClient.java
  Wraps synchronous HTTP calls to Flight Service's reserve/release endpoints
  (see docs/inter-service-http-calls.md for the general concept). Same
  token-relay pattern as HotelServiceClient, but simpler - no request body,
  since a single seat is identified entirely by the path.
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

@Component
public class FlightServiceClient {

    // Reusable HTTP client pointed at flight-service's base URL - built once here, reused for every call below.
    private final RestClient restClient;

    // Spring injects the configured URL once at bean creation (from application.properties) (this class is a singleton) - built once, reused by every call below.
    public FlightServiceClient(@Value("${app.flight-service.url}") String flightServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(flightServiceUrl).build();
    }

    // Response shape scoped to this class only - trimmed down from
    // flight-service's full FlightSeatResponse to just the one field this
    // client actually uses.
    private record SeatResponse(BigDecimal basePricePerSeat) {
    }

    // ------------ METHOD 1: Reserve one seat, returns its price ------------
    public BigDecimal reserve(Long flightId, Long flightSeatId, String authorizationHeader) {
        try {
            SeatResponse response = restClient.post()
                    .uri("/flights/{flightId}/seats/{seatId}/reserve", flightId, flightSeatId) // fills in the path
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader) // token relay - forwards the traveler's own JWT
                    .retrieve() // actually sends the request, blocks for the response
                    .body(SeatResponse.class); // deserializes the JSON response

            if (response == null) {
                throw new InventoryServiceUnavailableException("Flight Service returned an empty reserve response for seat " + flightSeatId);
            }
            return response.basePricePerSeat();
        } catch (HttpClientErrorException.Conflict e) {
            // Flight Service's own 409 - a real "sold out" outcome, not a failure.
            throw new InsufficientAvailabilityException("Flight seat " + flightSeatId + " is not available");
        } catch (RestClientException e) {
            // Network failure, timeout, or any other non-409 error status.
            throw new InventoryServiceUnavailableException("Flight Service is unreachable or returned an unexpected error", e);
        }
    }

    // ------------ METHOD 2: Release a previously reserved seat ------------
    public void release(Long flightId, Long flightSeatId, String authorizationHeader) {
        try {
            restClient.post()
                    .uri("/flights/{flightId}/seats/{seatId}/release", flightId, flightSeatId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new InventoryServiceUnavailableException("Flight Service is unreachable or returned an unexpected error releasing seat " + flightSeatId, e);
        }
    }
}

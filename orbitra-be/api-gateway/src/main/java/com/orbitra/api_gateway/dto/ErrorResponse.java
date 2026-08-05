/*
  ErrorResponse.java
  Response body returned when the Gateway itself rejects a request (e.g. a
  missing/invalid JWT on a protected route) - same shape every other service
  in this project uses, so a client can't tell whether the rejection came
  from the Gateway or from the backend service it was routed to.
*/
package com.orbitra.api_gateway.dto;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String message
) {
}

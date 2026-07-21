/*
  ErrorResponse.java
  Response body returned for any request that fails validation or is rejected
  by a service in this app - shape is the same regardless of which exception
  caused it.
*/
package com.orbitra.hotel_service.dto;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String message
) {
}

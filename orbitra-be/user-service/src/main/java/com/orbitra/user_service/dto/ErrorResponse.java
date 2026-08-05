/*
  ErrorResponse.java
  Consistent JSON error body for every non-2xx response from this service.
  Own copy, not shared with auth-service - services don't share code.
*/
package com.orbitra.user_service.dto;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String message
) {
}

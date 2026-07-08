/*
  ErrorResponse.java
  Response body returned for any request that fails validation or is rejected
  by AuthService - shape is the same regardless of which exception caused it.
*/
package com.orbitra.auth_service.dto;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String message
) {
}

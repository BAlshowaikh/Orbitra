/*
  InvalidProfileRequestException.java
  Thrown when a profile update request fails manual validation (firstName/
  lastName missing or blank, dateOfBirth unparsable). Needed because the PUT
  endpoint now reads the raw request body as a JsonNode instead of binding to
  a validated DTO - see UserProfileService for why.
*/
package com.orbitra.user_service.exception;

public class InvalidProfileRequestException extends RuntimeException {

    public InvalidProfileRequestException(String message) {
        super(message);
    }
}

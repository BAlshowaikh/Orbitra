/*
  DuplicateFlightNumberException.java
  Thrown when a Flight's flightNumber already exists - maps to 409 Conflict,
  same pattern as DuplicateSeatClassNameException.
*/
package com.orbitra.flight_service.exception;

public class DuplicateFlightNumberException extends RuntimeException {

    public DuplicateFlightNumberException(String message) {
        super(message);
    }
}

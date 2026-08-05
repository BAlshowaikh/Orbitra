/*
  FlightNotFoundException.java
  Thrown when a requested Flight id doesn't exist (or, for the public detail
  view, exists but isn't active) - distinct from a generic error so
  GlobalExceptionHandler can map it to 404 Not Found.
*/
package com.orbitra.flight_service.exception;

public class FlightNotFoundException extends RuntimeException {

    public FlightNotFoundException(String message) {
        super(message);
    }
}

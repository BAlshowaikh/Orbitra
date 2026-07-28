/*
  FlightSeatNotFoundException.java
  Thrown when a requested FlightSeat id doesn't exist, or doesn't belong to
  the given flight - maps to 404 Not Found.
*/
package com.orbitra.flight_service.exception;

public class FlightSeatNotFoundException extends RuntimeException {

    public FlightSeatNotFoundException(String message) {
        super(message);
    }
}

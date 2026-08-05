/*
  DuplicateFlightSeatException.java
  Thrown when a flight already has a FlightSeat for the given SeatClass -
  maps to 409 Conflict, same pattern as DuplicateSeatClassNameException.
*/
package com.orbitra.flight_service.exception;

public class DuplicateFlightSeatException extends RuntimeException {

    public DuplicateFlightSeatException(String message) {
        super(message);
    }
}

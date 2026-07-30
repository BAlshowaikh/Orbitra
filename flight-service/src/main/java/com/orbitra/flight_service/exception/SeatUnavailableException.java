/*
  SeatUnavailableException.java
  Thrown when a reserve call finds availableCount already at 0 (or the seat
  otherwise isn't reservable) - maps to 409 Conflict, distinct from 404 since
  the seat exists, it's just sold out.
*/
package com.orbitra.flight_service.exception;

public class SeatUnavailableException extends RuntimeException {

    public SeatUnavailableException(String message) {
        super(message);
    }
}

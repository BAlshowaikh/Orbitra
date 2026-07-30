/*
  InvalidBookingStateException.java
  Thrown when an action is attempted against a booking whose current status
  doesn't allow it - e.g. cancelling a booking that isn't PENDING. Maps to
  400 Bad Request.
*/
package com.orbitra.booking_service.exception;

public class InvalidBookingStateException extends RuntimeException {

    public InvalidBookingStateException(String message) {
        super(message);
    }
}

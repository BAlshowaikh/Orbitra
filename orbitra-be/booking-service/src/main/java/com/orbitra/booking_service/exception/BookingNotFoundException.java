/*
  BookingNotFoundException.java
  Thrown when a requested booking id doesn't exist, or doesn't belong to the
  caller - maps to 404 Not Found. Deliberately the same message/status for
  both cases, so a caller probing other travelers' booking ids can't use the
  response to tell "doesn't exist" apart from "exists, isn't yours".
*/
package com.orbitra.booking_service.exception;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(String message) {
        super(message);
    }
}

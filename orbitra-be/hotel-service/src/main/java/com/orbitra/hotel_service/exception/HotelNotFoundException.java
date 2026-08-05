/*
  HotelNotFoundException.java
  Thrown when a requested Hotel id doesn't exist (or, for the public detail
  view, exists but isn't active) - distinct from a generic error so
  GlobalExceptionHandler can map it to 404 Not Found.
*/
package com.orbitra.hotel_service.exception;

public class HotelNotFoundException extends RuntimeException {

    public HotelNotFoundException(String message) {
        super(message);
    }
}

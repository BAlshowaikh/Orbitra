/*
  SeatClassNotFoundException.java
  Thrown when a requested SeatClass id doesn't exist - maps to 404 Not Found.
*/
package com.orbitra.flight_service.exception;

public class SeatClassNotFoundException extends RuntimeException {

    public SeatClassNotFoundException(String message) {
        super(message);
    }
}

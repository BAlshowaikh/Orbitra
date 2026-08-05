/*
  InvalidRequestException.java
  Thrown for business-rule request validation that plain @Valid annotations
  can't express on their own - e.g. a search's date param shape, or a
  FlightSeat's totalInventory exceeding the parent Flight's seatCount (see
  FlightSeatService). GlobalExceptionHandler maps it to 400 Bad Request, same
  as a bean-validation failure.
*/
package com.orbitra.flight_service.exception;

public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}

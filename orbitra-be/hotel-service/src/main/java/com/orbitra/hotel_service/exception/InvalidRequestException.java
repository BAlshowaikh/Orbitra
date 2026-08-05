/*
  InvalidRequestException.java
  Thrown for business-rule request validation that plain @Valid annotations
  can't express on their own - e.g. a search's checkIn/checkOut pairing and
  ordering (see HotelService.search). GlobalExceptionHandler maps it to 400
  Bad Request, same as a bean-validation failure.
*/
package com.orbitra.hotel_service.exception;

public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}

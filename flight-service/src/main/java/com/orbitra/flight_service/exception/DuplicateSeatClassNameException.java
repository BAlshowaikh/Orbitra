/*
  DuplicateSeatClassNameException.java
  Thrown when a SeatClass name already exists - maps to 409 Conflict, same
  pattern as hotel-service's DuplicateRoomTypeNameException.
*/
package com.orbitra.flight_service.exception;

public class DuplicateSeatClassNameException extends RuntimeException {

    public DuplicateSeatClassNameException(String message) {
        super(message);
    }
}

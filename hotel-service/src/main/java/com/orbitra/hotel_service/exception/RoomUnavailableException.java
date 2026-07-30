/*
  RoomUnavailableException.java
  Thrown when a reserve call finds a night in the requested stay with no
  availability left - maps to 409 Conflict, distinct from 404 since the room
  exists, it's just sold out for at least one of the requested nights.
*/
package com.orbitra.hotel_service.exception;

public class RoomUnavailableException extends RuntimeException {

    public RoomUnavailableException(String message) {
        super(message);
    }
}

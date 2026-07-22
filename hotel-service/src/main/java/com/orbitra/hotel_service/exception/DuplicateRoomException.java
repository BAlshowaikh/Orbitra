/*
  DuplicateRoomException.java
  Thrown when a hotel already has a Room for the given RoomType - maps to 409
  Conflict, same pattern as DuplicateRoomTypeNameException.
*/
package com.orbitra.hotel_service.exception;

public class DuplicateRoomException extends RuntimeException {

    public DuplicateRoomException(String message) {
        super(message);
    }
}

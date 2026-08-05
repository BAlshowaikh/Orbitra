/*
  DuplicateRoomTypeNameException.java
  Thrown when a RoomType name already exists - maps to 409 Conflict, same
  pattern as auth-service's DuplicateEmailException.
*/
package com.orbitra.hotel_service.exception;

public class DuplicateRoomTypeNameException extends RuntimeException {

    public DuplicateRoomTypeNameException(String message) {
        super(message);
    }
}

/*
  RoomNotFoundException.java
  Thrown when a requested Room id doesn't exist, or doesn't belong to the
  given hotel - maps to 404 Not Found.
*/
package com.orbitra.hotel_service.exception;

public class RoomNotFoundException extends RuntimeException {

    public RoomNotFoundException(String message) {
        super(message);
    }
}

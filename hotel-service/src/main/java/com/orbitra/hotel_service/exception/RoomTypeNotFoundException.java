/*
  RoomTypeNotFoundException.java
  Thrown when a requested RoomType id doesn't exist - maps to 404 Not Found.
*/
package com.orbitra.hotel_service.exception;

public class RoomTypeNotFoundException extends RuntimeException {

    public RoomTypeNotFoundException(String message) {
        super(message);
    }
}

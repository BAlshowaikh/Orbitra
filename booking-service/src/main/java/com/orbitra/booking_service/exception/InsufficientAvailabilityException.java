/*
  InsufficientAvailabilityException.java
  Thrown when Hotel Service or Flight Service's reserve endpoint responds
  409 - no room/seat availability left. Maps to 409 Conflict here too,
  distinct from InventoryServiceUnavailableException (that other service
  being unreachable/erroring, not just declining the reservation).
*/
package com.orbitra.booking_service.exception;

public class InsufficientAvailabilityException extends RuntimeException {

    public InsufficientAvailabilityException(String message) {
        super(message);
    }
}

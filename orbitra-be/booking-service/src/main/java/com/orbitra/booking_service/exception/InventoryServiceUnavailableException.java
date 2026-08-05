/*
  InventoryServiceUnavailableException.java
  Thrown when a call to Hotel Service or Flight Service fails outright -
  network error, timeout, or an unexpected non-409 error status. Distinct
  from InsufficientAvailabilityException (that's a normal "sold out" business
  outcome; this is "the other service didn't answer properly at all").
*/
package com.orbitra.booking_service.exception;

public class InventoryServiceUnavailableException extends RuntimeException {

    public InventoryServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public InventoryServiceUnavailableException(String message) {
        super(message);
    }
}

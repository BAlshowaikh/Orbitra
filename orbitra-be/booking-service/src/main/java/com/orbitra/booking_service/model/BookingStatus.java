/*
  BookingStatus.java
  Lifecycle state of a Booking. No CONFIRMED yet - that transition (and the
  refund-driven CANCELLED path) waits for Payment Service (Phase 4); until
  then a booking only ever moves PENDING -> CANCELLED, or is left PENDING.
*/
package com.orbitra.booking_service.model;

public enum BookingStatus {
    // Created, inventory reserved on Hotel/Flight Service - the only status
    // a booking can start at.
    PENDING,
    // Traveler cancelled - inventory has been released back via Hotel/Flight
    // Service's release endpoint.
    CANCELLED,
    // Reserved for later use (e.g. once a stay/flight date has passed) - not
    // set by any code path yet.
    COMPLETED
}

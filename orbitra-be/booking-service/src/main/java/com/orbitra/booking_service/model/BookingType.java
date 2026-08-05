/*
  BookingType.java
  Which kind of inventory a booking reserves. Not an entity field - which
  concrete class (HotelBooking/FlightBooking) a Booking row is IS its type,
  via JOINED inheritance's discriminator column. Used at the API boundary
  instead (e.g. GET /bookings/mine?type=HOTEL, BookingResponse.type) where a
  plain type-safe value is more convenient than checking instanceof.
*/
package com.orbitra.booking_service.model;

public enum BookingType {
    HOTEL,
    FLIGHT
}

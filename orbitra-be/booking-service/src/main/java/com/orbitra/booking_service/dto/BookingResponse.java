/*
  BookingResponse.java
  Sealed response type for GET /bookings/mine's unified list (mixed hotel +
  flight bookings, one query, one endpoint) - each JSON item only ever has
  its own real fields (no cross-type nulls), with Jackson adding a "type"
  discriminator automatically so the frontend knows which shape it got.
  HotelBookingResponse/FlightBookingResponse are unchanged otherwise - same
  fields as when they were used standalone.
*/
package com.orbitra.booking_service.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = HotelBookingResponse.class, name = "HOTEL"),
        @JsonSubTypes.Type(value = FlightBookingResponse.class, name = "FLIGHT")
})
public sealed interface BookingResponse permits HotelBookingResponse, FlightBookingResponse {
}

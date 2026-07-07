/*
  PartnerType.java
  Sub-classifies an Account whose role is PARTNER, into the fixed inventory type it manages.
  Null for non-PARTNER accounts; every PARTNER account has exactly one type.
*/
package com.orbitra.auth_service.model;

public enum PartnerType {
    // Hotel partner — owns hotel listings, rooms, availability, pricing
    HOTEL,
    // Flight partner — owns flight schedules, seats, pricing
    FLIGHT
}

/*
  Role.java
  Defines the set of account roles recognized by Auth Service.
  Stored on Account as a STRING-mapped enum (not ORDINAL) so column values stay readable and safe to reorder.
*/
package com.orbitra.auth_service.model;

public enum Role {
    // Registered customer — books, pays, reviews (GUEST is unauthenticated and has no stored role)
    TRAVELER,
    // Manages listings; scoped by a fixed PartnerType (HOTEL or FLIGHT) on the same Account
    PARTNER,
    // Platform-wide control, disputes, moderation
    ADMIN
}

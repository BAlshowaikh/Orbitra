/*
  Booking.java
  Abstract JPA base entity for a reservation - JOINED inheritance (Class
  Table Inheritance): this class maps to the shared "booking" table holding
  common fields, while HotelBooking/FlightBooking each map to their own
  extension table (hotel_booking/flight_booking) holding only their
  type-specific fields, joined back to this table by a shared id. Never
  instantiated directly - only its two concrete subclasses are.
*/
package com.orbitra.booking_service.model;

// ------------------- IMPORTS -------------------
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "booking")
@Inheritance(strategy = InheritanceType.JOINED)
// Hibernate manages this column itself (values come from each subclass's
// @DiscriminatorValue, e.g. "HOTEL"/"FLIGHT") - it is NOT a mapped Java
// field here, so there's no getter/setter for it on this class.
@DiscriminatorColumn(name = "type")
@Getter
@Setter
@NoArgsConstructor // required by JPA to instantiate entities via reflection
// SuperBuilder (not plain @Builder) - lets HotelBooking/FlightBooking's own
// @SuperBuilder chain include these inherited fields in their builders too.
@SuperBuilder
public abstract class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Account.id of the traveler who made this booking, in the same loose,
    // no-FK way Hotel.ownerId/Flight.ownerId link to Account - booking-service
    // has its own database, so there's no cross-service foreign key.
    @Column(nullable = false)
    private Long travelerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    // Snapshotted at creation from Hotel/Flight Service's reserve response
    // (Room.basePricePerNight x nights, or FlightSeat.basePricePerSeat) -
    // never recomputed later, so a partner changing their price afterward
    // doesn't retroactively change what a past booking shows/owes. This is
    // also what Payment Service (Phase 4) will charge against.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        // Defaults applied here rather than field initializers so builder
        // callers on the subclasses don't have to remember to set them.
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

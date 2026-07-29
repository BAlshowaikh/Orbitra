/*
  Flight.java
  JPA entity for a single flight route/schedule, owned by a PARTNER account
  (partnerType = FLIGHT). A partner can own many flights - this entity's id is
  its own auto-generated identity, not the owner's account id.
*/
package com.orbitra.flight_service.model;

// ------------------- IMPORTS -------------------
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "flight")
@Getter
@Setter
@NoArgsConstructor // required by JPA to instantiate entities via reflection
@AllArgsConstructor
@Builder
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Account.id of the owning PARTNER, in the same loose, no-FK way
    // Hotel.ownerId links to Account - flight-service has its own database, so
    // there's no cross-service foreign key to enforce this at the DB level.
    @Column(nullable = false)
    private Long ownerId;

    //  a flight number is a real-world identifier
    // for one specific route+schedule, not just a display label.
    @Column(nullable = false, unique = true)
    private String flightNumber;

    // IATA airport codes (e.g. "JFK") - kept as their own columns since
    // search filters by origin/destination directly.
    @Column(nullable = false)
    private String originCode;

    @Column(nullable = false)
    private String destinationCode;

    @Column(nullable = false)
    private LocalDateTime departureTime;

    @Column(nullable = false)
    private LocalDateTime arrivalTime;

    @Column(nullable = false)
    private Integer durationMinutes;

    // Total physical seats on this flight (aircraft capacity) - the sum of
    // every linked FlightSeat.totalInventory must not exceed this value;
    // enforced in FlightSeatService, not at the DB level.
    @Column(nullable = false)
    private Integer seatCount;

    // Soft-delete flag - deactivating a listing (partner's own choice, or
    // admin moderation) never removes the row, since future services
    // (Booking) may still need to reference it.
    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        // Defaults applied here rather than field initializers so @Builder
        // callers don't have to remember to set them.
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

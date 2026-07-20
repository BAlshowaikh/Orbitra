/*
  Hotel.java
  JPA entity for a single hotel listing, owned by a PARTNER account
  (partnerType = HOTEL). A partner can own many hotels - this entity's id is
  its own auto-generated identity, not the owner's account id.
*/
package com.orbitra.hotel_service.model;

// ------------------- IMPORTS -------------------
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hotel")
@Getter
@Setter
@NoArgsConstructor // required by JPA to instantiate entities via reflection
@AllArgsConstructor
@Builder
public class Hotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Account.id of the owning PARTNER, in the same loose, no-FK way
    // UserProfile links to Account - hotel-service has its own database, so
    // there's no cross-service foreign key to enforce this at the DB level.
    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String address;

    // Kept as its own column (not folded into address) since search filters
    // by city directly.
    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String country;

    // Separate hotel_amenity table (one row per amenity string) rather than a
    // single delimited column, so amenities stay individually queryable
    // (e.g. "hotels with a pool") without string-parsing a column.
    @ElementCollection // Tells JPA this is a field of collection of plain values, not a collection of full entities
    @CollectionTable(name = "hotel_amenity", joinColumns = @JoinColumn(name = "hotel_id")) // Tells JPA to create a separate table for the collection, with a foreign key back to this entity
    @Column(name = "amenity", nullable = false)
    @Builder.Default
    private List<String> amenities = new ArrayList<>();

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

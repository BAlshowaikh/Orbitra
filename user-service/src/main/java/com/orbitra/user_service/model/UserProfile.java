/*
  UserProfile.java
  JPA entity backing User Service's profile store. Deliberately keyed by the
  same value as Auth Service's Account.id (never auto-generated here) - the
  two are linked only by sharing that id as a plain column, no cross-database
  foreign key, since each service owns its own separate database.
*/
package com.orbitra.user_service.model;

// ------------------- IMPORTS -------------------
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "user_profile")
@Getter
@Setter
@NoArgsConstructor // required by JPA to instantiate entities via reflection
@AllArgsConstructor
@Builder
public class UserProfile {

    // NOT @GeneratedValue - always explicitly set to the account id taken
    // from the caller's JWT (sub claim), never invented by this database.
    @Id
    private Long id;

    private String firstName;

    private String lastName;

    private String phone;

    private String address;

    private LocalDate dateOfBirth;

    // Plain URL string - this service never handles image bytes/uploads
    // itself, only stores wherever the photo already lives (a future Media
    // Service would just change what fills this field, not this entity).
    private String profilePhotoUrl;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

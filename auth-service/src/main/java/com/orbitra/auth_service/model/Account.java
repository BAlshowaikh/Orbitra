/*
  Account.java
  JPA entity backing Auth Service's identity/credential store.
  Deliberately named Account (not User) to avoid overlap with the future User Service's
  profile entity — this row holds only what's needed to authenticate and authorize,
  nothing about name, address, or preferences.
*/
package com.orbitra.auth_service.model;

// ------------------- IMPORTS -------------------
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
// Explicit table name — also sidesteps "user" being a reserved word in some SQL dialects (Postgres included)
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor // required by JPA to instantiate entities via reflection
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique at the DB level (not just checked in application code) so concurrent
    // registrations with the same email can't both slip through a race condition
    @Column(nullable = false, unique = true)
    private String email;

    // Always a BCrypt hash — never the raw password
    @Column(nullable = false)
    private String password;

    // Stored as STRING, not ORDINAL, so column values stay readable and safe to reorder
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Null unless role == PARTNER; set exactly once at registration for partner accounts
    @Enumerated(EnumType.STRING)
    private PartnerType partnerType;

    // Lets Admin deactivate an account without deleting its history
    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        // Defaults applied here rather than field initializers so @Builder callers
        // don't have to remember to set them
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

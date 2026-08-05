/*
  AccountRepository.java
  Spring Data JPA repository for Account — the only data-access point Auth Service
  uses to look up or persist credential/identity records.
*/
package com.orbitra.auth_service.repository;

// ------------------- IMPORTS -------------------
import com.orbitra.auth_service.model.Account;
import com.orbitra.auth_service.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// ------------------- REPOSITORY -------------------
// The <Account, Long> type parameters indicate that this repository manages Account entities and that the primary key type is Long.
public interface AccountRepository extends JpaRepository<Account, Long> {

    // ------------------ Custom Queries ------------------
    // Used at login to fetch the account to authenticate against
    Optional<Account> findByEmail(String email);

    // Used at registration for a fast pre-check, ahead of the DB unique constraint
    // that guards against a concurrent duplicate registration
    boolean existsByEmail(String email);

    // Used by the bootstrap-only ADMIN self-registration check: once any ADMIN
    // account exists, self-registration as ADMIN closes itself
    boolean existsByRole(Role role);

    // Used by JwtAuthFilter on every authenticated request, so a deactivated
    // account's still-valid JWT stops working immediately instead of only at
    // its next login. A lightweight boolean query, not a full entity fetch.
    boolean existsByIdAndEnabledTrue(Long id);
}

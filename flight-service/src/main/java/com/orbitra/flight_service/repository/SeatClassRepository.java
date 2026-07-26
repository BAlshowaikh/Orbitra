/*
  SeatClassRepository.java
  Spring Data JPA repository for SeatClass — the admin-managed, global seat
  class catalog flight partners pick from.
*/
package com.orbitra.flight_service.repository;

// ------------------- IMPORTS -------------------
import com.orbitra.flight_service.model.SeatClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// ------------------- REPOSITORY -------------------
public interface SeatClassRepository extends JpaRepository<SeatClass, Long> {

    // ------------------ Custom Queries ------------------
    // Used by GET /seat-classes (public) - populates the partner's "choose a
    // seat class" dropdown with only currently-selectable entries.
    List<SeatClass> findByActiveTrue();

    // Used at admin create-time for a fast pre-check, ahead of the DB unique
    // constraint that guards against a concurrent duplicate name.
    boolean existsByName(String name);
}

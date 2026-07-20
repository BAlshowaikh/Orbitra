/*
  RoomTypeRepository.java
  Spring Data JPA repository for RoomType — the admin-managed, global room
  type catalog hotel partners pick from.
*/
package com.orbitra.hotel_service.repository;

// ------------------- IMPORTS -------------------
import com.orbitra.hotel_service.model.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// ------------------- REPOSITORY -------------------
public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {

    // ------------------ Custom Queries ------------------
    // Used by GET /room-types (public) - populates the partner's "choose a
    // room type" dropdown with only currently-selectable entries.
    List<RoomType> findByActiveTrue();

    // Used at admin create-time for a fast pre-check, ahead of the DB unique
    // constraint that guards against a concurrent duplicate name.
    boolean existsByName(String name);
}

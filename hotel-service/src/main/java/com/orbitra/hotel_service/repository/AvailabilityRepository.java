/*
  AvailabilityRepository.java
  Spring Data JPA repository for Availability — per-Room-per-date remaining
  inventory counts. A missing row for a given (room, date) means "fully
  available" (falls back to Room.totalInventory) - see RoomService for where
  that fallback actually gets applied; this repository only ever returns
  what's explicitly been written.
*/
package com.orbitra.hotel_service.repository;

// ------------------- IMPORTS -------------------
import com.orbitra.hotel_service.model.Availability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// ------------------- REPOSITORY -------------------
public interface AvailabilityRepository extends JpaRepository<Availability, Long> {

    // ------------------ Custom Queries ------------------
    // Single-date lookup - the building block the default-fill logic in
    // RoomService calls per date (returns empty when no override exists).
    Optional<Availability> findByRoomIdAndDate(Long roomId, LocalDate date);

    // Range lookup - used both for the partner's own calendar view and as the
    // "which dates already have an explicit row" check when upserting a
    // partner-submitted availability range.
    List<Availability> findByRoomIdAndDateBetween(Long roomId, LocalDate startDate, LocalDate endDate);
}

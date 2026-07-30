/*
  RoomRepository.java
  Spring Data JPA repository for Room — a specific hotel's own instance of a
  RoomType (capacity, price, inventory, facilities).
*/
package com.orbitra.hotel_service.repository;

// ------------------- IMPORTS -------------------
import com.orbitra.hotel_service.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

// ------------------- REPOSITORY -------------------
public interface RoomRepository extends JpaRepository<Room, Long> {

    // ------------------ Custom Queries ------------------
    // Used by the public hotel detail view - only active rooms, never a
    // partner's deactivated ones.
    List<Room> findByHotelIdAndActiveTrue(Long hotelId);

    // Used by the partner's own hotel management view - includes inactive
    // rooms, unlike the public detail view above.
    List<Room> findByHotelId(Long hotelId);

    // Used at room-creation time to enforce "one RoomType per hotel at most
    // once" ahead of the DB unique constraint on (hotel_id, room_type_id) -
    // lets the service layer return a clean 409 instead of a raw constraint
    // violation.
    boolean existsByHotelIdAndRoomTypeId(Long hotelId, Long roomTypeId);

    // Cheapest active room's price for a hotel - empty if it has none yet.
    @Query("SELECT MIN(r.basePricePerNight) FROM Room r WHERE r.hotel.id = :hotelId AND r.active = true")
    Optional<BigDecimal> findMinActivePriceByHotelId(@Param("hotelId") Long hotelId);

    // Row-locks this Room for the duration of the caller's transaction - used
    // by RoomService.reserve()/release() to serialize concurrent reservation
    // attempts against the same room. Needed because a plain guarded UPDATE
    // (like FlightSeat's) can't protect the "no Availability row yet, falls
    // back to totalInventory" case - there's no row to lock there, so two
    // concurrent first-ever reservations for the same room/date could both
    // read "fully available" before either writes. Locking the parent Room
    // row instead serializes all reserve/release calls for that room -
    // coarser than per-date locking, but simple and correct, and this
    // project's scale doesn't need finer-grained throughput.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);
}

/*
  RoomRepository.java
  Spring Data JPA repository for Room — a specific hotel's own instance of a
  RoomType (capacity, price, inventory, facilities).
*/
package com.orbitra.hotel_service.repository;

// ------------------- IMPORTS -------------------
import com.orbitra.hotel_service.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

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
}

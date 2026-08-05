/*
  HotelBookingRepository.java
  Spring Data JPA repository for HotelBooking - used at creation time (save)
  and for the hotel-only slice of a traveler's booking history.
*/
package com.orbitra.booking_service.repository;

// ------------------- IMPORTS -------------------
import com.orbitra.booking_service.model.HotelBooking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// ------------------- REPOSITORY -------------------
public interface HotelBookingRepository extends JpaRepository<HotelBooking, Long> {

    // ------------------ Custom Queries ------------------
    // Used by GET /bookings/mine?type=HOTEL - the hotel-only "My Trips" section.
    Page<HotelBooking> findByTravelerId(Long travelerId, Pageable pageable);
}

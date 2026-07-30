/*
  FlightBookingRepository.java
  Spring Data JPA repository for FlightBooking - used at creation time (save)
  and for the flight-only slice of a traveler's booking history.
*/
package com.orbitra.booking_service.repository;

// ------------------- IMPORTS -------------------
import com.orbitra.booking_service.model.FlightBooking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// ------------------- REPOSITORY -------------------
public interface FlightBookingRepository extends JpaRepository<FlightBooking, Long> {

    // ------------------ Custom Queries ------------------
    // Used by GET /bookings/mine?type=FLIGHT - the flight-only "My Trips" section.
    Page<FlightBooking> findByTravelerId(Long travelerId, Pageable pageable);
}

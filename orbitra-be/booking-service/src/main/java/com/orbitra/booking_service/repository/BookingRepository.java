/*
  BookingRepository.java
  Spring Data JPA repository for the abstract Booking type - used where the
  concrete type (HotelBooking/FlightBooking) doesn't matter yet, e.g.
  cancel's ownership lookup (a bookingId alone doesn't say which type it is).
  Returns real HotelBooking/FlightBooking instances at runtime (JOINED
  inheritance resolves the concrete class via the discriminator column), so
  callers can instanceof-check or pattern-match once they have one.
*/
package com.orbitra.booking_service.repository;

// ------------------- IMPORTS -------------------
import com.orbitra.booking_service.model.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// ------------------- REPOSITORY -------------------
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // ------------------ Custom Queries ------------------
    // Ownership-scoped lookup for cancel - confirms both that the booking
    // exists and that it belongs to the caller, in one query.
    Optional<Booking> findByIdAndTravelerId(Long id, Long travelerId);

    // Used by GET /bookings/mine when no ?type filter is given - one unified
    // "My Trips" query mixing HotelBooking/FlightBooking rows together.
    // Hibernate joins whichever child table each row needs, resolved via the
    // discriminator column, in a single query.
    Page<Booking> findByTravelerId(Long travelerId, Pageable pageable);
}

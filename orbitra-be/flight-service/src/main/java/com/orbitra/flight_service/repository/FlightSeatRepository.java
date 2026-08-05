/*
  FlightSeatRepository.java
  Spring Data JPA repository for FlightSeat — a specific flight's own instance
  of a SeatClass (price, inventory, availability, facilities).
*/
package com.orbitra.flight_service.repository;

// ------------------- IMPORTS -------------------
import com.orbitra.flight_service.model.FlightSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

// ------------------- REPOSITORY -------------------
public interface FlightSeatRepository extends JpaRepository<FlightSeat, Long> {

    // ------------------ Custom Queries ------------------
    // Used by the public flight detail view - only active seats, never a
    // partner's deactivated ones.
    List<FlightSeat> findByFlightIdAndActiveTrue(Long flightId);

    // Used by the partner's own flight management view - includes inactive
    // seats, unlike the public detail view above.
    List<FlightSeat> findByFlightId(Long flightId);

    // Used at seat-creation time to enforce "one SeatClass per flight at most
    // once" ahead of the DB unique constraint on (flight_id, seat_class_id) -
    // lets the service layer return a clean 409 instead of a raw constraint
    // violation.
    boolean existsByFlightIdAndSeatClassId(Long flightId, Long seatClassId);

    // Cheapest active seat's price for a flight - empty if it has none yet.
    @Query("SELECT MIN(fs.basePricePerSeat) FROM FlightSeat fs WHERE fs.flight.id = :flightId AND fs.active = true")
    Optional<BigDecimal> findMinActivePriceByFlightId(@Param("flightId") Long flightId);

    // Sum of totalInventory already allocated to a flight, excluding one seat
    // row (nullable) - the running total FlightSeatService checks against
    // Flight.seatCount on create (excludeSeatId = null) and update
    // (excludeSeatId = the row being changed, so it isn't double-counted).
    @Query("""
            SELECT COALESCE(SUM(fs.totalInventory), 0) FROM FlightSeat fs
            WHERE fs.flight.id = :flightId
            AND (:excludeSeatId IS NULL OR fs.id <> :excludeSeatId)
            """)
    Integer sumTotalInventoryByFlightIdExcluding(@Param("flightId") Long flightId, @Param("excludeSeatId") Long excludeSeatId);

    // Atomic guarded decrement/increment for reserve/release (called by
    // Booking Service) - the WHERE guard is what makes this concurrency-safe:
    // Postgres's own row locking during the UPDATE means two simultaneous
    // reserve calls can't both succeed on the last seat, with no need for a
    // separate optimistic-locking retry loop. Returns rows-affected (0 or 1)
    // so the service layer can tell "reserved" from "nothing left".
    @Modifying
    @Query("UPDATE FlightSeat fs SET fs.availableCount = fs.availableCount - 1 WHERE fs.id = :seatId AND fs.availableCount > 0")
    int decrementAvailableCount(@Param("seatId") Long seatId);

    // Capped at totalInventory so a release can't push availableCount past
    // what the partner actually allocated.
    @Modifying
    @Query("UPDATE FlightSeat fs SET fs.availableCount = fs.availableCount + 1 WHERE fs.id = :seatId AND fs.availableCount < fs.totalInventory")
    int incrementAvailableCount(@Param("seatId") Long seatId);
}

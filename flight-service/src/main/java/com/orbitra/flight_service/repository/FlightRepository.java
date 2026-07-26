/*
  FlightRepository.java
  Spring Data JPA repository for Flight — the only data-access point Flight
  Service uses to look up or persist flight listings.
*/
package com.orbitra.flight_service.repository;

// ------------------- IMPORTS -------------------
import com.orbitra.flight_service.model.Flight;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ------------------- REPOSITORY -------------------
public interface FlightRepository extends JpaRepository<Flight, Long> {

    // ------------------ Custom Queries ------------------
    // Used by GET /flights/mine - a partner's own listings, including inactive
    // ones (unlike public search, which only ever shows active flights).
    Page<Flight> findByOwnerId(Long ownerId, Pageable pageable);

    // Fast pre-check ahead of the DB unique constraint on flightNumber.
    boolean existsByFlightNumber(String flightNumber);

    // GET /flights search - all filter params optional, null ones are no-ops.
    // dayStart/dayEnd are computed by FlightService from a single travelDate
    // (start-of-day / start-of-next-day) rather than truncating
    // f.departureTime to a date in JPQL, the same way hotel-service avoids
    // dialect-specific date functions after its own city-filter type
    // inference bug.
    @Query(
            value = """
                    SELECT f FROM Flight f
                    WHERE f.active = true
                    AND (:originCode IS NULL OR LOWER(f.originCode) = LOWER(CAST(:originCode AS string)))
                    AND (:destinationCode IS NULL OR LOWER(f.destinationCode) = LOWER(CAST(:destinationCode AS string)))
                    AND (:dayStart IS NULL OR (f.departureTime >= :dayStart AND f.departureTime < :dayEnd))
                    AND EXISTS (
                        SELECT 1 FROM FlightSeat fs
                        WHERE fs.flight = f
                        AND fs.active = true
                        AND (:passengers IS NULL OR fs.availableCount >= :passengers)
                        AND (:seatClassName IS NULL OR LOWER(fs.seatClass.name) = LOWER(CAST(:seatClassName AS string)))
                        AND (:minPrice IS NULL OR fs.basePricePerSeat >= :minPrice)
                        AND (:maxPrice IS NULL OR fs.basePricePerSeat <= :maxPrice)
                    )
                    """,
            countQuery = """
                    SELECT COUNT(f) FROM Flight f
                    WHERE f.active = true
                    AND (:originCode IS NULL OR LOWER(f.originCode) = LOWER(CAST(:originCode AS string)))
                    AND (:destinationCode IS NULL OR LOWER(f.destinationCode) = LOWER(CAST(:destinationCode AS string)))
                    AND (:dayStart IS NULL OR (f.departureTime >= :dayStart AND f.departureTime < :dayEnd))
                    AND EXISTS (
                        SELECT 1 FROM FlightSeat fs
                        WHERE fs.flight = f
                        AND fs.active = true
                        AND (:passengers IS NULL OR fs.availableCount >= :passengers)
                        AND (:seatClassName IS NULL OR LOWER(fs.seatClass.name) = LOWER(CAST(:seatClassName AS string)))
                        AND (:minPrice IS NULL OR fs.basePricePerSeat >= :minPrice)
                        AND (:maxPrice IS NULL OR fs.basePricePerSeat <= :maxPrice)
                    )
                    """
    )
    Page<Flight> search(
            @Param("originCode") String originCode,
            @Param("destinationCode") String destinationCode,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            @Param("passengers") Integer passengers,
            @Param("seatClassName") String seatClassName,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable
    );
}

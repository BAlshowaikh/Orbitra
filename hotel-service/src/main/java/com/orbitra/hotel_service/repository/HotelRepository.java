/*
  HotelRepository.java
  Spring Data JPA repository for Hotel — the only data-access point Hotel
  Service uses to look up or persist hotel listings.
*/
package com.orbitra.hotel_service.repository;

// ------------------- IMPORTS -------------------
import com.orbitra.hotel_service.model.Hotel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

// ------------------- REPOSITORY -------------------
public interface HotelRepository extends JpaRepository<Hotel, Long> {

    // ------------------ Custom Queries ------------------
    // Used by GET /hotels/mine - a partner's own listings, including inactive
    // ones (unlike public search, which only ever shows active hotels).
    Page<Hotel> findByOwnerId(Long ownerId, Pageable pageable);

    // GET /hotels search - all filter params optional, null ones are no-ops.
    @Query(
            value = """
                    SELECT h FROM Hotel h
                    WHERE h.active = true
                    AND (:city IS NULL OR LOWER(h.city) = LOWER(:city))
                    AND EXISTS (
                        SELECT 1 FROM Room r
                        WHERE r.hotel = h
                        AND r.active = true
                        AND (:guests IS NULL OR r.capacity >= :guests)
                        AND (:minPrice IS NULL OR r.basePricePerNight >= :minPrice)
                        AND (:maxPrice IS NULL OR r.basePricePerNight <= :maxPrice)
                        AND (:checkIn IS NULL OR NOT EXISTS (
                            SELECT 1 FROM Availability a
                            WHERE a.room = r
                            AND a.date >= :checkIn AND a.date < :checkOut
                            AND a.availableCount < 1
                        ))
                    )
                    """,
            countQuery = """
                    SELECT COUNT(h) FROM Hotel h
                    WHERE h.active = true
                    AND (:city IS NULL OR LOWER(h.city) = LOWER(:city))
                    AND EXISTS (
                        SELECT 1 FROM Room r
                        WHERE r.hotel = h
                        AND r.active = true
                        AND (:guests IS NULL OR r.capacity >= :guests)
                        AND (:minPrice IS NULL OR r.basePricePerNight >= :minPrice)
                        AND (:maxPrice IS NULL OR r.basePricePerNight <= :maxPrice)
                        AND (:checkIn IS NULL OR NOT EXISTS (
                            SELECT 1 FROM Availability a
                            WHERE a.room = r
                            AND a.date >= :checkIn AND a.date < :checkOut
                            AND a.availableCount < 1
                        ))
                    )
                    """
    )
    Page<Hotel> search(
            @Param("city") String city,
            @Param("guests") Integer guests,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            Pageable pageable
    );
}

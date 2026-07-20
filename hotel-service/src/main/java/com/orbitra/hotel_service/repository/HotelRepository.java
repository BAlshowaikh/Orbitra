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

// ------------------- REPOSITORY -------------------
public interface HotelRepository extends JpaRepository<Hotel, Long> {

    // ------------------ Custom Queries ------------------
    // Used by GET /hotels/mine - a partner's own listings, including inactive
    // ones (unlike public search, which only ever shows active hotels).
    Page<Hotel> findByOwnerId(Long ownerId, Pageable pageable);
}

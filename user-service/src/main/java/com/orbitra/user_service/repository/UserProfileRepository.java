/*
  UserProfileRepository.java
  Spring Data JPA repository for UserProfile — the only data-access point
  User Service uses to look up or persist profile records.
*/
package com.orbitra.user_service.repository;

// ------------------- IMPORTS -------------------
import com.orbitra.user_service.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

// ------------------- REPOSITORY -------------------
// findById/save (inherited from JpaRepository) cover both the lookup and the
// upsert - no custom queries needed yet since profiles are only ever
// accessed by their own id (the caller's own account id from the JWT).
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
}

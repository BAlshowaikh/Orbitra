/*
  UserProfileService.java
  Business logic for the caller's own profile: fetch it, or create/update it
  in one upsert operation. The only place that decides how a UserProfile row
  gets built from a request.
*/
package com.orbitra.user_service.service;

// --------------- IMPORTS ---------------
import com.orbitra.user_service.dto.UserProfileRequest;
import com.orbitra.user_service.dto.UserProfileResponse;
import com.orbitra.user_service.exception.ProfileNotFoundException;
import com.orbitra.user_service.model.UserProfile;
import com.orbitra.user_service.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    private final UserProfileRepository userProfileRepository;

    public UserProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    // ---------------- METHOD 1: Get the caller's own profile ----------------
    // Read-only - no @Transactional needed since nothing is written. 404s
    // until the caller has PUT a profile at least once - there's no
    // proactive creation at registration (see project decision).
    public UserProfileResponse getProfile(Long accountId) {
        UserProfile profile = userProfileRepository.findById(accountId)
                .orElseThrow(() -> new ProfileNotFoundException("No profile yet for this account"));

        return toResponse(profile);
    }

    // ---------------- METHOD 2: Create or update the caller's own profile ----------------
    // @Transactional: the lookup and the save() happen as one atomic unit.
    @Transactional
    public UserProfileResponse upsertProfile(Long accountId, UserProfileRequest request) {
        UserProfile profile = userProfileRepository.findById(accountId)
                .orElseGet(() -> {
                    // Brand new profile - id is never auto-generated, it's
                    // always explicitly set to the caller's own account id.
                    UserProfile p = new UserProfile();
                    p.setId(accountId);
                    return p;
                });

        profile.setFirstName(request.firstName());
        profile.setLastName(request.lastName());
        profile.setPhone(request.phone());
        profile.setAddress(request.address());
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setProfilePhotoUrl(request.profilePhotoUrl());

        userProfileRepository.save(profile);
        log.info("Upserted profile for account id={}", accountId);

        return toResponse(profile);
    }

    // ---------------- HELPER 3: Map entity -> response DTO ----------------
    private UserProfileResponse toResponse(UserProfile profile) {
        return new UserProfileResponse(
                profile.getFirstName(),
                profile.getLastName(),
                profile.getPhone(),
                profile.getAddress(),
                profile.getDateOfBirth(),
                profile.getProfilePhotoUrl()
        );
    }
}

/*
  UserProfileService.java
  Business logic for the caller's own profile: fetch it, or create/update it
  in one upsert operation. The only place that decides how a UserProfile row
  gets built from a request.
*/
package com.orbitra.user_service.service;

// --------------- IMPORTS ---------------
import com.orbitra.user_service.dto.UserProfileResponse;
import com.orbitra.user_service.exception.InvalidProfileRequestException;
import com.orbitra.user_service.exception.ProfileNotFoundException;
import com.orbitra.user_service.model.UserProfile;
import com.orbitra.user_service.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;

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
    // Takes the raw request body as a JsonNode, not a bound/validated DTO -
    // this is what lets a field OMITTED from the JSON mean "leave it
    // unchanged", distinct from a field explicitly sent as null (which means
    // "clear it"). A plain DTO can't tell those two cases apart, since both
    // deserialize to the same Java null. @Transactional: the lookup and the
    // save() happen as one atomic unit.
    @Transactional
    public UserProfileResponse upsertProfile(Long accountId, JsonNode body) {
        UserProfile profile = userProfileRepository.findById(accountId)
                .orElseGet(() -> {
                    // Brand new profile - id is never auto-generated, it's
                    // always explicitly set to the caller's own account id.
                    UserProfile p = new UserProfile();
                    p.setId(accountId);
                    return p;
                });

        // firstName/lastName are still required on every save, present or not.
        profile.setFirstName(requireNonBlank(body, "firstName"));
        profile.setLastName(requireNonBlank(body, "lastName"));

        // Everything else: only touched if the field actually appears in the
        // request body at all - omitted means "leave the existing value".
        applyIfPresent(body, "phone", profile::setPhone);
        applyIfPresent(body, "address", profile::setAddress);
        applyIfPresent(body, "profilePhotoUrl", profile::setProfilePhotoUrl);

        if (body.has("dateOfBirth")) {
            JsonNode node = body.get("dateOfBirth");
            profile.setDateOfBirth(node.isNull() ? null : parseDate(node.asString()));
        }

        userProfileRepository.save(profile);
        log.info("Upserted profile for account id={}", accountId);

        return toResponse(profile);
    }

    // ---------------- HELPER: Require a non-blank string field ----------------
    private String requireNonBlank(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || node.asString().isBlank()) {
            throw new InvalidProfileRequestException(field + " must not be blank");
        }
        return node.asString();
    }

    // ---------------- HELPER: Apply a string field only if present in the body ----------------
    private void applyIfPresent(JsonNode body, String field, Consumer<String> setter) {
        if (body.has(field)) {
            JsonNode node = body.get(field);
            setter.accept(node.isNull() ? null : node.asString());
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidProfileRequestException("dateOfBirth must be a valid ISO date (yyyy-MM-dd)");
        }
    }

    // ---------------- HELPER: Map entity -> response DTO ----------------
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

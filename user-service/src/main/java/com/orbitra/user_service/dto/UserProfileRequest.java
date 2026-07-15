/*
  UserProfileRequest.java
  Request body for PUT /users/me. Crosses the HTTP boundary in place of the
  UserProfile entity - the caller never sets id/createdAt/updatedAt directly,
  those are derived from the caller's own JWT or managed by the entity itself.
*/
package com.orbitra.user_service.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UserProfileRequest(
        // A profile without a name isn't a meaningful profile - everything
        // else here is genuinely optional.
        @NotBlank String firstName,
        @NotBlank String lastName,

        String phone,
        String address,
        LocalDate dateOfBirth,

        // Plain URL - this service only stores wherever the photo already
        // lives, it never handles the image bytes itself.
        String profilePhotoUrl
) {
}

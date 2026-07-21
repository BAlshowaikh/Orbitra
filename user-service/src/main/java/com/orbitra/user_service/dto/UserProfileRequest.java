/*
  UserProfileRequest.java
  Documents the expected shape of PUT /users/profile's request body. Not
  currently bound to by UserController - that endpoint reads the raw request
  as a JsonNode instead (see UserProfileService.upsertProfile), so it can
  distinguish an omitted field ("leave unchanged") from an explicit null
  ("clear it"), which a plain record can't do. Kept here as documentation of
  the field shape, not as an active DTO.
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

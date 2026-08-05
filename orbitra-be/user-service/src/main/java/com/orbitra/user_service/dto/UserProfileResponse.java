/*
  UserProfileResponse.java
  Response shape for GET/PUT /users/me - the caller's own profile. No id or
  timestamps: this is always the caller's own record (identified by their
  JWT, never by an id in the request/response body), and nothing currently
  needs "member since"/"last updated" info.
*/
package com.orbitra.user_service.dto;

import java.time.LocalDate;

public record UserProfileResponse(
        String firstName,
        String lastName,
        String phone,
        String address,
        LocalDate dateOfBirth,
        String profilePhotoUrl
) {
}

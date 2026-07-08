/*
  RegisterRequest.java
  Request body for POST /auth/register. Crosses the HTTP boundary in place of
  the Account entity, so callers never see/set fields like id or enabled.
*/
package com.orbitra.auth_service.dto;

import com.orbitra.auth_service.model.PartnerType;
import com.orbitra.auth_service.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,

        // Plain text here only - AuthService hashes it before it ever touches the database
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,

        @NotNull Role role,

        // Required only when role == PARTNER; that cross-field rule can't be expressed with
        // a simple annotation here, so AuthService validates it explicitly at registration
        PartnerType partnerType
) {
}

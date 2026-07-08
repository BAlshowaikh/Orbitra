/*
  LoginRequest.java
  Request body for POST /auth/login.
*/
package com.orbitra.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// A record: Java auto-generates the constructor, email()/password() accessors,
// equals()/hashCode()/toString() from the two fields below - no boilerplate needed.
public record LoginRequest(
        // Bean Validation annotations only take effect when a controller method
        // takes this record with @Valid - Spring then rejects bad input (400)
        // before AuthService ever sees it.
        @Email @NotBlank String email,
        @NotBlank String password
) {
}

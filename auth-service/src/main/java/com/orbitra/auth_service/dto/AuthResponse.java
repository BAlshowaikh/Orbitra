/*
  AuthResponse.java
  Response body returned by both POST /auth/register and POST /auth/login on success.
*/
package com.orbitra.auth_service.dto;

import com.orbitra.auth_service.model.Role;

public record AuthResponse(
        // Signed JWT; its subject claim is the Account id, not the email - see JwtService
        String token,

        long expiresInMs,

        Role role
) {
}

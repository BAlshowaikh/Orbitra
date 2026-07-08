/*
  MeResponse.java
  Response body for GET /auth/me - identifies the caller from their JWT alone,
  no database lookup involved.
*/
package com.orbitra.auth_service.dto;

import com.orbitra.auth_service.model.Role;

public record MeResponse(
        Long accountId,
        Role role
) {
}

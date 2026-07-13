/*
  UpdateAccountStatusRequest.java
  Request body for PATCH /auth/admin/accounts/{id}/status - lets an admin
  activate or deactivate a single account.
*/
package com.orbitra.auth_service.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(
        // Boxed Boolean, not primitive boolean - @NotNull can only catch a
        // missing/null field this way; a primitive would silently default to false.
        @NotNull Boolean enabled
) {
}

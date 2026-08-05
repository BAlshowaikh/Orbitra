/*
  AccountSummaryResponse.java
  Response shape for admin-facing account views (list all accounts, and the
  result of an activate/deactivate call). Deliberately excludes password -
  admin endpoints still never expose the entity or its hash directly.
*/
package com.orbitra.auth_service.dto;

import com.orbitra.auth_service.model.PartnerType;
import com.orbitra.auth_service.model.Role;

import java.time.Instant;

public record AccountSummaryResponse(
        Long id,
        String email,
        Role role,

        // Null for non-PARTNER accounts, same as on Account itself
        PartnerType partnerType,

        boolean enabled,
        Instant createdAt
) {
}

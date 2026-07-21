/*
  UserController.java
  Thin HTTP layer for the caller's own profile: view and upsert. Business
  logic lives entirely in UserProfileService - this class only handles
  request binding, delegation, and reading the account id off the
  authenticated caller. The PUT body is bound as a raw JsonNode rather than a
  validated DTO - see UserProfileService.upsertProfile for why.
*/
package com.orbitra.user_service.controller;

// ----------- IMPORTS -----------
import com.orbitra.user_service.dto.UserProfileResponse;
import com.orbitra.user_service.service.UserProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    // ------------------ Endpoint 1: Get my profile -----------------
    // 404 (via ProfileNotFoundException) until the caller has PUT one.
    @GetMapping("/profile")
    public UserProfileResponse getProfile(Authentication authentication) {
        Long accountId = extractAccountId(authentication);
        return userProfileService.getProfile(accountId);
    }

    // ------------------ Endpoint 2: Create or update my profile -----------------
    // Same endpoint handles both the very first save and every edit after -
    // see UserProfileService.upsertProfile for the create-or-update logic.
    @PutMapping("/profile")
    public UserProfileResponse putProfile(Authentication authentication, @RequestBody JsonNode body) {
        Long accountId = extractAccountId(authentication);
        return userProfileService.upsertProfile(accountId, body);
    }

    // Principal is the account id (a Long), not email - set that way by
    // JwtAuthFilter, same convention as auth-service.
    private Long extractAccountId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}

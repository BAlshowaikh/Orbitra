/*
  AuthController.java
  Thin HTTP layer for authentication: register, login, and the protected /me
  endpoint. Business logic lives entirely in AuthService - this class only
  handles request binding (@Valid), delegation, and response shaping.
*/

/*
 ---------- Notes:
    1. Spring automatically converts the returned AuthResponse/MeResponse record
        into JSON, and sets the Content-Type header to application/json.
    2. Spring automatically converts the request body JSON into a RegisterRequest DTO 
        because of the @RequestBody annotation.
    3. @Valid triggers the Bean Validation annotations on RegisterRequest
        and LoginRequest DTO fields before this method body ever runs.
    4. Authentication as a plain method parameter (no annotation needed) - Spring
        MVC recognizes it because Authentication implements java.security.Principal,
        and fills it in from whatever JwtAuthFilter put in the SecurityContext.
    5. accountId comes from the JWT's sub claim (JwtAuthFilter set it as the
        principal); role comes from the authorities list, with the "ROLE_" prefix
        stripped back off since that prefix is only a Spring Security convention -
        the Role enum's actual values (TRAVELER/PARTNER/ADMIN) don't include it.
*/

package com.orbitra.auth_service.controller;

// ----------- IMPORTS -----------
import com.orbitra.auth_service.dto.AuthResponse;
import com.orbitra.auth_service.dto.LoginRequest;
import com.orbitra.auth_service.dto.MeResponse;
import com.orbitra.auth_service.dto.RegisterRequest;
import com.orbitra.auth_service.model.Role;
import com.orbitra.auth_service.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Public per SecurityConfig. @Valid triggers the Bean Validation annotations
    // on RegisterRequest before this method body ever runs.
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    // Public per SecurityConfig.
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // Protected - reaching this method at all means JwtAuthFilter already
    // validated the token and populated Authentication, so identity is read
    // straight off it with no database lookup. Spring MVC injects Authentication
    // automatically since it implements java.security.Principal.
    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        Long accountId = (Long) authentication.getPrincipal();
        Role role = Role.valueOf(extractRoleName(authentication));
        return new MeResponse(accountId, role);
    }

    // Undoes the "ROLE_" prefix JwtAuthFilter added, since that prefix is a
    // Spring Security convention, not part of the Role enum itself.
    private String extractRoleName(Authentication authentication) {
        GrantedAuthority authority = authentication.getAuthorities().iterator().next();
        return authority.getAuthority().substring("ROLE_".length());
    }
}

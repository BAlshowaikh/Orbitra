/*
  JwtAuthFilter.java
  Runs once per request, ahead of Spring Security's normal authentication
  step. Reads the Authorization: Bearer <token> header, and if it's a valid
  JWT (issued by auth-service), populates the SecurityContext so the rest of
  the request is treated as authenticated.
*/
package com.orbitra.booking_service.security;

// ----------- IMPORTS -----------
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    // ------------ METHOD 1: Authenticate the request from its JWT, if present/valid ------------
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER_NAME);

        // No/malformed header: leave SecurityContext empty and move on - this
        // service has no public routes, so anything unauthenticated gets
        // rejected downstream anyway.
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());

        if (jwtService.isTokenValid(token)) {
            Long accountId = jwtService.extractAccountId(token);
            String role = jwtService.extractRole(token);

            // principal = account id, not email - the same identifier used as
            // the JWT's sub claim and shared across services. Also what
            // ownership checks compare against Booking.travelerId.
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    accountId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        // Invalid/expired token: SecurityContext stays empty rather than
        // rejecting the request here directly - authorizeHttpRequests handles
        // the actual reject-or-allow decision. Note: like hotel/flight-
        // service's filters, this service has no access to the accounts
        // table (separate database), so it cannot re-check enabled status
        // per request - a deactivated account's still-valid token keeps
        // working here until it naturally expires.

        filterChain.doFilter(request, response);
    }
}

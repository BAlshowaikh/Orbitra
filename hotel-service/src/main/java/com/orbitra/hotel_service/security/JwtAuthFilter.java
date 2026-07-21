/*
  JwtAuthFilter.java
  Runs once per request, ahead of Spring Security's normal authentication
  step. Reads the Authorization: Bearer <token> header, and if it's a valid
  JWT (issued by auth-service), populates the SecurityContext so the rest of
  the request is treated as authenticated.
*/
package com.orbitra.hotel_service.security;

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
import java.util.ArrayList;
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

        // No/malformed header: leave SecurityContext empty and move on -
        // permitAll routes (public search/detail/catalog GETs) still work;
        // anything else gets rejected downstream for having no authentication.
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());

        if (jwtService.isTokenValid(token)) {
            Long accountId = jwtService.extractAccountId(token);
            String role = jwtService.extractRole(token);
            String partnerType = jwtService.extractPartnerType(token);

            // "ROLE_" prefix is a Spring Security convention required for
            // hasRole()-style checks to recognize this as a role authority.
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));

            // Extra "PARTNER_<type>" authority (e.g. PARTNER_HOTEL) - this is
            // the first service where SecurityConfig actually checks for this,
            // since role alone (PARTNER) can't distinguish a hotel partner
            // from a flight partner.
            if (partnerType != null) {
                authorities.add(new SimpleGrantedAuthority("PARTNER_" + partnerType));
            }

            // principal = account id, not email - the same identifier used as
            // the JWT's sub claim and shared across services. Also what
            // ownership checks compare against Hotel.ownerId.
            Authentication authentication = new UsernamePasswordAuthenticationToken(accountId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        // Invalid/expired token: SecurityContext stays empty rather than
        // rejecting the request here directly - authorizeHttpRequests handles
        // the actual reject-or-allow decision. Note: unlike auth-service's
        // filter, this service has no access to the accounts table (separate
        // database), so it cannot re-check enabled status per request - a
        // deactivated account's still-valid token keeps working here until it
        // naturally expires.

        filterChain.doFilter(request, response);
    }
}

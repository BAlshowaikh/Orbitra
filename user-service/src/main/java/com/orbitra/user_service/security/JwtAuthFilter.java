/*
  JwtAuthFilter.java
  Runs once per request, ahead of Spring Security's normal authentication
  step. Reads the Authorization: Bearer <token> header, and if it's a valid
  JWT (issued by auth-service), populates the SecurityContext so the rest of
  the request is treated as authenticated.
*/
package com.orbitra.user_service.security;

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
        // anything under /users/** gets rejected downstream for having no
        // authentication (no public routes exist in this service).
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
            // hasRole()-style checks to recognize this as a role authority -
            // not used by any endpoint yet, but kept consistent with
            // auth-service's filter in case a role-restricted route is added later.
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));

            // Extra "PARTNER_<type>" authority, mirroring auth-service's filter -
            // unused by this service's own routes today, kept for consistency.
            if (partnerType != null) {
                authorities.add(new SimpleGrantedAuthority("PARTNER_" + partnerType));
            }

            // (principal, credentials, authorities) - principal is normally the
            // username, here it's accountId instead; credentials is normally the
            // password, left null since the JWT check above is already the proof;
            // authorities is the role list from above.
            Authentication authentication = new UsernamePasswordAuthenticationToken(accountId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        // Invalid/expired token: SecurityContext stays empty rather than
        // rejecting the request here directly - authorizeHttpRequests handles
        // the actual reject-or-allow decision.

        filterChain.doFilter(request, response);
    }
}

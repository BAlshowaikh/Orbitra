/*
  JwtAuthFilter.java
  Runs once per request, ahead of Spring Security's normal authentication step.
  Reads the Authorization: Bearer <token> header, and if it's a valid JWT,
  populates the SecurityContext so the rest of the request is treated as
  authenticated - this is what actually enforces SecurityConfig's
  anyRequest().authenticated() rule.
*/
package com.orbitra.auth_service.security;

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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER_NAME);

        // No/malformed header: leave SecurityContext empty and move on. Public
        // routes (permitAll in SecurityConfig) still work; anything else gets
        // rejected downstream for having no authentication.
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());

        if (jwtService.isTokenValid(token)) {
            Long accountId = jwtService.extractAccountId(token);
            String role = jwtService.extractRole(token);

            // "ROLE_" prefix is a Spring Security convention required for
            // hasRole()-style checks to recognize this as a role authority.
            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

            // principal = account id, not email - the same identifier used as
            // the JWT's sub claim and shared across services.
            Authentication authentication = new UsernamePasswordAuthenticationToken(accountId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        // Invalid/expired token: SecurityContext stays empty rather than
        // rejecting the request here directly - authorizeHttpRequests handles
        // the actual reject-or-allow decision.

        filterChain.doFilter(request, response);
    }
}

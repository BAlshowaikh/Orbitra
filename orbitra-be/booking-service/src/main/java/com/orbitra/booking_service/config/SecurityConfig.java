/*
  SecurityConfig.java
  Configures the Spring Security filter chain for Booking Service: stateless
  sessions (no HttpSession, no cookies), CSRF disabled (meaningless without
  session cookies to forge). No public routes, and no partner/admin routes
  either - every endpoint here is a traveler action (book, cancel, view own
  trips), so the whole service is locked to hasRole("TRAVELER") rather than
  the looser anyRequest().authenticated() user-service uses - a PARTNER or
  ADMIN account has no legitimate reason to hit any route here.
*/
package com.orbitra.booking_service.config;

// ------------- IMPORTS -------------
import com.orbitra.booking_service.security.JwtAuthFilter;
import com.orbitra.booking_service.security.RestAccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    // Turns a wrong-role 403 into the app's normal JSON ErrorResponse shape
    // instead of Spring Security's blank default body.
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, RestAccessDeniedHandler restAccessDeniedHandler) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
    }

    // ------------ METHOD 1: Build the security filter chain ------------
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // turns off a browser-cookie-session protection
                .csrf(csrf -> csrf.disable())
                // Stateless: no HttpSession, no cookies - every request proves its own identity
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // No public routes, no role-mix like hotel/flight-service -
                // every single endpoint requires ROLE_TRAVELER.
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasRole("TRAVELER")
                )
                // Run our JWT check before Spring Security's built-in username/password
                // filter, so SecurityContext is already populated by the time
                // authorizeHttpRequests decides whether to allow the request.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // hasRole() rejections land here instead of Spring Security's
                // default blank-body 403.
                .exceptionHandling(ex -> ex.accessDeniedHandler(restAccessDeniedHandler));

        return http.build();
    }
}

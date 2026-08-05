/*
  SecurityConfig.java
  Configures the Spring Security filter chain for Flight Service: stateless
  sessions (no HttpSession, no cookies), CSRF disabled (meaningless without
  session cookies to forge). Like hotel-service, this service has public
  routes (browsing/search, GUEST role) alongside partner-only and admin-only
  ones.
*/
package com.orbitra.flight_service.config;

// ------------- IMPORTS -------------
import com.orbitra.flight_service.security.JwtAuthFilter;
import com.orbitra.flight_service.security.RestAccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    // Turns a wrong-authority 403 into the app's normal JSON ErrorResponse
    // shape instead of Spring Security's blank default body.
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
                // Order matters - first match wins, so more specific rules
                // (GET /flights/mine, PATCH .../status) must come before the
                // generic single-segment GET /flights/{id} rule they'd
                // otherwise be shadowed by or shadow.
                .authorizeHttpRequests(auth -> auth
                        // --- Public (GUEST) - browsing/search only ---
                        // No separate /search sub-route - GET /flights IS the
                        // list, with every filter (origin, destination, date,
                        // passengers, seat class, price) as an optional query
                        // param. No filters = browse all active flights; any
                        // combination narrows the results.
                        .requestMatchers(HttpMethod.GET, "/flights").permitAll()
                        .requestMatchers(HttpMethod.GET, "/seat-classes").permitAll()

                        // --- Partner's own management views/actions ---
                        // Must precede the generic GET /flights/{id} rule below,
                        // since "mine" would otherwise match that {id} placeholder.
                        .requestMatchers(HttpMethod.GET, "/flights/mine").hasAuthority("PARTNER_FLIGHT")

                        // --- Public flight detail view ---
                        // Single path segment only - does not match deeper paths
                        // like /flights/{id}/seats/**, so it can't accidentally
                        // expose the partner-only seat routes below.
                        .requestMatchers(HttpMethod.GET, "/flights/{id}").permitAll()

                        // --- Flight status: partner (own listing) or admin (moderation) ---
                        // Ownership vs. admin-bypass is enforced in the service
                        // layer, not here - this rule only gates who may attempt it.
                        .requestMatchers(HttpMethod.PATCH, "/flights/{id}/status")
                        .hasAnyAuthority("PARTNER_FLIGHT", "ROLE_ADMIN")

                        // --- List a flight's seats - public, but must precede
                        //     the partner-only blanket rule below or it would
                        //     be shadowed by it. Owner sees inactive seats too;
                        //     everyone else only sees active ones (enforced in
                        //     FlightSeatService, not here). ---
                        .requestMatchers(HttpMethod.GET, "/flights/*/seats").permitAll()

                        // --- Reserve/release - TRAVELER only, called by Booking
                        //     Service forwarding the traveler's own JWT (not the
                        //     owning partner's). Must precede the partner-only
                        //     blanket "/flights/*/seats/**" rule below, or that
                        //     rule would shadow these and reject every traveler. ---
                        .requestMatchers(HttpMethod.POST, "/flights/*/seats/*/reserve").hasRole("TRAVELER")
                        .requestMatchers(HttpMethod.POST, "/flights/*/seats/*/release").hasRole("TRAVELER")

                        // --- Flight/seat writes - all partner-only, no public
                        //     GETs under a specific flight's seats beyond the
                        //     list route above ---
                        .requestMatchers(HttpMethod.POST, "/flights").hasAuthority("PARTNER_FLIGHT")
                        .requestMatchers(HttpMethod.PATCH, "/flights/{id}").hasAuthority("PARTNER_FLIGHT")
                        .requestMatchers("/flights/*/seats/**").hasAuthority("PARTNER_FLIGHT")

                        // --- Seat class catalog - admin-managed, GET already
                        //     covered above, everything else (create/update/status) ---
                        .requestMatchers("/seat-classes/**").hasRole("ADMIN")

                        // Everything else just requires authentication.
                        .anyRequest().authenticated()
                )
                // Run our JWT check before Spring Security's built-in username/password
                // filter, so SecurityContext is already populated by the time
                // authorizeHttpRequests decides whether to allow the request.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // hasAuthority()/hasRole() rejections land here instead of
                // Spring Security's default blank-body 403.
                .exceptionHandling(ex -> ex.accessDeniedHandler(restAccessDeniedHandler));

        return http.build();
    }
}

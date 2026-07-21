/*
  SecurityConfig.java
  Configures the Spring Security filter chain for Hotel Service: stateless
  sessions (no HttpSession, no cookies), CSRF disabled (meaningless without
  session cookies to forge). Unlike user-service, this service has public
  routes (browsing/search, GUEST role) alongside partner-only and admin-only
  ones - the first service with that mix since auth-service.
*/
package com.orbitra.hotel_service.config;

// ------------- IMPORTS -------------
import com.orbitra.hotel_service.security.JwtAuthFilter;
import com.orbitra.hotel_service.security.RestAccessDeniedHandler;
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
                // (GET /hotels/mine, PATCH .../status) must come before the
                // generic single-segment GET /hotels/{id} rule they'd
                // otherwise be shadowed by or shadow.
                .authorizeHttpRequests(auth -> auth
                        // --- Public (GUEST) - browsing/search only ---
                        // No separate /search sub-route - GET /hotels IS the
                        // list, with every filter (city, dates, price, guests)
                        // as an optional query param. No filters = browse all
                        // active hotels; any combination narrows the results.
                        .requestMatchers(HttpMethod.GET, "/hotels").permitAll()
                        .requestMatchers(HttpMethod.GET, "/room-types").permitAll()

                        // --- Partner's own management views/actions ---
                        // Must precede the generic GET /hotels/{id} rule below,
                        // since "mine" would otherwise match that {id} placeholder.
                        .requestMatchers(HttpMethod.GET, "/hotels/mine").hasAuthority("PARTNER_HOTEL")

                        // --- Public hotel detail view ---
                        // Single path segment only - does not match deeper paths
                        // like /hotels/{id}/rooms/**, so it can't accidentally
                        // expose the partner-only room/availability routes below.
                        .requestMatchers(HttpMethod.GET, "/hotels/{id}").permitAll()

                        // --- Hotel status: partner (own listing) or admin (moderation) ---
                        // Ownership vs. admin-bypass is enforced in the service
                        // layer, not here - this rule only gates who may attempt it.
                        .requestMatchers(HttpMethod.PATCH, "/hotels/{id}/status")
                        .hasAnyAuthority("PARTNER_HOTEL", "ROLE_ADMIN")

                        // --- Hotel/room/availability writes and the partner's
                        //     own availability calendar view - all partner-only,
                        //     no public GETs under a specific hotel's rooms ---
                        .requestMatchers(HttpMethod.POST, "/hotels").hasAuthority("PARTNER_HOTEL")
                        .requestMatchers(HttpMethod.PUT, "/hotels/{id}").hasAuthority("PARTNER_HOTEL")
                        .requestMatchers("/hotels/*/rooms/**").hasAuthority("PARTNER_HOTEL")

                        // --- Room type catalog - admin-managed, GET already
                        //     covered above, everything else (create/update/status) ---
                        .requestMatchers("/room-types/**").hasRole("ADMIN")

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

/*
  SecurityConfig.java
  Configures the Spring Security filter chain for User Service: stateless
  sessions (no HttpSession, no cookies), CSRF disabled (meaningless without
  session cookies to forge). Unlike auth-service, there is no public route
  here - every /users/** endpoint requires a valid JWT, since this service
  has nothing a caller could need before authenticating.
*/
package com.orbitra.user_service.config;

// ------------- IMPORTS -------------
import com.orbitra.user_service.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    // JwtAuthFilter is a @Component, so Spring can inject it here.
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    // ------------ METHOD 1: Build the security filter chain ------------
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // turns off a browser-cookie-session protection
                .csrf(csrf -> csrf.disable())
                // Stateless: no HttpSession, no cookies - every request proves its own identity
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // No public routes - everything requires a valid JWT
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                // Run our JWT check before Spring Security's built-in username/password
                // filter, so SecurityContext is already populated by the time
                // authorizeHttpRequests decides whether to allow the request.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

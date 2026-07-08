/*
  SecurityConfig.java
  Configures the Spring Security filter chain for Auth Service: stateless
  sessions (no HttpSession, no cookies - every request proves its own identity),
  CSRF disabled (meaningless without session cookies to forge), and public
  access to /auth/register and /auth/login. Everything else requires authentication
*/
package com.orbitra.auth_service.config;

// ------------- IMPORTS -------------
import com.orbitra.auth_service.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

// Marks this class as a source of bean definitions - Spring scans it at
// startup and runs its @Bean methods to build objects for the container.
@Configuration
public class SecurityConfig {

    // JwtAuthFilter is a @Component, so Spring can inject it here the same way
    // it injects PasswordEncoder/JwtService elsewhere.
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    // Marks this method's return value as a bean: Spring calls it once at
    // startup, keeps the result, and injects that same PasswordEncoder
    // instance into any other class that asks for one (e.g. AuthService).
    //
    // One-way hash with a built-in random salt. AuthService uses this to hash
    // passwords before saving (register) and to compare raw vs. hash (login) -
    // the plain password itself is never stored or compared directly.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Same idea as above - Spring builds this SecurityFilterChain once and
    // applies it to every incoming request.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // turns off a browser-cookie-session protection
                .csrf(csrf -> csrf.disable())
                // Stateless: no HttpSession, no cookies, don't remember anyone's session - every request proves its own identity
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Only /auth/register and /auth/login are public; everything else requires authentication
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/register", "/auth/login").permitAll()
                        .anyRequest().authenticated()
                )
                // Run our JWT check before Spring Security's built-in username/password
                // filter, so SecurityContext is already populated by the time
                // authorizeHttpRequests decides whether to allow the request.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

/*
  JwtService.java
  Parses and validates JWTs issued by auth-service. This service never
  generates tokens - it only ever validates ones it receives, using the same
  shared secret auth-service signs with (app.jwt.secret, must match exactly).
*/
package com.orbitra.hotel_service.security;

// ----------- IMPORTS -----------
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Service
public class JwtService {

    private final SecretKey key;

    // @Value pulls app.jwt.secret from application.properties (which in turn
    // resolves from the JWT_SECRET env var / .env) - Spring injects this at
    // construction time.
    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ------------ METHOD 1: Parse and validate a JWT, extracting claims ------------
    // Parses the token's claims, verifying the signature and expiration in
    // the process - throws JwtException (or a subclass) if the token was
    // tampered with, expired, or malformed.
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ------------ METHOD 2: Extract the account id from a valid JWT ------------
    public Long extractAccountId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    // ------------ METHOD 3: Extract the role from a valid JWT ------------
    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    // ------------ METHOD 4: Extract the partnerType from a valid JWT ------------
    // Null for non-partner accounts - callers must not assume this is always
    // present. This is the claim hotel-service actually relies on (unlike
    // user-service's copy, which only mirrors the extraction for consistency)
    // to tell a HOTEL partner apart from a FLIGHT partner.
    public String extractPartnerType(String token) {
        return parseClaims(token).get("partnerType", String.class);
    }

    // ------------ METHOD 5: Validate a JWT's signature and expiration ------------
    // Used by the auth filter to decide whether to reject a request before
    // trusting anything extracted from the token.
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}

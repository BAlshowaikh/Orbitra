/*
  JwtService.java
  Generates and parses/validates JWTs for Auth Service. The subject (sub) claim
  is always Account.id (not email), since that's the identifier every downstream
  service uses to identify whose data a request is about.
*/
package com.orbitra.auth_service.security;

// ----------- IMPORTS -----------
import com.orbitra.auth_service.model.Account;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    // @Value pulls app.jwt.secret / app.jwt.expiration-ms from application.properties
    // (which in turn resolve from the real JWT_SECRET/JWT_EXPIRATION_MS env vars, or
    // their dev fallbacks) - Spring injects these at construction time.
    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // ------------ METHOD 1: Generate a signed JWT for the given account ------------
    // Builds a signed JWT for the given account: sub = account id, plus role and
    // partnerType claims so downstream services (e.g. hotel-service telling a
    // HOTEL partner apart from a FLIGHT partner) can authorize without a
    // database lookup on every request - services never call each other
    // synchronously, so these claims are the only source of truth they get.
    public String generateToken(Account account) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(account.getId().toString())
                .claim("role", account.getRole().name())
                .claim("partnerType", account.getPartnerType() == null ? null : account.getPartnerType().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    // ------------ METHOD 2: Parse and validate a JWT, extracting claims ------------
    // Parses the token's claims, verifying the signature and expiration in the
    // process - throws JwtException (or a subclass) if the token was tampered
    // with, expired, or malformed.
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ------------ METHOD 3: Extract account id, role, and partnerType from a valid JWT ------------
    public Long extractAccountId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    // Null for non-partner accounts - callers must not assume this is always present.
    public String extractPartnerType(String token) {
        return parseClaims(token).get("partnerType", String.class);
    }

    // ------------ METHOD 4: Expose expirationMs for AuthService ------------
    // Lets AuthService report a token's lifetime back to the client in
    // AuthResponse without duplicating the expiration value in two places.
    public long getExpirationMs() {
        return expirationMs;
    }

    // ------------ METHOD 5: Validate a JWT's signature and expiration ------------
    // Used by the auth filter (step 11) to decide whether to reject a request
    // before trusting anything extracted from the token.
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}

/*
  JwtService.java
  Validates JWTs issued by auth-service, using the same shared secret every
  service signs/validates with (app.jwt.secret). The Gateway only ever needs
  a yes/no validity check (not the claims themselves) - it doesn't forward
  decoded identity to downstream services, since each one already re-parses
  the same Authorization header itself via its own JwtAuthFilter.
*/
package com.orbitra.api_gateway.security;

// ----------- IMPORTS -----------
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

    // @Value pulls app.jwt.secret from application.yml (which in turn
    // resolves from the JWT_SECRET env var / .env) - Spring injects this at
    // construction time.
    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ------------ METHOD 1: Validate a JWT's signature and expiration ------------
    public boolean isTokenValid(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}

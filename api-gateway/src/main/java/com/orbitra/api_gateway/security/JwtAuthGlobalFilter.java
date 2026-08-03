/*
  JwtAuthGlobalFilter.java
    1. Runs on every single request that hits the Gateway, before it gets forwarded anywhere.
    2. Checks if the request is going to a "public" route — a hardcoded list mirroring each service's own permitAll rules (e.g. GET /hotels, GET /flights/{id}, POST /auth/login).
    - If public → let it through immediately, no token needed.
    3. If it's not public, it looks for an Authorization: Bearer <token> header.
    - Missing or malformed → reject with 401 right there, request never leaves the Gateway.
    4. If a token is present, it checks the token is validly signed and not expired (jwtService.isTokenValid()).
    - Invalid/expired → reject with 401.
    - Valid → let it through.
    5. "Let it through" means it doesn't actually decide anything else — no role checks, no ownership checks. It just forwards the request onward. This filter is just an early bouncer at the door, not the final authority.
*/
package com.orbitra.api_gateway.security;

// ----------- IMPORTS -----------
import com.orbitra.api_gateway.dto.ErrorResponse;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String HEADER_NAME = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    // One rule per permitAll entry across every service's SecurityConfig,
    // in the same order those services declare them - order matters, since
    // e.g. GET /hotels/mine (protected) must be checked before the more
    // general GET /hotels/* (public) pattern it would otherwise also match.
    // method == null means "matches any HTTP method" (mirrors auth-service's
    // /auth/register and /auth/login matchers, which don't restrict method).
    private record PublicRule(HttpMethod method, String pattern, boolean isPublic) {
    }

    private final List<PublicRule> rules = List.of(
            new PublicRule(null, "/auth/register", true),
            new PublicRule(null, "/auth/login", true),
            new PublicRule(HttpMethod.GET, "/hotels/mine", false),
            new PublicRule(HttpMethod.GET, "/flights/mine", false),
            new PublicRule(HttpMethod.GET, "/hotels", true),
            new PublicRule(HttpMethod.GET, "/room-types", true),
            new PublicRule(HttpMethod.GET, "/hotels/*", true),
            new PublicRule(HttpMethod.GET, "/hotels/*/rooms", true),
            new PublicRule(HttpMethod.GET, "/flights", true),
            new PublicRule(HttpMethod.GET, "/seat-classes", true),
            new PublicRule(HttpMethod.GET, "/flights/*", true),
            new PublicRule(HttpMethod.GET, "/flights/*/seats", true)
    );

    public JwtAuthGlobalFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    // ------------ METHOD 1: run second - after the rate limiter, ahead of routing/load-balancing ------------
    @Override
    public int getOrder() {
        // RateLimitGlobalFilter takes HIGHEST_PRECEDENCE itself (cheaper check,
        // and it covers public routes too, which this filter skips) - this one
        // runs right after it, still well before routing.
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    // ------------ METHOD 2: allow public routes through, reject bad tokens on the rest ------------
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (isPublicRoute(exchange)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return reject(exchange, "Missing or malformed Authorization header");
        }

        String token = header.substring(BEARER_PREFIX.length());
        if (!jwtService.isTokenValid(token)) {
            return reject(exchange, "Invalid or expired token");
        }

        return chain.filter(exchange);
    }

    // ------------ METHOD 3: first-match-wins lookup against the mirrored permitAll rules ------------
    private boolean isPublicRoute(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        for (PublicRule rule : rules) {
            boolean methodMatches = rule.method() == null || rule.method().equals(method);
            if (methodMatches && pathMatcher.match(rule.pattern(), path)) {
                return rule.isPublic();
            }
        }
        return false;
    }

    // ------------ METHOD 4: short-circuit with the project's standard ErrorResponse shape ------------
    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse body = new ErrorResponse(Instant.now(), HttpStatus.UNAUTHORIZED.value(), message);
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}

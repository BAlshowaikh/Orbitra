/*
  RateLimitGlobalFilter.java
  Runs on every request, ahead of the JWT filter (cheaper check, and it also
  needs to cover public routes, which the JWT filter skips entirely). Keys
  each client by IP since public routes have no token to key off of instead
  - rejects with 429 once that IP's bucket (RateLimiterService) runs dry.
*/
package com.orbitra.api_gateway.ratelimit;

// ----------- IMPORTS -----------
import com.orbitra.api_gateway.dto.ErrorResponse;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.time.Instant;

@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    public RateLimitGlobalFilter(RateLimiterService rateLimiterService, ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    // ------------ METHOD 1: run before the JWT filter - cheaper, and covers public routes too ------------
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    // ------------ METHOD 2: consume a token from the caller's IP bucket, reject with 429 if empty ------------
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = resolveClientIp(exchange);

        if (!rateLimiterService.tryConsume(clientIp)) {
            return reject(exchange);
        }

        return chain.filter(exchange);
    }

    // ------------ METHOD 3: the caller's IP, as seen by this server - no proxy-header trust ------------
    private String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    // ------------ METHOD 4: short-circuit with the project's standard ErrorResponse shape ------------
    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse body = new ErrorResponse(
                Instant.now(), HttpStatus.TOO_MANY_REQUESTS.value(), "Rate limit exceeded - try again later");
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}

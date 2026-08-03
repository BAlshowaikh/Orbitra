/*
  RateLimiterService.java
  Token-bucket rate limiting (Bucket4j), in-memory - one bucket per client
  IP, held in a plain ConcurrentHashMap for this Gateway's own JVM heap.
  Only works correctly with a single Gateway instance; a horizontally-scaled
  Gateway would need a shared store (e.g. Redis) instead, so every instance
  sees the same counts - deferred until that's actually needed.
*/
package com.orbitra.api_gateway.ratelimit;

// ----------- IMPORTS -----------
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RateLimiterService {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int capacity;
    private final int refillSeconds;

    public RateLimiterService(
            @Value("${app.rate-limit.capacity}") int capacity,
            @Value("${app.rate-limit.refill-seconds}") int refillSeconds) {
        this.capacity = capacity;
        this.refillSeconds = refillSeconds;
    }

    // ------------ METHOD 1: consume one token from the caller's bucket, creating it on first use ------------
    public boolean tryConsume(String clientIp) {
        Bucket bucket = buckets.computeIfAbsent(clientIp, ip -> newBucket());
        return bucket.tryConsume(1);
    }

    // ------------ METHOD 2: a fresh bucket, starts full, refills at the configured rate ------------
    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(capacity, Duration.ofSeconds(refillSeconds)));
        return Bucket.builder().addLimit(limit).build();
    }
}

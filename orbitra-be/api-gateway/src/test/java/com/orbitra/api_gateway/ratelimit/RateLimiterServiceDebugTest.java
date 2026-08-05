package com.orbitra.api_gateway.ratelimit;

import org.junit.jupiter.api.Test;

public class RateLimiterServiceDebugTest {

    @Test
    void debugConsume() {
        RateLimiterService service = new RateLimiterService(20, 60);
        int trueCount = 0;
        for (int i = 1; i <= 25; i++) {
            boolean allowed = service.tryConsume("1.2.3.4");
            System.out.println("request " + i + " -> " + allowed);
            if (allowed) trueCount++;
        }
        System.out.println("total allowed: " + trueCount);
    }
}

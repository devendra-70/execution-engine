package com.epam.execution_engine_service.gateway.ratelimit;

/**
 * DIP/ISP: Abstraction for rate-limiting.
 * The HTTP filter depends on this interface, not on Redis directly.
 */
public interface RateLimitService {

    /**
     * Attempt to consume one request slot for the given key.
     *
     * @param key a unique per-user or per-IP identifier
     * @return {@code true} if the request is allowed, {@code false} if the rate limit is exceeded
     */
    boolean tryConsume(String key);
}


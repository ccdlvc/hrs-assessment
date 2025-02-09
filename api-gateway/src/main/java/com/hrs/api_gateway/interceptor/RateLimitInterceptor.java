package com.hrs.api_gateway.interceptor;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Provider
@Priority(Priorities.AUTHENTICATION - 100) // Run before authentication/authorization (or adjust priority as needed)
public class RateLimitInterceptor implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(RateLimitInterceptor.class);

    private final Map<String, TokenBucket> tokenBuckets = new ConcurrentHashMap<>();

    @ConfigProperty(name = "quarkus.rate-limit.capacity", defaultValue = "100") // Configure in application.properties
    int capacity;

    @ConfigProperty(name = "quarkus.rate-limit.refill-rate", defaultValue = "10") // Configure refill rate
    int refillRate;

    @ConfigProperty(name = "quarkus.rate-limit.refill-interval", defaultValue = "1") // Configure refill interval (seconds)
    int refillIntervalSeconds;


    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        String method = requestContext.getMethod();

        // Apply rate limiting only to POST /api/v1/bookings endpoint
        if ("POST".equalsIgnoreCase(method) && "/api/v1/bookings".equals(path)) {
            String clientId = getClientId(requestContext); // Identify client (e.g., IP address, API key - for simplicity, using path+method for now)
            String rateLimitKey = method + ":" + path + ":" + clientId; // Unique key per endpoint and client

            TokenBucket tokenBucket = tokenBuckets.computeIfAbsent(rateLimitKey, k -> new TokenBucket(capacity, refillRate, Duration.ofSeconds(refillIntervalSeconds)));

            if (!tokenBucket.allowRequest()) {
                LOG.warnf("Rate limit exceeded for client: %s, endpoint: %s", clientId, path);
                requestContext.abortWith(Response.status(Response.Status.TOO_MANY_REQUESTS)
                        .entity("Rate limit exceeded. Please try again later.")
                        .build());
            } else {
                LOG.debugf("Request allowed, tokens remaining for client: %s, endpoint: %s, tokens: %s", clientId, path, tokenBucket.getTokens());
            }
        } else {
            LOG.debugf("RateLimitInterceptor bypassed for %s request to %s", method, path);
        }
    }

    private String getClientId(ContainerRequestContext requestContext) {
        // In a real application, you would identify the client based on:
        // - API Key (from header or query parameter)
        // - User ID (from authentication context)
        // - IP Address (requestContext.getSecurityContext().getUserPrincipal() - for authenticated users)
        // - Or a combination of these.
        // For simplicity in this example, we are just using a constant clientId (or path+method)
        return "default-client"; // Or requestContext.getRemoteAddress().getHostAddress(); for IP-based limiting (be mindful of shared IPs)
    }


    // Inner TokenBucket class (Simple In-Memory Token Bucket Implementation), consider to use Redis for distributed bucket across multi api-gateway instances
    private static class TokenBucket {
        private final int capacity;
        private final int refillRate;
        private final Duration refillInterval;
        private AtomicInteger tokens;
        private Instant lastRefillTime;

        public TokenBucket(int capacity, int refillRate, Duration refillInterval) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.refillInterval = refillInterval;
            this.tokens = new AtomicInteger(capacity); // Start with full bucket
            this.lastRefillTime = Instant.now();
        }

        public boolean allowRequest() {
            refill(); // Refill tokens before attempting to consume

            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true; // Request allowed
            } else {
                return false; // Rate limit exceeded
            }
        }

        public int getTokens() {
            refill(); // Ensure tokens are refilled before getting count
            return tokens.get();
        }

        private void refill() {
            Instant now = Instant.now();
            long durationSinceLastRefill = Duration.between(lastRefillTime, now).toSeconds();
            if (durationSinceLastRefill >= refillInterval.getSeconds()) {
                int tokensToAdd = (int) (durationSinceLastRefill / refillInterval.getSeconds()) * refillRate;
                tokens.getAndAccumulate(tokensToAdd, Math::min); // Add tokens, but not exceeding capacity
                lastRefillTime = now;
                if (tokens.get() > capacity) { // Ensure tokens never exceed capacity
                    tokens.set(capacity);
                }
            }
        }
    }
}
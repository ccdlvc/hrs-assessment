package com.hrs.api_gateway.interceptor;

import io.quarkus.redis.client.RedisClient;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.io.IOException;

@Provider
@Priority(Priorities.USER + 100)
public class IdempotencyInterceptor implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(IdempotencyInterceptor.class);

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Inject
    RedisClient redisClient;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String method = requestContext.getMethod();

        // Apply idempotency check only for POST and PUT methods
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            String idempotencyKey = requestContext.getHeaderString(IDEMPOTENCY_KEY_HEADER);
            String path = requestContext.getUriInfo().getPath();

            if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
                LOG.warnf("Missing %s header for %s request to %s", IDEMPOTENCY_KEY_HEADER, method, path);
                requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST.getStatusCode()).entity("Missing Idempotency-Key header for " + method + " request").build());
                return;
            }

            // Check if the idempotency key exists in Redis
            io.vertx.redis.client.Response redisResponse = redisClient.get(idempotencyKey);
            if (redisResponse != null) {
                String cachedResponse = redisResponse.toString();
                // Key exists, return the stored response
                LOG.infof("Returning cached response for idempotency key: %s, method: %s, path: %s", idempotencyKey, method, path);
                JsonObject jsonResponse = new JsonObject(cachedResponse);
                int statusCode = jsonResponse.getInteger("status");
                String body = jsonResponse.getString("body");

                requestContext.abortWith(Response.status(statusCode).entity(body).build());
                return;
            }

            // Proceed with processing the request and store the response
            requestContext.setProperty("idempotencyKey", idempotencyKey);
            requestContext.setProperty("requestMethod", method);
            requestContext.setProperty("requestPath", path);
            requestContext.setProperty("requestBody", requestContext.getEntityStream().toString());
        } else {
            // For methods other than POST and PUT, just proceed without idempotency check
            LOG.debugf("IdempotencyInterceptor bypassed for %s request to %s", method, requestContext.getUriInfo().getPath());
        }
    }
}
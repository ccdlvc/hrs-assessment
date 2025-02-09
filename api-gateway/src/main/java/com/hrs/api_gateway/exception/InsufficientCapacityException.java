package com.hrs.api_gateway.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class InsufficientCapacityException extends WebApplicationException {

    public InsufficientCapacityException(String message) {
        super(message, Response.Status.CONFLICT); // 409 Conflict status for capacity issue
    }
}
package com.hrs.api_gateway.exception;

import jakarta.ws.rs.NotFoundException;

public class BookingNotFoundException extends NotFoundException {

    public BookingNotFoundException(String message) {
        super(message);
    }
}
package com.hrs.api_gateway.exception;

import jakarta.ws.rs.NotFoundException;

public class HotelNotFoundException extends NotFoundException {

    public HotelNotFoundException(String message) {
        super(message);
    }
}
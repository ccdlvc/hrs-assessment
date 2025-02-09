package com.hrs.api_gateway.entity;

public enum BookingStatus {
    CANCELLED,
    PENDING, // Optional: For bookings that are not yet confirmed
    // Add other statuses as needed (e.g., CONFIRMED, COMPLETED, CHECKED_IN)
}
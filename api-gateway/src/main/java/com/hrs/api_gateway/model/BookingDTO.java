package com.hrs.api_gateway.model;

import com.hrs.api_gateway.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingDTO {

    private Long id;
    private Long hotelId;
    private Long userId;
    private LocalDateTime checkinDate;
    private LocalDateTime checkoutDate;
    private Integer numberOfGuests;
    private Long totalPrice;
    private BookingStatus bookingStatus; // New field
}
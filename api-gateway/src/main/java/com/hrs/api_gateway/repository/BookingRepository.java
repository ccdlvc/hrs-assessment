package com.hrs.api_gateway.repository;

import com.hrs.api_gateway.entity.Booking;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class BookingRepository implements PanacheRepository<Booking> {

    public List<Booking> findByUserId(Long userId) {
        return list("user.id", userId);
    }

    public List<Booking> findByHotelId(Long hotelId) {
        return list("hotel.id", hotelId);
    }
}
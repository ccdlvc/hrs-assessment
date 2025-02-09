package com.hrs.api_gateway.repository;

import com.hrs.api_gateway.entity.Hotel;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class HotelRepository implements PanacheRepository<Hotel> {

    public Hotel findByIdForUpdate(Long id) {
        return find("id", id)
                .withLock(LockModeType.PESSIMISTIC_WRITE) // Use Pessimistic Write Lock (SELECT FOR UPDATE)
                .firstResult();
    }
}
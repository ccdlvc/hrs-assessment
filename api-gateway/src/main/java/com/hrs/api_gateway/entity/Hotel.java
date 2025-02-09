package com.hrs.api_gateway.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "hotels")
public class Hotel extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "name", nullable = false)
    public String name;

    @Column(name = "city", nullable = false)
    public String city;

    @Column(name = "address", nullable = false)
    public String address;

    @Column(name = "capacity", nullable = false)
    public Integer capacity;

    public Hotel() {
    }

    public Hotel(String name, String city, String address, Integer capacity) { // Updated constructor
        this.name = name;
        this.city = city;
        this.address = address;
        this.capacity = capacity;
    }
}
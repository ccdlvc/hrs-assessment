package com.hrs.api_gateway.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Getter
@Setter
@Table(
        name = "bookings",
        indexes = {
                @Index(columnList = "hotel_id", name = "idx_hotel_id"),
                @Index(columnList = "user_id", name = "idx_user_id"),
        }
)
public class Booking extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    @JoinColumn(name = "hotel_id", nullable = false)
    @JsonProperty("hotel_id")
    public Hotel hotel;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonProperty("user_id")
    public User user;

    @Column(name = "check_in_date", nullable = false)
    @JsonProperty("check_in_date")
    public LocalDateTime checkinDate;

    @Column(name = "check_out_date", nullable = false)
    @JsonProperty("check_out_date")
    public LocalDateTime checkoutDate;

    @Column(name = "number_of_guests", nullable = false)
    @JsonProperty("number_of_guests")
    public Integer numberOfGuests;

    @Column(name = "total_price", nullable = false)
    @JsonProperty("total_price")
    public Long totalPrice;

    @Enumerated(EnumType.STRING) 
    @Column(name = "booking_status", nullable = false)
    @JsonProperty("booking_status")
    public BookingStatus bookingStatus;

    public Booking() {
    }
}
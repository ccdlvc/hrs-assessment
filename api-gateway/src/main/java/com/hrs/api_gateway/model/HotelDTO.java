package com.hrs.api_gateway.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotelDTO {
    private Long id;
    private String name;
    private String city;
    private String address;
    private Integer capacity;
}
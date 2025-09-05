package org.crumb.be.dto;

import lombok.Data;

@Data
public class KakaoBakeryDto {
    private Long id; // id
    private String name; // place_name
    private String address; // address_name
    private String road_address; // road_address_name
    private String phone; // phone
    private double latitude;   // y
    private double longitude;  // x
    private Long distance; // distance (m)
    private String place_url; // place_url
}

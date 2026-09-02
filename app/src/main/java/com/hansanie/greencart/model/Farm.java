package com.hansanie.greencart.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Farm {
    private Long id;
    private String name;
    private String address;
    private Double latitude;
    private Double longitude;
    private String contactNumber;
    private String imageUrl;
    private String certificationInfo;
}
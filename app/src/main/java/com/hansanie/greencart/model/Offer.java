package com.hansanie.greencart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Offer {

    private Long id; // BIGINT(19)
    private String title; // VARCHAR(255)
    private String description; // VARCHAR(255)
    private Double discountPercentage; // DOUBLE
    private String promoCode; // VARCHAR(255)
    private String imageUrl; // VARCHAR(255)

    // දීමනාව ක්‍රියාත්මක වන කාලය
    // Store as ISO 8601 String for Firestore compatibility
    private String startDate;
    private String expiryDate;

    private String status; // Active, Draft, Archived

    // අලුතින් එකතු කළ යුත්තේ:
    // 'GLOBAL', 'FIRST_ORDER' වැනි ENUM අගයන් සඳහා
    private String promoType;

    // වට්ටම ලබා ගැනීමට තිබිය යුතු අවම ඇණවුම් අගය
    private Double minOrderValue;

    // ලබා ගත හැකි උපරිම වට්ටම් මුදල
    private Double maxDiscountAmount;

    private String createdAt; // TIMESTAMP, stored as ISO 8601 String
}
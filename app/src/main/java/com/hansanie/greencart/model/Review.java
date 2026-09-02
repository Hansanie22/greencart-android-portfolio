package com.hansanie.greencart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {
    private String id;
    private Long productId;
    private Long farmId;
    private String reviewerName;
    private String reviewerInitial;
    private String reviewDateLabel;
    private double rating;
    private String comment;
    private boolean verifiedPurchase;
    private int helpfulCount;
}


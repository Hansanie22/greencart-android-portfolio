package com.hansanie.greencart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistItem {
    private long productId;
    private String name;
    private String category;
    private double price;
    private String imageUrl;
    private String variantName;
}
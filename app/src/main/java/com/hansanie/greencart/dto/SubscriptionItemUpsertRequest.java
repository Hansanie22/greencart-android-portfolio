package com.hansanie.greencart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionItemUpsertRequest {
    private Long productId;
    private Long variantId;
    private String name;
    private String variantName;
    private Integer quantity;
    private Double unitPrice;
    private String imageUrl;
}
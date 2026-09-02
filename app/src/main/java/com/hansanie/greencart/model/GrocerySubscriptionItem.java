package com.hansanie.greencart.model;

import androidx.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrocerySubscriptionItem implements Serializable {
    @Nullable
    private Long productId;
    @Nullable
    private Long variantId;
    @Nullable
    private String name;
    @Nullable
    private String variantName;
    @Nullable
    private Integer quantity;
    @Nullable
    private Double unitPrice;
    @Nullable
    private String imageUrl;
}


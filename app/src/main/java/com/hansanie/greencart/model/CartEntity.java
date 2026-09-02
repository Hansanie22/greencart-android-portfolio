package com.hansanie.greencart.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cart")
public class CartEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String userId;
    public long productId;
    public Long variantId;          // FIX: stores the variant's server-side FK
    public String productName;
    public String variantName;
    public double price;
    public int quantity;
    public String imageUrl;
    public boolean isSubscriptionItem;
    public String subscriptionFrequency;

    public CartEntity() {}

    /** 7-param: no variantId, no subscription fields */
    public CartEntity(String userId, long productId, String productName,
                      String variantName, double price, int quantity, String imageUrl) {
        this(userId, productId, null, productName, variantName, price, quantity, imageUrl, false, null);
    }

    /** 9-param: no variantId – kept for backward compatibility */
    public CartEntity(String userId, long productId, String productName,
                      String variantName, double price, int quantity, String imageUrl,
                      boolean isSubscriptionItem, String subscriptionFrequency) {
        this(userId, productId, null, productName, variantName, price, quantity, imageUrl,
                isSubscriptionItem, subscriptionFrequency);
    }

    /** 10-param: full constructor including variantId */
    public CartEntity(String userId, long productId, Long variantId,
                      String productName, String variantName, double price, int quantity,
                      String imageUrl, boolean isSubscriptionItem, String subscriptionFrequency) {
        this.userId = userId;
        this.productId = productId;
        this.variantId = variantId;
        this.productName = productName;
        this.variantName = variantName;
        this.price = price;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
        this.isSubscriptionItem = isSubscriptionItem;
        this.subscriptionFrequency = subscriptionFrequency;
    }
}


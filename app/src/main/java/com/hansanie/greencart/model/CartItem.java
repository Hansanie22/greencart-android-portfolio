package com.hansanie.greencart.model;

public class CartItem {
    private int roomId;            // Room primary key (for Room-based updates)
    private String userId;
    private long productId;
    private Long variantId;        // FIX: nullable variant FK
    private String name;
    private String variantName;
    private double price;
    private int quantity;
    private String imageUrl;        // URL loaded by Glide
    private boolean subscriptionItem;
    private String subscriptionFrequency;

    public CartItem() {}

    public CartItem(int roomId, String userId, long productId, Long variantId,
                    String name, String variantName, double price, int quantity,
                    String imageUrl, boolean subscriptionItem, String subscriptionFrequency) {
        this.roomId = roomId;
        this.userId = userId;
        this.productId = productId;
        this.variantId = variantId;
        this.name = name;
        this.variantName = variantName;
        this.price = price;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
        this.subscriptionItem = subscriptionItem;
        this.subscriptionFrequency = subscriptionFrequency;
    }

    /** Build from CartEntity (Room). */
    public static CartItem from(CartEntity e) {
        return new CartItem(e.id, e.userId, e.productId, e.variantId,
                e.productName, e.variantName, e.price, e.quantity, e.imageUrl,
                e.isSubscriptionItem, e.subscriptionFrequency);
    }

    /** Convert back to CartEntity for Room operations. */
    public CartEntity toEntity() {
        CartEntity e = new CartEntity();
        e.id = roomId;
        e.userId = userId;
        e.productId = productId;
        e.variantId = variantId;
        e.productName = name;
        e.variantName = variantName;
        e.price = price;
        e.quantity = quantity;
        e.imageUrl = imageUrl;
        e.isSubscriptionItem = subscriptionItem;
        e.subscriptionFrequency = subscriptionFrequency;
        return e;
    }

    // ── Getters / Setters ──────────────────────────────────────────────────────
    public int getRoomId()        { return roomId; }
    public String getUserId()     { return userId; }
    public long getProductId()    { return productId; }
    public Long getVariantId()    { return variantId; }
    public void setVariantId(Long variantId) { this.variantId = variantId; }
    public String getName()       { return name; }
    public String getVariantName(){ return variantName; }
    public double getPrice()      { return price; }
    public int getQuantity()      { return quantity; }
    public void setQuantity(int q){ this.quantity = q; }
    public String getImageUrl()   { return imageUrl; }
    public boolean isSubscriptionItem() { return subscriptionItem; }
    public String getSubscriptionFrequency() { return subscriptionFrequency; }

    // Legacy compatibility (used by old CartAdapter)
    public String getUnit()       { return variantName; }
}
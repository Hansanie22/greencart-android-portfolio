package com.hansanie.greencart.model;

import com.google.gson.annotations.SerializedName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariant {
    private Long id;
    private String variantName;
    private Double price;

    @SerializedName(value = "minStockCount", alternate = {"stock", "stockCount", "availableStock", "availableQty", "quantity", "qty", "inventoryCount", "inventoryQuantity"})
    private Integer minStockCount;

    @SerializedName(value = "stockStatus", alternate = {"availabilityStatus", "availability", "inventoryStatus", "status"})
    private String stockStatus;

    @SerializedName(value = "originalPrice", alternate = {"oldPrice", "mrp", "listPrice", "compareAtPrice"})
    private Double originalPrice;

    @SerializedName(value = "dealPrice", alternate = {"discountedPrice", "salePrice"})
    private Double dealPrice;

    @SerializedName(value = "discountPercent", alternate = {"discountPercentage"})
    private Integer discountPercent;

    @SerializedName(value = "dealActive", alternate = {"hasDeal", "onDeal"})
    private Boolean dealActive;

    @SerializedName(value = "dealTag", alternate = {"dealLabel", "dealText"})
    private String dealTag;

    public Integer getStock() {
        return minStockCount;
    }

    public void setStock(Integer stock) {
        this.minStockCount = stock;
    }

    public Integer getResolvedStockCount() {
        if (minStockCount == null) {
            return null;
        }
        return Math.max(0, minStockCount);
    }

    public boolean isInStock() {
        Integer stock = getResolvedStockCount();
        if (stock != null) {
            return stock > 0;
        }

        if (stockStatus == null) {
            return false;
        }

        String normalized = stockStatus.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return false;
        }
        return normalized.contains("in_stock")
                || normalized.contains("instock")
                || normalized.contains("available")
                || normalized.contains("limited")
                || normalized.contains("low");
    }

    public String getResolvedAvailabilityLabel() {
        Integer stock = getResolvedStockCount();
        if (stock != null) {
            if (stock <= 0) {
                return "Out of Stock";
            }
            if (stock < 10) {
                return "Only " + stock + " left!";
            }
            return stock + " available";
        }

        if (stockStatus == null || stockStatus.trim().isEmpty()) {
            return null;
        }

        String normalized = stockStatus.trim().replace('_', ' ').toLowerCase();
        if (normalized.equals("in stock")) {
            return "In Stock";
        }
        if (normalized.equals("out of stock")) {
            return "Out of Stock";
        }

        String[] words = normalized.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    public double getEffectivePrice() {
        if (dealPrice != null && dealPrice > 0) {
            return dealPrice;
        }
        return price != null ? price : 0.0;
    }

    public Double getComparableOriginalPrice() {
        if (originalPrice != null && originalPrice > 0) {
            return originalPrice;
        }
        if (price != null && dealPrice != null && price > dealPrice) {
            return price;
        }
        return null;
    }

    public int getResolvedDiscountPercent() {
        if (discountPercent != null && discountPercent > 0) {
            return discountPercent;
        }
        Double original = getComparableOriginalPrice();
        double effective = getEffectivePrice();
        if (original == null || original <= 0 || original <= effective) {
            return 0;
        }
        return (int) Math.round(((original - effective) / original) * 100);
    }

    public boolean hasDeal() {
        if (Boolean.TRUE.equals(dealActive)) {
            return true;
        }
        Double original = getComparableOriginalPrice();
        return original != null && original > getEffectivePrice();
    }
}
package com.hansanie.greencart.model;

import com.google.gson.annotations.SerializedName;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.PropertyName;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    private Long id; // BIGINT(19)

    // Database එකේ category_id සහ farm_id තියෙන නිසා මේවා අනිවාර්යයි
    private Long categoryId;
    @SerializedName("farmId")
    private Long farmId;

    private String name; // VARCHAR(255)
    private String sku; // VARCHAR(255)
    private String description; // TEXT
    private String unit; // VARCHAR(255)
    private String status; // VARCHAR(255)

    // Android app එකේ පෙන්වන්න පාවිච්චි කරන images
    private List<String> images;
    private String imageUrl;

    // Subscription එකට සුදුසුද සහ Delete කරලාද කියලා බැලීමට
    private boolean isDeleted;
    private boolean isSubscriptionEligible;

    // Firestore Mapping
    @PropertyName("category")
    private String categoryName;

    private Double rating;
    private Integer reviewCount;

    @SerializedName(value = "hasActiveDeal", alternate = {"hasDeal", "dealActive", "onDeal"})
    private Boolean dealLabel;

    @SerializedName(value = "isNewest", alternate = {"newest", "isNew", "newArrival"})
    private Boolean newestLabel;

    private Map<String, Object> nutrition;
    private List<ProductVariant> variants;

    @Exclude
    @SerializedName("category")
    private Category categoryObject;

    @Exclude
    @SerializedName(value = "farmObject", alternate = {"farm"})
    private Farm farmObject;

    /**
     * පළමු පින්තූරය ලබා ගැනීමට.
     */
    public String getPrimaryImage() {
        if (images != null && !images.isEmpty()) {
            return images.get(0);
        }
        return imageUrl;
    }

    // Compatibility helpers used by existing fragments/adapters.
    public String getCategory() {
        if (categoryName != null) {
            return categoryName;
        }
        if (categoryObject != null && categoryObject.getName() != null) {
            return categoryObject.getName();
        }
        return null;
    }

    public void setCategory(String category) {
        this.categoryName = category;
    }

    public Double getMinPrice() {
        if (variants == null || variants.isEmpty()) return null;
        Double min = null;
        for (ProductVariant v : variants) {
            if (v != null && v.getPrice() != null) {
                if (min == null || v.getPrice() < min) {
                    min = v.getPrice();
                }
            }
        }
        return min;
    }

    public Double getMaxPrice() {
        if (variants == null || variants.isEmpty()) return null;
        Double max = null;
        for (ProductVariant v : variants) {
            if (v != null && v.getPrice() != null) {
                if (max == null || v.getPrice() > max) {
                    max = v.getPrice();
                }
            }
        }
        return max;
    }
}
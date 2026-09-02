package com.hansanie.greencart.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import com.google.firebase.firestore.Exclude;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(tableName = "grocery_subscriptions")
public class GrocerySubscription {

    @PrimaryKey(autoGenerate = true)
    private Long id; // BIGINT(19) AUTO_INCREMENT

    @ColumnInfo(name = "user_id")
    private Long userId; // BIGINT(19)

    @ColumnInfo(name = "firebase_uid")
    private String firebaseUid;

    @ColumnInfo(name = "name")
    private String name; // VARCHAR(255)

    @ColumnInfo(name = "frequency")
    private String frequency; // ENUM

    @ColumnInfo(name = "start_date")
    private String startDate; // DATE

    @ColumnInfo(name = "next_delivery_date")
    private String nextDeliveryDate; // DATE

    @ColumnInfo(name = "delivery_time_slot")
    private String deliveryTimeSlot; // VARCHAR(50)

    @ColumnInfo(name = "delivery_address_id")
    private String deliveryAddressId;

    @ColumnInfo(name = "billing_address_id")
    private String billingAddressId;

    @ColumnInfo(name = "status")
    private String status; // VARCHAR(255)

    @ColumnInfo(name = "created_at")
    private String createdAt; // TIMESTAMP

    @ColumnInfo(name = "address_id")
    private String addressId; // VARCHAR(255)

    @ColumnInfo(name = "total_amount")
    private Double totalAmount; // DECIMAL

    @ColumnInfo(name = "item_count")
    private Integer itemCount; // INT

    @ColumnInfo(name = "discount_amount")
    private Double discountAmount;

    @ColumnInfo(name = "updated_at")
    private String updatedAt;

    @ColumnInfo(name = "skip_next")
    private boolean skipNext;

    @Exclude
    @Ignore
    private String firestoreId;

    @Exclude
    @Ignore
    private List<Long> productIds;

    @Exclude
    @Ignore
    private String deliveryDay;

    @Exclude
    @Ignore
    private Integer intervalDays;

    @Exclude
    @Ignore
    private Integer bonusPoints;

    // Promotion / redemption fields stored in Firestore for subscriptions.
    // Marked @Exclude/@Ignore so Room schema is unchanged and these are only
    // used for Firestore -> model mapping.
    @Exclude
    @Ignore
    private String appliedPromoCode;

    @Exclude
    @Ignore
    private Double promoCodeDiscount;

    @Exclude
    @Ignore
    private Integer pointsRedeemed;

    @Exclude
    @Ignore
    private Double redeemValue;

    @Exclude
    @Ignore
    private String firestoreSyncStatus;

    @Exclude
    @Ignore
    private List<GrocerySubscriptionItem> items;
}
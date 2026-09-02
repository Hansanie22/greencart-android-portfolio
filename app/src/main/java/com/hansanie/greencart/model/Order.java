package com.hansanie.greencart.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(tableName = "orders")
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    @PrimaryKey(autoGenerate = true)
    private Long id;

    @ColumnInfo(name = "user_id")
    private Long userId;

    @ColumnInfo(name = "address_id")
    private String addressId;

    @ColumnInfo(name = "total_amount")
    private Double totalAmount;

    @ColumnInfo(name = "order_status")
    private String orderStatus;

    @ColumnInfo(name = "payment_status")
    private String paymentStatus;

    @ColumnInfo(name = "order_date")
    private String orderDate;

    @ColumnInfo(name = "created_at")
    private String createdAt;

    @ColumnInfo(name = "discount_amount")
    private Double discountAmount;

    @ColumnInfo(name = "subtotal")
    private Double subtotal;

    @ColumnInfo(name = "shipping")
    private Double shipping;

    @ColumnInfo(name = "green_points_redeemed")
    private Integer greenPointsRedeemed;

    @ColumnInfo(name = "green_points_redeem_value")
    private Double greenPointsRedeemValue;

    @ColumnInfo(name = "notes")
    private String notes;

    @ColumnInfo(name = "promo_code")
    private String promoCode;

    @ColumnInfo(name = "status")
    private String status;

    @ColumnInfo(name = "order_code")
    private String orderCode;

    @ColumnInfo(name = "subscription_id")
    private Long subscriptionId;

    @ColumnInfo(name = "is_subscription")
    private boolean subscriptionOrder;

    @ColumnInfo(name = "firebase_uid")
    private String firebaseUid;

    @ColumnInfo(name = "delivery_address")
    private String deliveryAddress;

    @ColumnInfo(name = "delivery_latitude")
    private Double deliveryLatitude;

    @ColumnInfo(name = "delivery_longitude")
    private Double deliveryLongitude;

    @ColumnInfo(name = "hub_name")
    private String hubName;

    @ColumnInfo(name = "hub_latitude")
    private Double hubLatitude;

    @ColumnInfo(name = "hub_longitude")
    private Double hubLongitude;

    @ColumnInfo(name = "farm_name")
    private String farmName;

    @ColumnInfo(name = "farm_address")
    private String farmAddress;

    @ColumnInfo(name = "farm_latitude")
    private Double farmLatitude;

    @ColumnInfo(name = "farm_longitude")
    private Double farmLongitude;

    @ColumnInfo(name = "rider_name")
    private String riderName;

    @ColumnInfo(name = "rider_phone")
    private String riderPhone;

    @ColumnInfo(name = "support_phone")
    private String supportPhone;

    @ColumnInfo(name = "estimated_arrival")
    private String estimatedArrival;

    @ColumnInfo(name = "pending_at")
    private String pendingAt;

    @ColumnInfo(name = "confirmed_at")
    private String confirmedAt;

    @ColumnInfo(name = "out_for_delivery_at")
    private String outForDeliveryAt;

    @ColumnInfo(name = "delivered_at")
    private String deliveredAt;

    @ColumnInfo(name = "arrival_notified")
    private boolean arrivalNotified;

    @ColumnInfo(name = "green_points_earned")
    private Integer greenPointsEarned;

    @ColumnInfo(name = "offer_id")
    private Long offerId;

    @ColumnInfo(name = "offer_percentage")
    private Double offerPercentage;

    @ColumnInfo(name = "promo_offer_id")
    private Long promoOfferId;

    @ColumnInfo(name = "promo_discount_percent")
    private Double promoDiscountPercent;

    @ColumnInfo(name = "promo_discount")
    private Double promoDiscount;

    private Double subscriptionDiscount;

    @ColumnInfo(name = "status_updated_at")
    private String statusUpdatedAt;

    @Ignore
    private List<OrderItem> items;

    @Ignore
    private String firestoreDocumentId;

    public String getOrderId() {
        if (orderCode != null && !orderCode.trim().isEmpty()) {
            return orderCode;
        }
        return id != null ? "#ORD-" + id : "";
    }

    public String getDate() {
        return orderDate;
    }

}

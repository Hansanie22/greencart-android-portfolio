package com.hansanie.greencart.dto;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionSaveRequest {
    private Long id;
    private String firebaseUid;
    private String name;
    private String frequency;
    private String status;

    @SerializedName("startDate")
    private String startDate;

    @SerializedName("nextDeliveryDate")
    private String nextDeliveryDate;

    private String deliveryDay;
    private Integer intervalDays;
    private String deliveryTimeSlot;
    private String deliveryAddressId;
    private String billingAddressId;
    private Double totalAmount;
    private Double discountAmount;
    private Integer itemCount;
    private Integer bonusPoints;
    private String firestoreId;
    private Boolean skipNextDelivery;
    private List<SubscriptionItemUpsertRequest> items;
}
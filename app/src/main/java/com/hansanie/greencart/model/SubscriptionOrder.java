package com.hansanie.greencart.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.gson.annotations.SerializedName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(tableName = "subscription_orders")
public class SubscriptionOrder {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private Long id;

    @SerializedName("subscriptionId")
    @ColumnInfo(name = "subscription_id")
    private Long subscriptionId;

    @SerializedName("orderId")
    @ColumnInfo(name = "order_id")
    private Long orderId;

    @SerializedName("deliveryDate")
    @ColumnInfo(name = "delivery_date")
    private String deliveryDate;

    // These fields are used for network/Firestore payloads but are not stored in the local Room table
    @Ignore
    @SerializedName("firestoreId")
    private String firestoreId;

    @Ignore
    @SerializedName("firebaseUid")
    private String firebaseUid;
}

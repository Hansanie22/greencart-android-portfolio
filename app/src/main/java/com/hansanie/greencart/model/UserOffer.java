package com.hansanie.greencart.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(tableName = "user_offers")
public class UserOffer {

    @PrimaryKey(autoGenerate = true)
    private Long id; // BIGINT(19) AUTO_INCREMENT

    @ColumnInfo(name = "user_id")
    private Long userId; // BIGINT(19)

    @ColumnInfo(name = "offer_id")
    private Long offer_id; // BIGINT(19)

    @ColumnInfo(name = "claimed_at")
    private Long claimedAt; // TIMESTAMP in ms

    // Additional fields for offer logic
    private Boolean used; // true if offer is used
    private String firebaseUid;
    private String promoCode;
    private String status;
}
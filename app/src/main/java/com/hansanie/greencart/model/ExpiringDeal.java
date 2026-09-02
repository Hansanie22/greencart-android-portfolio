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
@Entity(tableName = "expiring_deals")
public class ExpiringDeal {
    @PrimaryKey
    private Long id; // BIGINT(19)

    @ColumnInfo(name = "stock_id")
    private Long stockId; // ProductStock එකට සම්බන්ධයි

    @ColumnInfo(name = "deal_price")
    private Double dealPrice; // DOUBLE

    @ColumnInfo(name = "start_time")
    private String startTime;

    @ColumnInfo(name = "end_time")
    private String endTime;

    @ColumnInfo(name = "is_active")
    private boolean isActive; // BIT(1)
}
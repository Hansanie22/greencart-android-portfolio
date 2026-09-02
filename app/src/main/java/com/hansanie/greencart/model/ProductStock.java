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
@Entity(tableName = "product_stock")
public class ProductStock {
    @PrimaryKey
    private Long id; // BIGINT(19)

    @ColumnInfo(name = "variant_id")
    private Long variantId; // BIGINT(19)

    @ColumnInfo(name = "product_id")
    private Long productId; // Foreign Key

    @ColumnInfo(name = "batch_number")
    private String batchNumber;

    private Integer quantity; // INT(10)

    @ColumnInfo(name = "expiry_date")
    private String expiryDate; // DATE

    @ColumnInfo(name = "received_date")
    private String receivedDate;

    private String status; // 'AVAILABLE', 'OUT_OF_STOCK'
}
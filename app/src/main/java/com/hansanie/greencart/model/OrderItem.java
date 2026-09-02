package com.hansanie.greencart.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity(tableName = "order_items")
public class OrderItem implements java.io.Serializable {

    @PrimaryKey(autoGenerate = true)
    private Long id; // BIGINT(19) AUTO_INCREMENT

    @ColumnInfo(name = "order_id")
    private Long orderId; // BIGINT(19)

    @ColumnInfo(name = "variant_id")
    private Long variantId; // BIGINT(19)

    @ColumnInfo(name = "quantity")
    private Integer quantity; // INT(10)

    @ColumnInfo(name = "price_at_purchase")
    private Double priceAtPurchase; // DECIMAL(10,2) - Mapped to Double for Room

    @ColumnInfo(name = "unit_price")
    private Double unitPrice; // DOUBLE

    @ColumnInfo(name = "product_id")
    private Long productId; // BIGINT(19)

    @Ignore
    // Not persisted in Room - transient helpers populated from Firestore 'items' map
    private String productName;

    @Ignore
    private String variantName;

    @Ignore
    private String imageUrl;
}
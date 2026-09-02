package com.hansanie.greencart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Maps to MySQL `payments` table.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    /** VARCHAR(255) PRIMARY KEY */
    private String id;

    /** BIGINT - FK -> orders.id */
    private Long orderId;

    private String firebaseUid;
    private Double amount;

    @Builder.Default
    private String currency = "LKR";

    /** CARD | CASH_ON_DELIVERY */
    private String paymentMethod;

    /** PENDING | SUCCESS | FAILED | REFUNDED */
    @Builder.Default
    private String status = "PENDING";

    private String payherePaymentId;
    private Double payhereAmount;
    private String md5sig;
}

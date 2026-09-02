package com.hansanie.greencart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Maps to MySQL `user_card_info` table.
 * Extra fields (expiryDate, firestoreDocId) are stored in Firebase only.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCard {
    /** BIGINT AUTO_INCREMENT PRIMARY KEY */
    private Long id;

    private String firebaseUid;
    private String cardHolderName;

    /** e.g. "**** **** **** 4111" */
    private String cardMasked;

    /** VISA / MASTERCARD / AMEX / OTHER */
    private String cardBrand;

    private boolean isDefault;

    // Firebase-only fields (ignored by Spring Boot @Transient mapping)
    /** MM/YY - stored only in Firebase payment_cards subcollection */
    private String expiryDate;

    /** Firestore document ID used for deletion */
    private String firestoreDocId;

    // Convenience getters kept for adapter backward-compat
    public String getMaskedNumber() {
        return cardMasked;
    }

    public String getCardType() {
        return cardBrand;
    }
}
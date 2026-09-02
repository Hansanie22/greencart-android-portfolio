package com.hansanie.greencart.model;

import java.util.Locale;

public enum AddressType {
    BILLING,
    SHIPPING,
    BOTH;

    public static AddressType fromValue(String value) {
        if (value == null) {
            return BOTH;
        }
        try {
            return AddressType.valueOf(value.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException ignored) {
            return BOTH;
        }
    }
}


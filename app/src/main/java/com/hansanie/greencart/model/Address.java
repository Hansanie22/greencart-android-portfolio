package com.hansanie.greencart.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.PropertyName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    private String id;
    @Exclude
    private String firestoreDocId;
    private String firebaseUid;

    private String title;

    private String addressLine1; //
    private String addressLine2; // Optional

    private String city;
    private String postalCode;

    private String addressType;

    private boolean isDefault;

    private LocalDateTime createdAt; //
    private LocalDateTime updatedAt; //

    @Exclude
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Exclude
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Exclude
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Exclude
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PropertyName("createdAt")
    public Timestamp getCreatedAtTimestamp() {
        return toTimestamp(createdAt);
    }

    @PropertyName("createdAt")
    public void setCreatedAtTimestamp(Object value) {
        this.createdAt = fromRawValue(value);
    }

    @PropertyName("updatedAt")
    public Timestamp getUpdatedAtTimestamp() {
        return toTimestamp(updatedAt);
    }

    @PropertyName("updatedAt")
    public void setUpdatedAtTimestamp(Object value) {
        this.updatedAt = fromRawValue(value);
    }

    private static Timestamp toTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        Instant instant = dateTime.atZone(ZoneId.systemDefault()).toInstant();
        return new Timestamp(instant.getEpochSecond(), instant.getNano());
    }

    private static LocalDateTime fromRawValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp) {
            Timestamp ts = (Timestamp) value;
            return LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(ts.getSeconds(), ts.getNanoseconds()),
                    ZoneId.systemDefault()
            );
        }
        if (value instanceof String) {
            try {
                return LocalDateTime.parse((String) value);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Object seconds = map.get("seconds");
            Object nanos = map.get("nanoseconds");
            if (seconds instanceof Number) {
                long sec = ((Number) seconds).longValue();
                int ns = nanos instanceof Number ? ((Number) nanos).intValue() : 0;
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(sec, ns), ZoneId.systemDefault());
            }
        }
        return null;
    }

    public String getFullAddress() {
        StringBuilder fullAddress = new StringBuilder();
        if (addressLine1 != null && !addressLine1.isEmpty()) {
            fullAddress.append(addressLine1);
        }
        if (addressLine2 != null && !addressLine2.isEmpty()) {
            if (fullAddress.length() > 0) fullAddress.append(", ");
            fullAddress.append(addressLine2);
        }
        if (city != null && !city.isEmpty()) {
            if (fullAddress.length() > 0) fullAddress.append(", ");
            fullAddress.append(city);
        }
        if (postalCode != null && !postalCode.isEmpty()) {
            if (fullAddress.length() > 0) fullAddress.append(", ");
            fullAddress.append(postalCode);
        }
        return fullAddress.toString();
    }
}
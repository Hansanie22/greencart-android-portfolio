package com.hansanie.greencart.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode
@Entity(tableName = "notifications")
public class NotificationItem {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String title;
    private String body;
    private long timestamp;
    private boolean isRead;
    private Long offerId; // nullable, for offer notifications
    // Optional destination for click actions (e.g. "subscriptions", "orders", "support_chat")
    private String destination;
    // Optional JSON payload to reconstruct click action (e.g. firestore id, mysql id, etc)
    private String payload;

    public NotificationItem(String title, String body) {
        this.title = title;
        this.body = body;
        this.timestamp = System.currentTimeMillis();
        this.isRead = false;
    }

    public NotificationItem(String title, String body, Long offerId) {
        this.title = title;
        this.body = body;
        this.timestamp = System.currentTimeMillis();
        this.isRead = false;
        this.offerId = offerId;
    }

    public NotificationItem(String title, String body, Long offerId, String destination, String payload) {
        this.title = title;
        this.body = body;
        this.timestamp = System.currentTimeMillis();
        this.isRead = false;
        this.offerId = offerId;
        this.destination = destination;
        this.payload = payload;
    }
}

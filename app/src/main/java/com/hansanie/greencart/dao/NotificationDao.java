package com.hansanie.greencart.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.hansanie.greencart.model.NotificationItem;

import java.util.List;

@Dao
public interface NotificationDao {

    @Insert
    void insert(NotificationItem item);

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    List<NotificationItem> getAll();

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    int getUnreadCount();

    @Query("UPDATE notifications SET isRead = 1")
    void markAllRead();

    // ── NEW: Individual item read mark ────────────────────────────────────
    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    void markReadById(long id);

    @Query("DELETE FROM notifications")
    void deleteAll();

    @Query("DELETE FROM notifications WHERE destination = :dest AND payload LIKE '%' || :payloadPart || '%'")
    void deleteByDestinationAndPayloadContains(String dest, String payloadPart);

    @Query("SELECT COUNT(*) FROM notifications WHERE offerId = :offerId")
    int getOfferNotificationCount(Long offerId);

    // ── NEW: Duplicate offer check by title (offerId null වූ විට) ─────────
    @Query("SELECT COUNT(*) FROM notifications WHERE title = :title")
    int getNotificationCountByTitle(String title);

    @Query("SELECT COUNT(*) FROM notifications WHERE title = :title AND body = :body")
    int getNotificationCountByTitleAndBody(String title, String body);
}
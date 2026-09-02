package com.hansanie.greencart.network;

import android.util.Log;
import android.text.TextUtils;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hansanie.greencart.activity.SubscriptionActionActivity;
import com.hansanie.greencart.worker.SubscriptionReminderWorker;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.hansanie.greencart.util.NotificationHelper;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    public static final String ACTION_NOTIFICATION_COUNT_UPDATED =
            "com.hansanie.greencart.NOTIFICATION_COUNT_UPDATED";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        String type = remoteMessage.getData().get("type");
        String conversationId = remoteMessage.getData().get("conversationId");
        String orderId = remoteMessage.getData().get("orderId");
        String supportPhone = remoteMessage.getData().get("supportPhone");
        String title = null;
        String body  = null;
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body  = remoteMessage.getNotification().getBody();
        } else if (!remoteMessage.getData().isEmpty()) {
            title = remoteMessage.getData().get("title");
            body  = remoteMessage.getData().get("body");
        }

        String safeType = type != null ? type : "general";
        String safeTitle = !TextUtils.isEmpty(title) ? title : "GreenCart";
        String safeBody = !TextUtils.isEmpty(body) ? body : "You have a new update";

        if (!TextUtils.isEmpty(safeTitle) || !TextUtils.isEmpty(safeBody)) {
            if ("support_message".equalsIgnoreCase(safeType)) {
                NotificationHelper.showSupportChatNotification(
                        this,
                        safeTitle,
                        safeBody,
                        conversationId,
                        orderId,
                        supportPhone
                );
                return;
            }

            if ("order_status".equalsIgnoreCase(safeType)) {
                NotificationHelper.showOrderStatusNotification(
                        this,
                        safeTitle,
                        safeBody,
                        orderId
                );
                return;
            }
            // Handle subscription reminder (smart_reminder) specially so clicking opens SubscriptionActionActivity
            if ("smart_reminder".equalsIgnoreCase(safeType) || "subscription_reminder".equalsIgnoreCase(safeType)) {
                Intent intent = new Intent(this, SubscriptionActionActivity.class);
                // Try multiple common key names that backend might send
                String firestoreId = getFirstNonEmpty(remoteMessage.getData().get("extra_firestore_id"),
                        remoteMessage.getData().get("firestoreId"),
                        remoteMessage.getData().get("firestore_id"));
                String mysqlIdStr = getFirstNonEmpty(remoteMessage.getData().get("extra_mysql_id"),
                        remoteMessage.getData().get("mysqlId"),
                        remoteMessage.getData().get("mysql_id"));
                String subNameData = getFirstNonEmpty(remoteMessage.getData().get("extra_sub_name"),
                        remoteMessage.getData().get("subName"),
                        remoteMessage.getData().get("sub_name"),
                        remoteMessage.getData().get("name"));
                String nextDateData = getFirstNonEmpty(remoteMessage.getData().get("extra_next_date"),
                        remoteMessage.getData().get("nextDate"),
                        remoteMessage.getData().get("next_date"));
                String totalAmountStr = getFirstNonEmpty(remoteMessage.getData().get("extra_total_amount"),
                        remoteMessage.getData().get("totalAmount"),
                        remoteMessage.getData().get("total_amount"));

                if (!TextUtils.isEmpty(firestoreId)) {
                    intent.putExtra(SubscriptionReminderWorker.EXTRA_FIRESTORE_ID, firestoreId);
                }
                Long mysqlIdVal = parseLong(mysqlIdStr);
                if (mysqlIdVal != null) intent.putExtra(SubscriptionReminderWorker.EXTRA_MYSQL_ID, mysqlIdVal);
                if (!TextUtils.isEmpty(subNameData)) intent.putExtra(SubscriptionReminderWorker.EXTRA_SUB_NAME, subNameData);
                if (!TextUtils.isEmpty(nextDateData)) intent.putExtra(SubscriptionReminderWorker.EXTRA_NEXT_DATE, nextDateData);
                Double totalAmountVal = parseDouble(totalAmountStr);
                if (totalAmountVal != null) intent.putExtra(SubscriptionReminderWorker.EXTRA_TOTAL_AMOUNT, totalAmountVal);

                // Ensure activity starts correctly from background
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                NotificationHelper.showAndPersist(
                        this,
                        NotificationHelper.CHANNEL_REMINDERS,
                        "Smart Reminders",
                        safeTitle,
                        safeBody,
                        intent
                );
                return;
            }

            String channelId = NotificationHelper.CHANNEL_GENERAL;
            String channelName = "Offers";
            if ("arrival".equalsIgnoreCase(safeType)) {
                channelId = NotificationHelper.CHANNEL_ORDERS;
                channelName = "Orders";
            }
            NotificationHelper.showAndPersist(
                    this,
                    channelId,
                    channelName,
                    safeTitle,
                    safeBody
            );
        }
    }

    private String getFirstNonEmpty(String... candidates) {
        if (candidates == null) return null;
        for (String c : candidates) {
            if (c != null && !c.trim().isEmpty()) return c.trim();
        }
        return null;
    }

    private Long parseLong(String s) {
        if (s == null) return null;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException ignored) { return null; }
    }

    private Double parseDouble(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) { return null; }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d("FCM_TOKEN", "Token refreshed: " + token);

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || uid.trim().isEmpty()) {
            uid = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .getString("firebase_uid", null);
        }
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("fcm_token", token)
                .addOnFailureListener(e -> Log.w("FCM_TOKEN", "Firestore token update failed", e));

        FcmTokenSyncManager.syncTokenToMySql(uid, token);
    }
}
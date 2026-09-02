package com.hansanie.greencart.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.hansanie.greencart.R;
import com.hansanie.greencart.activity.MainActivity;
import com.hansanie.greencart.database.AppDatabase;
import com.hansanie.greencart.model.NotificationItem;
import com.hansanie.greencart.network.MyFirebaseMessagingService;
import com.hansanie.greencart.worker.SubscriptionReminderWorker;

import java.util.concurrent.Executors;
import com.hansanie.greencart.util.AppExecutors;

public final class NotificationHelper {

    public static final String CHANNEL_GENERAL = "offers_channel";
    public static final String CHANNEL_ORDERS = "orders_channel";
    public static final String CHANNEL_REMINDERS = "reminders_channel";
    public static final String CHANNEL_SUPPORT = "support_channel";

    public static final String EXTRA_OPEN_DESTINATION = "open_destination";
    public static final String DEST_SUPPORT_CHAT = "support_chat";
    public static final String DEST_ORDERS = "orders";
    public static final String DEST_SUBSCRIPTIONS = "subscriptions";
    public static final String EXTRA_CONVERSATION_ID = "conversation_id";
    public static final String EXTRA_ORDER_ID = "order_id";
    public static final String EXTRA_SUPPORT_PHONE = "support_phone";

    private NotificationHelper() {
    }

    // SharedPreferences keys for dismissed notifications (cleared by user)
    private static final String PREFS_NAME = "greencart_notif_prefs";
    private static final String KEY_DISMISSED_OFFERS = "dismissed_offers"; // Set<String> of offerId strings
    private static final String KEY_DISMISSED_MSGS = "dismissed_msgs"; // Set<String> of title|body hashes
    private static final String KEY_DISMISSED_SUPPORT_MSG_IDS = "dismissed_support_msg_ids"; // Set<String> of support message ids

    public static void addDismissedOffer(Context context, Long offerId) {
        if (offerId == null) return;
        android.content.SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        java.util.Set<String> set = new java.util.HashSet<>(prefs.getStringSet(KEY_DISMISSED_OFFERS, new java.util.HashSet<>()));
        set.add(String.valueOf(offerId));
        prefs.edit().putStringSet(KEY_DISMISSED_OFFERS, set).apply();
    }

    public static boolean isOfferDismissed(Context context, Long offerId) {
        if (offerId == null) return false;
        android.content.SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        java.util.Set<String> set = prefs.getStringSet(KEY_DISMISSED_OFFERS, new java.util.HashSet<>());
        return set.contains(String.valueOf(offerId));
    }

    public static void addDismissedMessage(Context context, String title, String body) {
        String key = makeMessageKey(title, body);
        android.content.SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        java.util.Set<String> set = new java.util.HashSet<>(prefs.getStringSet(KEY_DISMISSED_MSGS, new java.util.HashSet<>()));
        set.add(key);
        prefs.edit().putStringSet(KEY_DISMISSED_MSGS, set).apply();
    }

    public static boolean isMessageDismissed(Context context, String title, String body) {
        String key = makeMessageKey(title, body);
        android.content.SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        java.util.Set<String> set = prefs.getStringSet(KEY_DISMISSED_MSGS, new java.util.HashSet<>());
        return set.contains(key);
    }

    public static void addDismissedSupportMessageId(Context context, String messageId) {
        if (messageId == null) return;
        android.content.SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        java.util.Set<String> set = new java.util.HashSet<>(prefs.getStringSet(KEY_DISMISSED_SUPPORT_MSG_IDS, new java.util.HashSet<>()));
        set.add(messageId);
        prefs.edit().putStringSet(KEY_DISMISSED_SUPPORT_MSG_IDS, set).apply();
    }

    public static boolean isSupportMessageDismissed(Context context, String messageId) {
        if (messageId == null) return false;
        android.content.SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        java.util.Set<String> set = prefs.getStringSet(KEY_DISMISSED_SUPPORT_MSG_IDS, new java.util.HashSet<>());
        return set.contains(messageId);
    }

    private static String makeMessageKey(String title, String body) {
        String key = (title != null ? title : "") + "|" + (body != null ? body : "");
        return String.valueOf(key.hashCode());
    }

    public static void showAndPersist(Context context, String channelId, String channelName,
                                      String title, String message) {
        if (context == null || (TextUtils.isEmpty(title) && TextUtils.isEmpty(message))) return;
        // If user previously cleared this message, do not show it again
        if (isMessageDismissed(context.getApplicationContext(), title, message)) return;
        // Persist then show - saveNotificationToRoom handles dedupe and broadcasting
        saveNotificationToRoom(context, title != null ? title : "",
                message != null ? message : "", null, null, null);
        showNotification(context, channelId, channelName, title, message, null);
    }

    public static void showAndPersist(Context context, String channelId, String channelName, String title, String message, @Nullable Intent clickIntent) {
        if (context == null || (TextUtils.isEmpty(title) && TextUtils.isEmpty(message))) {
            return;
        }
        // If user previously cleared this message, do not show it again
        if (isMessageDismissed(context.getApplicationContext(), title, message)) return;
        // Persist destination and payload if clickIntent contains known extras
        String dest = null;
        String payload = null;
        if (clickIntent != null) {
            if (clickIntent.hasExtra(EXTRA_OPEN_DESTINATION)) {
                dest = clickIntent.getStringExtra(EXTRA_OPEN_DESTINATION);
            }
            // subscription extras
            if (clickIntent.hasExtra(SubscriptionReminderWorker.EXTRA_FIRESTORE_ID)
                    || clickIntent.hasExtra(SubscriptionReminderWorker.EXTRA_MYSQL_ID)) {
                android.os.Bundle b = clickIntent.getExtras();
                // simple JSON-like string to store payload
                StringBuilder sb = new StringBuilder();
                if (b != null) {
                    if (b.containsKey(SubscriptionReminderWorker.EXTRA_FIRESTORE_ID)) {
                        sb.append("firestoreId=").append(b.getString(SubscriptionReminderWorker.EXTRA_FIRESTORE_ID)).append(";");
                        dest = DEST_SUBSCRIPTIONS;
                    }
                    if (b.containsKey(SubscriptionReminderWorker.EXTRA_MYSQL_ID)) {
                        sb.append("mysqlId=").append(b.getLong(SubscriptionReminderWorker.EXTRA_MYSQL_ID)).append(";");
                        dest = DEST_SUBSCRIPTIONS;
                    }
                    if (b.containsKey(SubscriptionReminderWorker.EXTRA_SUB_NAME)) {
                        sb.append("name=").append(b.getString(SubscriptionReminderWorker.EXTRA_SUB_NAME)).append(";");
                    }
                    if (b.containsKey(SubscriptionReminderWorker.EXTRA_NEXT_DATE)) {
                        sb.append("nextDate=").append(b.getString(SubscriptionReminderWorker.EXTRA_NEXT_DATE)).append(";");
                    }
                    if (b.containsKey(SubscriptionReminderWorker.EXTRA_TOTAL_AMOUNT)) {
                        sb.append("totalAmount=").append(b.getDouble(SubscriptionReminderWorker.EXTRA_TOTAL_AMOUNT)).append(";");
                    }
                    payload = sb.length() > 0 ? sb.toString() : null;
                }
            }
        }

        // Persist then show - saveNotificationToRoom handles dedupe and broadcasting
        saveNotificationToRoom(context, title != null ? title : "", message != null ? message : "", null, dest, payload);
        showNotification(context, channelId, channelName, title, message, clickIntent);
    }

    public static void showNotification(Context context, String channelId, String channelName, String title, String message) {
        showNotification(context, channelId, channelName, title, message, null);
    }

    public static void showNotification(Context context, String channelId, String channelName, String title, String message, @Nullable Intent clickIntent) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            nm.createNotificationChannel(channel);
        }

        Intent intent = clickIntent != null ? clickIntent : new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // Compute a stable notification id so duplicates can be updated instead of creating many notifications.
        int notificationId = makeNotificationId(channelId, title, message);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        nm.notify(notificationId, builder.build());
    }

    private static int makeNotificationId(String channelId, String title, String message) {
        // Prefer using channel-specific stable ids. For offers/general messages use title+message hash.
        String key = channelId + "|" + (title != null ? title : "") + "|" + (message != null ? message : "");
        return key.hashCode();
    }

    public static void showSupportChatNotification(
            Context context,
            @Nullable String title,
            @Nullable String message,
            @Nullable String conversationId,
            @Nullable String orderId,
            @Nullable String supportPhone) {
        showSupportChatNotification(context, title, message, conversationId, orderId, supportPhone, null);
    }

    public static void showSupportChatNotification(
            Context context,
            @Nullable String title,
            @Nullable String message,
            @Nullable String conversationId,
            @Nullable String orderId,
            @Nullable String supportPhone,
            @Nullable String supportMessageId) {

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(EXTRA_OPEN_DESTINATION, DEST_SUPPORT_CHAT);
        if (!TextUtils.isEmpty(conversationId)) intent.putExtra(EXTRA_CONVERSATION_ID, conversationId);
        if (!TextUtils.isEmpty(orderId))        intent.putExtra(EXTRA_ORDER_ID, orderId);
        if (!TextUtils.isEmpty(supportPhone))   intent.putExtra(EXTRA_SUPPORT_PHONE, supportPhone);

        String safeTitle   = !TextUtils.isEmpty(title)   ? title   : "GreenCart Support";
        String safeMessage = !TextUtils.isEmpty(message) ? message : "You have a new support message";

        // If user previously cleared this exact message (by title+body) or dismissed this support message by id, skip showing
        if (isMessageDismissed(context.getApplicationContext(), title, message)) return;
        if (!TextUtils.isEmpty(supportMessageId) && isSupportMessageDismissed(context.getApplicationContext(), supportMessageId)) return;

        // Build payload
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(conversationId)) sb.append("conversationId=").append(conversationId).append(";");
        if (!TextUtils.isEmpty(orderId))        sb.append("orderId=").append(orderId).append(";");
        if (!TextUtils.isEmpty(supportPhone))   sb.append("supportPhone=").append(supportPhone).append(";");
        final String payload = sb.length() > 0 ? sb.toString() : null;
        // If we have a supportMessageId, append it to payload for persistence and dismissal tracking
        final String fullPayload = (!TextUtils.isEmpty(supportMessageId))
                ? (payload != null ? payload + "messageId=" + supportMessageId + ";" : ("messageId=" + supportMessageId + ";"))
                : payload;

        // Insert into DB (dedupe) and then show system notification from the same DB executor
        AppExecutors.DB.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
            int dup = db.notificationDao().getNotificationCountByTitleAndBody(safeTitle, safeMessage);
            if (dup == 0) {
                NotificationItem item = new NotificationItem(safeTitle, safeMessage, null, DEST_SUPPORT_CHAT, fullPayload);
                db.notificationDao().insert(item);
                Intent broadcast = new Intent(MyFirebaseMessagingService.ACTION_NOTIFICATION_COUNT_UPDATED);
                LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(broadcast);
                // Only show the system notification when it's the first time we see this support message
                showNotification(context, CHANNEL_SUPPORT, "Support Chat", safeTitle, safeMessage, intent);
            } else {
                // already exists — do not show again
            }
        });
        // ────────────────────────────────────────────────────────────────────
    }

    public static void showOrderStatusNotification(
            Context context,
            @Nullable String title,
            @Nullable String message,
            @Nullable String orderId) {

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(EXTRA_OPEN_DESTINATION, DEST_ORDERS);
        if (!TextUtils.isEmpty(orderId)) intent.putExtra(EXTRA_ORDER_ID, orderId);

        String safeTitle   = !TextUtils.isEmpty(title)   ? title   : "Order Update";
        String safeMessage = !TextUtils.isEmpty(message) ? message : "Your order status has changed";

        // If user previously cleared this exact message, skip showing
        if (isMessageDismissed(context.getApplicationContext(), title, message)) return;

        final String payload = !TextUtils.isEmpty(orderId) ? "orderId=" + orderId + ";" : null;
        AppExecutors.DB.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
            int dup = db.notificationDao().getNotificationCountByTitleAndBody(safeTitle, safeMessage);
            if (dup == 0) {
                NotificationItem item = new NotificationItem(safeTitle, safeMessage, null, DEST_ORDERS, payload);
                db.notificationDao().insert(item);
                Intent broadcast = new Intent(MyFirebaseMessagingService.ACTION_NOTIFICATION_COUNT_UPDATED);
                LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(broadcast);
            }
            showNotification(context, CHANNEL_ORDERS, "Orders", safeTitle, safeMessage, intent);
        });
    }

    public static void showOfferNotificationIfNew(Context context, String title,
                                                  String message, Long offerId) {
        if (context == null) return;
        // If user dismissed this offer previously, do not notify again
        if (offerId != null && isOfferDismissed(context.getApplicationContext(), offerId)) return;

        // ── offerId null නම් title+body unique check ─────────────────────
        if (offerId == null) {
            // offerId null — dedupe by title+body using the shared DB executor
            AppExecutors.DB.execute(() -> {
                int count = AppDatabase.getInstance(context.getApplicationContext())
                        .notificationDao().getNotificationCountByTitleAndBody(title != null ? title : "", message != null ? message : "");
                if (count == 0) {
                    showNotification(context, CHANNEL_GENERAL, "Offers", title, message, null);
                    saveNotificationToRoom(context, title != null ? title : "",
                            message != null ? message : "", null, null, null);
                }
            });
            return;
        }
        // ─────────────────────────────────────────────────────────────────

        AppExecutors.DB.execute(() -> {
            int count = AppDatabase.getInstance(context.getApplicationContext())
                    .notificationDao().getOfferNotificationCount(offerId);
            if (count == 0) {
                showNotification(context, CHANNEL_GENERAL, "Offers", title, message, null);
                saveNotificationToRoom(context, title != null ? title : "",
                        message != null ? message : "", offerId, null, null);
            }
        });
    }

    public static void saveNotificationToRoom(Context context, String title, String body, Long offerId) {
        AppExecutors.DB.execute(() -> {
            // If offerId is null, try to dedupe by title+body
            AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
            // If user dismissed this notification previously, do not persist it
            if (offerId == null && isMessageDismissed(context.getApplicationContext(), title, body)) {
                Intent broadcast = new Intent(MyFirebaseMessagingService.ACTION_NOTIFICATION_COUNT_UPDATED);
                LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(broadcast);
                return;
            }
            if (offerId != null && isOfferDismissed(context.getApplicationContext(), offerId)) {
                Intent broadcast = new Intent(MyFirebaseMessagingService.ACTION_NOTIFICATION_COUNT_UPDATED);
                LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(broadcast);
                return;
            }
            if (offerId == null) {
                int dup = db.notificationDao().getNotificationCountByTitleAndBody(title != null ? title : "", body != null ? body : "");
                if (dup > 0) {
                    // Already exists — just broadcast count update in case UI needs sync
                    Intent broadcast = new Intent(MyFirebaseMessagingService.ACTION_NOTIFICATION_COUNT_UPDATED);
                    LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(broadcast);
                    return;
                }
            }

            NotificationItem item = new NotificationItem(title, body, offerId);
            db.notificationDao().insert(item);
            Intent broadcast = new Intent(MyFirebaseMessagingService.ACTION_NOTIFICATION_COUNT_UPDATED);
            LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(broadcast);
        });
    }

    public static void saveNotificationToRoom(Context context, String title, String body, Long offerId, String destination, String payload) {
        AppExecutors.DB.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
            // If payload contains a support message id and that id is dismissed, skip persisting
            if (!TextUtils.isEmpty(payload) && payload.contains("messageId=")) {
                // extract messageId
                String mid = null;
                try {
                    int idx = payload.indexOf("messageId=");
                    if (idx >= 0) {
                        int start = idx + "messageId=".length();
                        int end = payload.indexOf(';', start);
                        mid = end > start ? payload.substring(start, end) : payload.substring(start);
                    }
                } catch (Exception ignored) { }
                if (!TextUtils.isEmpty(mid) && isSupportMessageDismissed(context.getApplicationContext(), mid)) {
                    Intent broadcast = new Intent(MyFirebaseMessagingService.ACTION_NOTIFICATION_COUNT_UPDATED);
                    LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(broadcast);
                    return;
                }
            }
            if (offerId == null) {
                int dup = db.notificationDao().getNotificationCountByTitleAndBody(title != null ? title : "", body != null ? body : "");
                if (dup > 0) {
                    Intent broadcast = new Intent(MyFirebaseMessagingService.ACTION_NOTIFICATION_COUNT_UPDATED);
                    LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(broadcast);
                    return;
                }
            }

            NotificationItem item = new NotificationItem(title, body, offerId, destination, payload);
            db.notificationDao().insert(item);
            Intent broadcast = new Intent(MyFirebaseMessagingService.ACTION_NOTIFICATION_COUNT_UPDATED);
            LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(broadcast);
        });
    }
}


package com.hansanie.greencart.worker;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.hansanie.greencart.dto.SubscriptionSaveRequest;
import com.hansanie.greencart.network.ApiService;
import com.hansanie.greencart.network.RetrofitClient;
import com.hansanie.greencart.util.NotificationHelper;
import com.hansanie.greencart.activity.MainActivity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SubscriptionReminderWorker extends Worker {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String PREFS_REMINDER_STATE = "subscription_reminder_state";
    public static final String UNIQUE_WORK_NAME = "subscription_reminder_precheck";

    // Intent extras — SubscriptionActionActivity ට pass කරනවා
    public static final String EXTRA_FIRESTORE_ID = "extra_firestore_id";
    public static final String EXTRA_MYSQL_ID = "extra_mysql_id";
    public static final String EXTRA_SUB_NAME = "extra_sub_name";
    public static final String EXTRA_NEXT_DATE = "extra_next_date";
    public static final String EXTRA_TOTAL_AMOUNT = "extra_total_amount";

    public SubscriptionReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    public static PeriodicWorkRequest newPeriodicRequest() {
        return new PeriodicWorkRequest.Builder(
                SubscriptionReminderWorker.class,
                12,
                TimeUnit.HOURS
        ).build();
    }

    @NonNull
    @Override
    public Result doWork() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return Result.success();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        ApiService apiService = RetrofitClient.getApiService();
        SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences(PREFS_REMINDER_STATE, Context.MODE_PRIVATE);
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        try {
            for (DocumentSnapshot doc : Tasks.await(
                    db.collection("grocery_subscriptions")
                            .whereEqualTo("firebaseUid", user.getUid())
                            .whereEqualTo("status", "ACTIVE")
                            .get()).getDocuments()) {

                LocalDate nextDate = parseDate(readString(doc.get("next_delivery_date"),
                        readString(doc.get("nextDeliveryDate"), null)));
                if (nextDate == null) continue;

                boolean skipNext = readBoolean(doc.get("skip_next")) || readBoolean(doc.get("skipNext"));
                int intervalDays = readInt(doc.get("intervalDays"), 7);
                String scheduleName = readString(doc.get("name"), "Subscription");
                String reminderKey = buildReminderKey(user.getUid(), doc.getId());
                Long subscriptionId = readLong(doc.get("id"));
                double totalAmount = readDouble(doc.get("totalAmount"), 0.0);

                // Skip next handle
                if (skipNext && !nextDate.isAfter(tomorrow)) {
                    LocalDate advanced = nextDate;
                    while (!advanced.isAfter(tomorrow)) {
                        advanced = advanced.plusDays(Math.max(7, intervalDays));
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("skip_next", false);
                    updates.put("next_delivery_date", advanced.format(DATE_FORMATTER));
                    updates.put("updatedAt", LocalDateTime.now().format(DATE_TIME_FORMATTER));
                    Tasks.await(doc.getReference().set(updates, SetOptions.merge()));

                    if (subscriptionId != null) {
                        SubscriptionSaveRequest payload = SubscriptionSaveRequest.builder()
                                .id(subscriptionId)
                                .firebaseUid(user.getUid())
                                .name(scheduleName)
                                .frequency(readString(doc.get("frequency"), "WEEKLY"))
                                .deliveryDay(readString(doc.get("deliveryDay"), "Monday"))
                                .deliveryTimeSlot(readString(doc.get("deliveryTimeSlot"), "07:00 AM"))
                                .nextDeliveryDate(advanced.format(DATE_FORMATTER))
                                .status("ACTIVE")
                                .skipNextDelivery(false)
                                .items(new ArrayList<>())
                                .build();
                        try {
                            apiService.saveGrocerySubscription(payload).execute();
                            apiService.updateSkipNext(subscriptionId, false).execute();
                        } catch (Exception ignored) {}
                    }
                    continue;
                }

                // Tomorrow delivery notification — 8PM check
                if (!skipNext && nextDate.equals(tomorrow)) {
                    if (wasReminderAlreadySent(prefs, reminderKey, nextDate)) continue;

                    // Open MainActivity directly and instruct it to open the subscription review
                    Intent actionIntent = new Intent(getApplicationContext(), MainActivity.class);
                    // Signal to MainActivity that it should open the Subscriptions destination
                    actionIntent.putExtra(NotificationHelper.EXTRA_OPEN_DESTINATION, NotificationHelper.DEST_SUBSCRIPTIONS);
                    // Pass subscription identifiers/context so the fragment can open the review sheet directly
                    actionIntent.putExtra(EXTRA_FIRESTORE_ID, doc.getId());
                    if (subscriptionId != null) actionIntent.putExtra(EXTRA_MYSQL_ID, subscriptionId);
                    actionIntent.putExtra(EXTRA_SUB_NAME, scheduleName);
                    actionIntent.putExtra(EXTRA_NEXT_DATE, nextDate.format(DATE_FORMATTER));
                    actionIntent.putExtra(EXTRA_TOTAL_AMOUNT, totalAmount);
                    // Also add a helper flag used elsewhere to auto-open the review sheet
                    actionIntent.putExtra("auto_open_review", true);
                    actionIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                    NotificationHelper.showAndPersist(
                            getApplicationContext(),
                            NotificationHelper.CHANNEL_REMINDERS,
                            "Upcoming Delivery Tomorrow",
                            scheduleName,
                            String.format(Locale.getDefault(),
                                    "Your %s delivery is scheduled for tomorrow. Tap to pay or skip.",
                                    scheduleName),
                            actionIntent
                    );
                    markReminderSent(prefs, reminderKey, nextDate);
                }
            }
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private int readInt(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt(((String) value).trim()); }
            catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private boolean readBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        if (value instanceof String) {
            String n = ((String) value).trim();
            return "true".equalsIgnoreCase(n) || "1".equals(n);
        }
        return false;
    }

    private Long readLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try { return Long.parseLong(((String) value).trim()); }
            catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private double readDouble(Object value, double fallback) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble(((String) value).trim()); }
            catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    private String readString(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try { return LocalDate.parse(raw.trim(), DATE_FORMATTER); }
        catch (Exception ignored) { return null; }
    }

    @NonNull
    private String buildReminderKey(@NonNull String uid, @NonNull String firestoreId) {
        return uid + "_" + firestoreId;
    }

    private boolean wasReminderAlreadySent(SharedPreferences prefs, String key, LocalDate date) {
        return date.format(DATE_FORMATTER).equals(prefs.getString(key, null));
    }

    private void markReminderSent(SharedPreferences prefs, String key, LocalDate date) {
        prefs.edit().putString(key, date.format(DATE_FORMATTER)).apply();
    }
}
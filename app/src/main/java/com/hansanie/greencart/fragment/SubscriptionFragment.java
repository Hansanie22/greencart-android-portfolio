package com.hansanie.greencart.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.hansanie.greencart.R;
import com.hansanie.greencart.adapter.SubscriptionAdapter;
import com.hansanie.greencart.database.AppDatabase;
import com.hansanie.greencart.dto.SubscriptionItemUpsertRequest;
import com.hansanie.greencart.dto.SubscriptionSaveRequest;
import com.hansanie.greencart.model.GrocerySubscription;
import com.hansanie.greencart.model.GrocerySubscriptionItem;
import com.hansanie.greencart.network.ApiService;
import com.hansanie.greencart.network.RetrofitClient;
import com.hansanie.greencart.util.CustomToast;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SubscriptionFragment extends Fragment {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private RecyclerView rvSubscriptions;
    private LinearLayout layoutEmptyState;
    private TextView tvScheduleCount;
    private TextView tvTotalSavings;
    private TextView tvSmartReminder;
    private TextView tvGreenPointsBalance;
    private SubscriptionAdapter adapter;
    private List<GrocerySubscription> subscriptionList;
    private FirebaseFirestore db;
    private ApiService apiService;
    private boolean targetSubscriptionHandled = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_subscription, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            rvSubscriptions = view.findViewById(R.id.rvSubscriptions);
            layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
            tvScheduleCount = view.findViewById(R.id.tvScheduleCount);
            tvTotalSavings = view.findViewById(R.id.tvTotalSavings);
            tvSmartReminder = view.findViewById(R.id.tvSmartReminder);
            tvGreenPointsBalance = view.findViewById(R.id.tvGreenPointsBalance);
            ExtendedFloatingActionButton fabNewSchedule = view.findViewById(R.id.fabNewSchedule);
            db = FirebaseFirestore.getInstance();
            apiService = RetrofitClient.getApiService();

            setupRecyclerView();
            loadSubscriptions();

            if (fabNewSchedule != null) {
                fabNewSchedule.setOnClickListener(v -> {
                    try {
                        CreateSubscriptionSheet sheet = new CreateSubscriptionSheet(this::loadSubscriptions);
                        if (isAdded() && getChildFragmentManager() != null) {
                            sheet.show(getChildFragmentManager(), "CreateSchedule");
                        }
                    } catch (Exception ex) {
                        android.util.Log.e("SubscriptionFragment", "Failed to show CreateSubscriptionSheet", ex);
                    }
                });
            }
        } catch (Exception e) {
            // Guard against unexpected exceptions during view setup
            android.util.Log.e("SubscriptionFragment", "onViewCreated setup failed", e);
        }
    }

    private void setupRecyclerView() {
        subscriptionList = new ArrayList<>();
        adapter = new SubscriptionAdapter(getContext(), subscriptionList, new SubscriptionAdapter.OnSubscriptionActionListener() {
            @Override
            public void onModifyItems(GrocerySubscription sub) {
                showEditBottomSheet(sub);
            }

            @Override
            public void onSkipDelivery(GrocerySubscription sub) {
                skipNextDelivery(sub);
            }

            @Override
            public void onPauseResume(GrocerySubscription sub) {
                togglePauseResume(sub);
            }

            @Override
            public void onCancel(GrocerySubscription sub) {
                cancelSubscription(sub);
            }

            @Override
            public void onReviewAndPay(GrocerySubscription sub) {
                showReviewAndPayDialog(sub);
            }
        });
        rvSubscriptions.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSubscriptions.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSubscriptions();
    }

    private void loadSubscriptions() {
        if (!isAdded()) return;

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            subscriptionList.clear();
            adapter.notifyDataSetChanged();
            tvScheduleCount.setText("0");
            layoutEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        db.collection("grocery_subscriptions")
                .whereEqualTo("firebaseUid", uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    try {
                        subscriptionList.clear();
                        for (var doc : snapshot.getDocuments()) {
                            if (doc == null || !doc.exists()) continue;
                            GrocerySubscription sub = new GrocerySubscription();
                            sub.setFirestoreId(doc.getId());
                            sub.setId(readLong(doc.get("id")));
                            sub.setFirebaseUid(readString(doc.get("firebaseUid"), readString(doc.get("firebase_uid"), null)));
                            sub.setName(readString(doc.get("name"), readString(doc.get("title"), "")));
                            sub.setStartDate(readString(doc.get("start_date"), readString(doc.get("startDate"), "")));
                            sub.setNextDeliveryDate(readString(doc.get("next_delivery_date"), readString(doc.get("nextDeliveryDate"), "")));
                            sub.setDeliveryDay(readString(doc.get("deliveryDay"), readString(doc.get("delivery_day"), "Monday")));
                            sub.setIntervalDays(readInt(doc.get("intervalDays"), readInt(doc.get("interval_days"), 7)));
                            // frequency fallback for interval
                            if (sub.getIntervalDays() == null || sub.getIntervalDays() <= 0) {
                                String freq = readString(doc.get("frequency"), "WEEKLY").toUpperCase(Locale.getDefault());
                                if (freq.contains("BI") || freq.contains("2W")) sub.setIntervalDays(14);
                                else if (freq.contains("MONTH")) sub.setIntervalDays(30);
                                else sub.setIntervalDays(7);
                            }
                            sub.setBonusPoints(readInt(doc.get("bonusPoints"), readInt(doc.get("bonus_points"), 0)));
                            sub.setSkipNext(readBoolean(doc.get("skip_next")) || readBoolean(doc.get("skipNext")));
                            List<GrocerySubscriptionItem> parsedItems = parseItems(doc.get("items"));
                            sub.setItems(parsedItems);
                            // ensure itemCount reflects parsed items so UI shows correct count
                            sub.setItemCount(parsedItems != null ? parsedItems.size() : 0);
                            sub.setStatus(readString(doc.get("status"), "ACTIVE"));
                            Double total = readDouble(doc.get("totalAmount"));
                            if (total == null) total = readDouble(doc.get("total_amount"));
                            sub.setTotalAmount(total);

                            String status = sub.getStatus() != null ? sub.getStatus().toUpperCase(Locale.getDefault()) : "ACTIVE";
                            if (!"CANCELLED".equals(status)) {
                                subscriptionList.add(sub);
                            }
                        }
                        if (adapter != null) adapter.notifyDataSetChanged();
                        if (tvScheduleCount != null) tvScheduleCount.setText(String.valueOf(subscriptionList.size()));
                        if (layoutEmptyState != null) layoutEmptyState.setVisibility(subscriptionList.isEmpty() ? View.VISIBLE : View.GONE);
                        cacheSubscriptions();
                        loadInsightsFromFirestore();
                        handleTargetSubscriptionIfAny();
                    } catch (Exception ex) {
                        android.util.Log.e("SubscriptionFragment", "Error processing subscription documents", ex);
                        // Fallback to local cache if anything goes wrong while parsing
                        loadSubscriptionsFromLocal();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("SubscriptionFragment", "Firestore query failed", e);
                    loadSubscriptionsFromLocal();
                });
    }

    private void cacheSubscriptions() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(requireContext()).subscriptionDao().clearAll();
            AppDatabase.getInstance(requireContext()).subscriptionDao().insertAll(subscriptionList);
        });
    }

    private void loadSubscriptionsFromLocal() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<GrocerySubscription> cached = AppDatabase.getInstance(requireContext()).subscriptionDao().getAll();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                subscriptionList.clear();
                subscriptionList.addAll(cached);
                adapter.notifyDataSetChanged();
                tvScheduleCount.setText(String.valueOf(subscriptionList.size()));
                layoutEmptyState.setVisibility(subscriptionList.isEmpty() ? View.VISIBLE : View.GONE);
                bindInsightsFromList();
                handleTargetSubscriptionIfAny();
            });
        });
    }

    private void loadInsightsFromFirestore() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        LocalDate today        = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        LocalDate lastOfMonth  = firstOfMonth.plusMonths(1).minusDays(1);

        db.collection("grocery_subscriptions")
                .whereEqualTo("firebaseUid", uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    double monthlySavings = 0.0;
                    int    greenPoints    = 0;

                    for (var doc : snapshot.getDocuments()) {
                        greenPoints += readInt(doc.get("bonusPoints"), 0);

                        double discount = 0.0;
                        Object rawDiscount = doc.get("discountAmount");
                        if (rawDiscount instanceof Number)
                            discount = ((Number) rawDiscount).doubleValue();
                        double baseFivePercent = 0.0;
                        try {
                            double itemsTotal = computeItemsTotalFromRaw(doc.get("items"));
                            baseFivePercent = itemsTotal * 0.05;
                        } catch (Exception ignored) {}
                        if (discount <= 0 && baseFivePercent <= 0) continue;

                        LocalDate base = null;
                        try {
                            String s = readString(doc.get("next_delivery_date"),
                                    readString(doc.get("nextDeliveryDate"), ""));
                            if (!s.isEmpty()) base = LocalDate.parse(s, DATE_FORMATTER);
                        } catch (Exception ignored) {}
                        if (base == null) {
                            try {
                                String s = readString(doc.get("start_date"), "");
                                if (!s.isEmpty()) base = LocalDate.parse(s, DATE_FORMATTER);
                            } catch (Exception ignored) {}
                        }

                        int interval = readInt(doc.get("intervalDays"), 0);
                        if (interval <= 0) {
                            String freq = readString(doc.get("frequency"), "WEEKLY").toUpperCase(Locale.getDefault());
                            if (freq.contains("BI") || freq.contains("2W")) interval = 14;
                            else if (freq.contains("MONTH"))                 interval = 30;
                            else                                              interval = 7;
                        }

                        if (base == null) { monthlySavings += discount; continue; }
                        int occurrences = countOccurrencesInMonth(base, interval, firstOfMonth, lastOfMonth);
                        monthlySavings += occurrences * (discount + baseFivePercent);
                    }

                    double finalSavings = monthlySavings;
                    int    finalPoints  = greenPoints;
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            tvTotalSavings.setText(String.format(Locale.getDefault(), "Rs. %,.2f", finalSavings));
                            tvGreenPointsBalance.setText(String.format(Locale.getDefault(), "%d pts", finalPoints));
                        });
                    }
                })
                .addOnFailureListener(e -> bindInsightsFromList());
    }

    private int countOccurrencesInMonth(LocalDate base, int interval,
                                        LocalDate firstOfMonth, LocalDate lastOfMonth) {
        LocalDate d = base;
        while (d.isBefore(firstOfMonth)) d = d.plusDays(interval);
        while (d.isAfter(lastOfMonth))   d = d.minusDays(interval);
        if (d.isAfter(lastOfMonth) || d.isBefore(firstOfMonth)) return 0;
        while (d.isAfter(firstOfMonth))  d = d.minusDays(interval);
        if (d.isBefore(firstOfMonth))    d = d.plusDays(interval);
        int count = 0;
        while (!d.isAfter(lastOfMonth)) { count++; d = d.plusDays(interval); }
        return count;
    }

    private void bindInsightsFromList() {
        double monthlySavings = 0.0;
        int    greenPoints    = 0;
        String reminderMessage = "Build a subscription to unlock smart reminders";
        LocalDate today        = LocalDate.now();
        long closestGap        = Long.MAX_VALUE;
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        LocalDate lastOfMonth  = firstOfMonth.plusMonths(1).minusDays(1);

        for (GrocerySubscription sub : subscriptionList) {
            greenPoints += sub.getBonusPoints() != null ? sub.getBonusPoints() : 0;
            double discount      = sub.getDiscountAmount() != null ? sub.getDiscountAmount() : 0.0;
            double itemsTotal    = computeItemsTotal(sub);
            double baseFivePercent = itemsTotal * 0.05;
            if (!(discount <= 0 && baseFivePercent <= 0)) {
                try {
                    LocalDate base = null;
                    String nextStr = sub.getNextDeliveryDate();
                    if (nextStr != null && !nextStr.isEmpty())
                        base = LocalDate.parse(nextStr, DATE_FORMATTER);
                    if (base == null && sub.getStartDate() != null && !sub.getStartDate().isEmpty())
                        base = LocalDate.parse(sub.getStartDate(), DATE_FORMATTER);
                    int interval = sub.getIntervalDays() != null && sub.getIntervalDays() > 0 ? sub.getIntervalDays() : 7;
                    if (base != null) {
                        int occ = countOccurrencesInMonth(base, interval, firstOfMonth, lastOfMonth);
                        monthlySavings += occ * (discount + baseFivePercent);
                    } else {
                        monthlySavings += (discount + baseFivePercent);
                    }
                } catch (Exception ignored) {
                    monthlySavings += (discount + baseFivePercent);
                }
            }
            String status = sub.getStatus() != null ? sub.getStatus().toUpperCase(Locale.getDefault()) : "ACTIVE";
            if ("ACTIVE".equals(status)) {
                try {
                    String nextStr = sub.getNextDeliveryDate();
                    if (nextStr != null && !nextStr.isEmpty()) {
                        LocalDate nextDate = LocalDate.parse(nextStr, DATE_FORMATTER);
                        long days = java.time.temporal.ChronoUnit.DAYS.between(today, nextDate);
                        if (days >= 0 && days < closestGap) {
                            closestGap = days;
                            reminderMessage = String.format(Locale.getDefault(),
                                    "%s may run out in %d day%s",
                                    sub.getName() != null ? sub.getName() : "An essential",
                                    days, days == 1 ? "" : "s");
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        tvTotalSavings.setText(String.format(Locale.getDefault(), "Rs. %,.2f", monthlySavings));
        tvSmartReminder.setText(reminderMessage);
        tvGreenPointsBalance.setText(String.format(Locale.getDefault(), "%d pts", greenPoints));
    }

    private void showEditBottomSheet(GrocerySubscription sub) {
        try {
            EditSubscriptionSheet sheet = new EditSubscriptionSheet(sub, this::loadSubscriptions);
            if (isAdded() && getChildFragmentManager() != null) {
                sheet.show(getChildFragmentManager(), "EditSchedule");
            }
        } catch (Exception ex) {
            android.util.Log.e("SubscriptionFragment", "Failed to show EditSubscriptionSheet", ex);
        }
    }

    // ── CHANGED: handleTargetSubscriptionIfAny ────────────────────────────────
    // auto_open_review=true නම් edit sheet skip කර directly Review sheet open කරනවා
    private void handleTargetSubscriptionIfAny() {
        if (targetSubscriptionHandled || !isAdded()) return;
        Intent intent = requireActivity().getIntent();
        if (intent == null) return;

        String  target         = intent.getStringExtra("target_subscription_id");
        boolean autoOpenReview = intent.getBooleanExtra("auto_open_review", false);

        if (target == null || target.trim().isEmpty()) return;

        for (int i = 0; i < subscriptionList.size(); i++) {
            GrocerySubscription s = subscriptionList.get(i);
            if (s == null) continue;

            boolean matched = target.equals(s.getFirestoreId());
            if (!matched) {
                try {
                    long numeric = Long.parseLong(target);
                    matched = s.getId() != null && s.getId() == numeric;
                } catch (NumberFormatException ignored) {}
            }

            if (matched) {
                targetSubscriptionHandled = true;
                final GrocerySubscription matchedSub = s;
                rvSubscriptions.scrollToPosition(i);

                if (autoOpenReview) {
                    // Notification tap — directly Review sheet open කරනවා
                    new Handler(Looper.getMainLooper()).postDelayed(
                            () -> showReviewAndPayDialog(matchedSub), 250);
                } else {
                    // Manual deep link — edit sheet then review
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        showEditBottomSheet(matchedSub);
                        new Handler(Looper.getMainLooper()).postDelayed(
                                () -> showReviewAndPayDialog(matchedSub), 400);
                    }, 200);
                }
                return;
            }
        }

        // If we didn't find a numeric MySQL id match and the target looks like a Firestore id
        // (contains non-digit characters) then fetch it directly from Firestore and open review.
        String trimmed = target.trim();
        if (!targetSubscriptionHandled && autoOpenReview && !trimmed.matches("\\d+")) {
            targetSubscriptionHandled = true;
            fetchAndOpenSubscriptionFromFirestore(trimmed);
        }
    }

    // ── CHANGED: showReviewAndPayDialog ──────────────────────────────────────
    // AlertDialog REMOVED — SubscriptionReviewFragment (Bottom Sheet) use කරනවා
    private void showReviewAndPayDialog(GrocerySubscription sub) {
        if (sub == null || !isAdded()) return;
        try {
            SubscriptionReviewFragment sheet = SubscriptionReviewFragment.newInstance(
                    sub.getFirestoreId(),
                    sub.getId() != null ? sub.getId() : -1L,
                    sub.getName(),
                    sub.getNextDeliveryDate(),
                    sub.getTotalAmount() != null ? sub.getTotalAmount() : 0.0
            );
            if (isAdded() && getChildFragmentManager() != null) {
                sheet.show(getChildFragmentManager(), "SubscriptionReview");
            }
        } catch (Exception ex) {
            android.util.Log.e("SubscriptionFragment", "Failed to show SubscriptionReviewFragment", ex);
        }
    }
    // processSubscriptionPayment() REMOVED — SubscriptionReviewFragment handles payment

    private void skipNextDelivery(@Nullable GrocerySubscription sub) {
        if (sub == null || sub.getFirestoreId() == null) return;
        if (sub.isSkipNext()) {
            CustomToast.showInfo(getContext(), "Upcoming delivery already skipped");
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("skip_next", true);
        updates.put("updatedAt", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        db.collection("grocery_subscriptions")
                .document(sub.getFirestoreId())
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    sub.setSkipNext(true);
                    syncSkipNextToMySql(sub, true);
                    loadSubscriptions();
                    CustomToast.showSuccess(getContext(), "Upcoming delivery skipped");
                })
                .addOnFailureListener(e -> CustomToast.showError(getContext(), "Unable to skip upcoming delivery"));
    }

    private void togglePauseResume(@Nullable GrocerySubscription sub) {
        if (sub == null || sub.getFirestoreId() == null) return;
        String current = sub.getStatus() != null ? sub.getStatus().toUpperCase(Locale.getDefault()) : "ACTIVE";
        String next = "PAUSED".equals(current) ? "ACTIVE" : "PAUSED";
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", next);
        updates.put("updatedAt", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        if ("ACTIVE".equals(next)) {
            String rawNextDate = sub.getNextDeliveryDate();
            LocalDate nextDelivery = null;
            try {
                if (rawNextDate != null && !rawNextDate.isEmpty())
                    nextDelivery = LocalDate.parse(rawNextDate, DATE_FORMATTER);
            } catch (Exception ignored) {}
            LocalDate today = LocalDate.now();
            if (nextDelivery == null || !nextDelivery.isAfter(today)) {
                int intervalDays = sub.getIntervalDays() != null && sub.getIntervalDays() > 0 ? sub.getIntervalDays() : 7;
                LocalDate recalculated = today.plusDays(intervalDays);
                updates.put("next_delivery_date", recalculated.format(DATE_FORMATTER));
                sub.setNextDeliveryDate(recalculated.format(DATE_FORMATTER));
            }
        }
        db.collection("grocery_subscriptions")
                .document(sub.getFirestoreId())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    syncStatusToMySql(sub, next);
                    loadSubscriptions();
                    CustomToast.showSuccess(getContext(), "ACTIVE".equals(next) ? "Subscription resumed" : "Subscription paused");
                })
                .addOnFailureListener(e -> CustomToast.showError(getContext(), "Unable to update subscription"));
    }

    private void cancelSubscription(@Nullable GrocerySubscription sub) {
        if (sub == null || sub.getFirestoreId() == null) return;
        db.collection("grocery_subscriptions")
                .document(sub.getFirestoreId())
                .update("status", "CANCELLED")
                .addOnSuccessListener(unused -> {
                    syncStatusToMySql(sub, "CANCELLED");
                    loadSubscriptions();
                    CustomToast.showSuccess(getContext(), "Subscription cancelled");
                })
                .addOnFailureListener(e -> CustomToast.showError(getContext(), "Unable to cancel subscription"));
    }

    private void syncStatusToMySql(@NonNull GrocerySubscription sub, @NonNull String status) {
        if (sub.getId() == null) return;
        apiService.updateSubscriptionStatus(sub.getId(), status).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {}
            @Override public void onFailure(Call<Void> call, Throwable t) {}
        });
    }

    private void syncSkipNextToMySql(@NonNull GrocerySubscription sub, boolean skip) {
        if (sub.getId() == null) return;
        List<SubscriptionItemUpsertRequest> requestItems = new ArrayList<>();
        if (sub.getItems() != null) {
            for (GrocerySubscriptionItem item : sub.getItems()) {
                requestItems.add(SubscriptionItemUpsertRequest.builder()
                        .productId(item.getProductId())
                        .variantId(item.getVariantId())
                        .name(item.getName())
                        .variantName(item.getVariantName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .imageUrl(item.getImageUrl())
                        .build());
            }
        }
        SubscriptionSaveRequest payload = SubscriptionSaveRequest.builder()
                .id(sub.getId())
                .firebaseUid(sub.getFirebaseUid())
                .name(sub.getName())
                .frequency(sub.getFrequency())
                .deliveryDay(sub.getDeliveryDay())
                .deliveryTimeSlot(sub.getDeliveryTimeSlot())
                .nextDeliveryDate(sub.getNextDeliveryDate())
                .status(sub.getStatus())
                .skipNextDelivery(skip)
                .firestoreId(sub.getFirestoreId())
                .itemCount(requestItems.size())
                .items(new ArrayList<>())
                .build();
        apiService.saveGrocerySubscription(payload).enqueue(new Callback<GrocerySubscription>() {
            @Override public void onResponse(Call<GrocerySubscription> call, Response<GrocerySubscription> response) {
                if (!response.isSuccessful()) android.util.Log.e("SUB_SYNC", "Skip sync failed: HTTP " + response.code());
            }
            @Override public void onFailure(Call<GrocerySubscription> call, Throwable t) {
                android.util.Log.e("SUB_SYNC", "Skip network error: " + t.getMessage());
            }
        });
        apiService.updateSkipNext(sub.getId(), skip).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {
                if (!response.isSuccessful()) android.util.Log.e("SUB_SYNC", "updateSkipNext failed: HTTP " + response.code());
            }
            @Override public void onFailure(Call<Void> call, Throwable t) {
                android.util.Log.e("SUB_SYNC", "updateSkipNext network error: " + t.getMessage());
            }
        });
    }

    private double computeItemsTotal(GrocerySubscription sub) {
        if (sub == null || sub.getItems() == null) return 0.0;
        double sum = 0.0;
        for (GrocerySubscriptionItem it : sub.getItems()) {
            double price = it.getUnitPrice() != null ? it.getUnitPrice() : 0.0;
            int qty = it.getQuantity() != null ? it.getQuantity() : 1;
            sum += price * qty;
        }
        return sum;
    }

    private double computeItemsTotalFromRaw(Object raw) {
        if (!(raw instanceof List<?>)) return 0.0;
        double sum = 0.0;
        for (Object o : (List<?>) raw) {
            if (!(o instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            Double unit = readDouble(m.get("unitPrice"));
            Integer qty = readInt(m.get("quantity"), 1);
            sum += (unit != null ? unit : 0.0) * (qty != null ? qty : 1);
        }
        return sum;
    }

    @NonNull
    private String readString(@Nullable Object value, @NonNull String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private int readInt(@Nullable Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt(((String) value).trim()); }
            catch (NumberFormatException ignored) { return fallback; }
        }
        return fallback;
    }

    private boolean readBoolean(@Nullable Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        if (value instanceof String) {
            String normalized = ((String) value).trim();
            return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
        }
        return false;
    }

    @NonNull
    private List<GrocerySubscriptionItem> parseItems(@Nullable Object raw) {
        List<GrocerySubscriptionItem> out = new ArrayList<>();
        if (raw == null) return out;

        // Support both array/list shapes and map/object shapes (some documents store items as a map)
        Iterable<?> iterable = null;
        if (raw instanceof List<?>) {
            iterable = (List<?>) raw;
        } else if (raw instanceof Map) {
            // map of id -> item-object
            iterable = ((Map<?, ?>) raw).values();
        }
        if (iterable == null) return out;

        for (Object obj : iterable) {
            if (!(obj instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) obj;
            GrocerySubscriptionItem parsed = GrocerySubscriptionItem.builder()
                    .productId(readLong(item.get("productId")))
                    .variantId(readLong(item.get("variantId")))
                    .name(readString(item.get("name"), "Item"))
                    .variantName(readString(item.get("variantName"), ""))
                    .quantity(readInt(item.get("quantity"), 1))
                    .unitPrice(readDouble(item.get("unitPrice")))
                    .imageUrl(readString(item.get("imageUrl"), ""))
                    .build();
            out.add(parsed);
        }
        return out;
    }

    @Nullable
    private Long readLong(@Nullable Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try { return Long.parseLong(((String) value).trim()); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    @Nullable
    private Double readDouble(@Nullable Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try { return Double.parseDouble(((String) value).trim()); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    // Add method to fetch a subscription document from Firestore and open the review dialog
    private void fetchAndOpenSubscriptionFromFirestore(@NonNull String firestoreId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("grocery_subscriptions")
                .document(firestoreId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    try {
                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            // Manually map fields to avoid Firestore automatic mapping issues
                            GrocerySubscription sub = new GrocerySubscription();
                            sub.setFirestoreId(documentSnapshot.getId());
                            // id (MySQL numeric id)
                            sub.setId(readLong(documentSnapshot.get("id")));
                            sub.setFirebaseUid(readString(documentSnapshot.get("firebaseUid"), readString(documentSnapshot.get("firebase_uid"), null)));
                            sub.setName(readString(documentSnapshot.get("name"), readString(documentSnapshot.get("title"), "")));
                            // start / next delivery with multiple key fallbacks
                            sub.setStartDate(readString(documentSnapshot.get("start_date"), readString(documentSnapshot.get("startDate"), "")));
                            sub.setNextDeliveryDate(readString(documentSnapshot.get("next_delivery_date"), readString(documentSnapshot.get("nextDeliveryDate"), "")));
                            sub.setDeliveryDay(readString(documentSnapshot.get("deliveryDay"), readString(documentSnapshot.get("delivery_day"), "Monday")));
                            sub.setIntervalDays(readInt(documentSnapshot.get("intervalDays"), readInt(documentSnapshot.get("interval_days"), 7)));
                            sub.setBonusPoints(readInt(documentSnapshot.get("bonusPoints"), readInt(documentSnapshot.get("bonus_points"), 0)));
                            sub.setSkipNext(readBoolean(documentSnapshot.get("skip_next")) || readBoolean(documentSnapshot.get("skipNext")));
                            List<GrocerySubscriptionItem> parsedItems = parseItems(documentSnapshot.get("items"));
                            sub.setItems(parsedItems);
                            sub.setItemCount(parsedItems != null ? parsedItems.size() : 0);
                            sub.setStatus(readString(documentSnapshot.get("status"), "ACTIVE"));
                            Double total = readDouble(documentSnapshot.get("totalAmount"));
                            if (total == null) total = readDouble(documentSnapshot.get("total_amount"));
                            sub.setTotalAmount(total);

                            // Open the review dialog safely on UI thread
                            if (isAdded() && getActivity() != null) {
                                requireActivity().runOnUiThread(() -> {
                                    try { showReviewAndPayDialog(sub); }
                                    catch (Exception ex) { android.util.Log.e("SubscriptionFragment", "Error showing review dialog", ex); }
                                });
                            } else {
                                try { showReviewAndPayDialog(sub); }
                                catch (Exception ex) { android.util.Log.e("SubscriptionFragment", "Error showing review dialog", ex); }
                            }
                        }
                    } catch (Exception ex) {
                        android.util.Log.e("SubscriptionFragment", "Failed to fetch/convert subscription doc: " + firestoreId, ex);
                    }
                })
                .addOnFailureListener(e -> {
                    // Log or show error
                    android.util.Log.e("SubscriptionFragment", "Failed to load subscription from Firestore: " + firestoreId, e);
                });
    }
}


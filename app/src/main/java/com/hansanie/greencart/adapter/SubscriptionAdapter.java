package com.hansanie.greencart.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.hansanie.greencart.R;
import com.hansanie.greencart.model.GrocerySubscription;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public class SubscriptionAdapter extends RecyclerView.Adapter<SubscriptionAdapter.ViewHolder> {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM", Locale.getDefault());

    private final Context context;
    private final List<GrocerySubscription> list;
    private final OnSubscriptionActionListener actionListener;

    public interface OnSubscriptionActionListener {
        void onModifyItems(GrocerySubscription sub);
        void onSkipDelivery(GrocerySubscription sub);
        void onPauseResume(GrocerySubscription sub);
        void onCancel(GrocerySubscription sub);
        // New: request to review & pay for this subscription
        void onReviewAndPay(GrocerySubscription sub);
    }

    public SubscriptionAdapter(Context context, List<GrocerySubscription> list, OnSubscriptionActionListener actionListener) {
        this.context = context;
        this.list = list;
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_subscription, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GrocerySubscription sub = list.get(position);
        String statusValue = sub.getStatus() != null ? sub.getStatus().toUpperCase(Locale.getDefault()) : "ACTIVE";
        boolean isPaused = "PAUSED".equals(statusValue);
        boolean isSkipped = sub.isSkipNext();

        if (holder.name != null) holder.name.setText(sub.getName() != null ? sub.getName() : "");
        if (holder.freq != null) {
            String frequency = prettyFrequency(sub.getFrequency());
            String day = sub.getDeliveryDay() != null ? sub.getDeliveryDay() : "Monday";
            String time = sub.getDeliveryTimeSlot() != null ? sub.getDeliveryTimeSlot() : "7:00 AM";
            holder.freq.setText(frequency + " • " + day + " • " + time);
        }
        if (holder.status != null) {
            if (isPaused) {
                holder.status.setText("Paused • Resume anytime");
            } else if (isSkipped) {
                holder.status.setText("Upcoming delivery skipped");
            } else {
                LocalDate nextDeliveryDate = resolveNextDeliveryDate(sub);
                holder.status.setText(nextDeliveryDate != null
                        ? "Next delivery: " + nextDeliveryDate
                        : "Active");
            }
        }
        if (holder.total != null) holder.total.setText(sub.getTotalAmount() != null
                ? String.format(java.util.Locale.getDefault(), "Rs. %.2f", sub.getTotalAmount()) : "Rs. 0.00");

        if (holder.total != null) {
            holder.total.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onReviewAndPay(sub);
            });
        }

        LocalDate badgeDate = resolveDisplayDeliveryDate(sub);
        String month = badgeDate != null ? badgeDate.format(MONTH_FORMATTER).toUpperCase(Locale.getDefault()) : "--";
        String day = badgeDate != null ? String.valueOf(badgeDate.getDayOfMonth()) : "--";

        if (holder.month != null) holder.month.setText(month);
        if (holder.day != null) holder.day.setText(day);

        if (holder.items != null) {
            int displayCount = 0;
            if (sub.getItemCount() != null && sub.getItemCount() > 0) {
                displayCount = sub.getItemCount();
            } else if (sub.getItems() != null) {
                displayCount = sub.getItems().size();
            }
            holder.items.setText(displayCount + " items");
        }

        if (holder.btnSkip != null) {
            holder.btnSkip.setText(isPaused ? "Resume" : "Pause");
        }

        if (holder.btnSkipDelivery != null) {
            holder.btnSkipDelivery.setVisibility(isPaused ? View.GONE : View.VISIBLE);
            holder.btnSkipDelivery.setEnabled(!isSkipped);
            holder.btnSkipDelivery.setText(isSkipped ? "Delivery Skipped ✓" : "Skip Next Delivery");
            holder.btnSkipDelivery.setOnClickListener(v -> {
                if (actionListener != null && !isSkipped) {
                    actionListener.onSkipDelivery(sub);
                }
            });
        }

        if (holder.btnEdit != null) {
            holder.btnEdit.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onModifyItems(sub);
                }
            });
        }

        if (holder.btnSkip != null) {
            holder.btnSkip.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onPauseResume(sub);
                }
            });
        }

        if (holder.btnCancel != null) {
            holder.btnCancel.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onCancel(sub);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, freq, status, total, month, day, items;
        MaterialButton btnSkip, btnEdit, btnCancel, btnSkipDelivery;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvSubName);
            freq = itemView.findViewById(R.id.tvFrequency);
            status = itemView.findViewById(R.id.tvSubStatus);
            total = itemView.findViewById(R.id.tvSubscriptionTotal);
            month = itemView.findViewById(R.id.tvDeliveryMonth);
            day = itemView.findViewById(R.id.tvDeliveryDay);

            items = itemView.findViewById(R.id.tvItemCount);

            btnSkipDelivery = itemView.findViewById(R.id.btnSkipDelivery);
            btnSkip = itemView.findViewById(R.id.btnSkipNext);
            btnEdit = itemView.findViewById(R.id.btnEditSchedule);
            btnCancel = itemView.findViewById(R.id.btnViewDetails);
        }
    }

    @NonNull
    private String prettyFrequency(String frequency) {
        if (frequency == null) {
            return "Weekly";
        }
        String normalized = frequency.trim().toUpperCase(Locale.getDefault());
        if ("BI_WEEKLY".equals(normalized) || "BIWEEKLY".equals(normalized)) {
            return "Every 2 Weeks";
        }
        return "Weekly";
    }

    private LocalDate resolveNextDeliveryDate(@NonNull GrocerySubscription sub) {
        LocalDate parsed = parseIsoDate(sub.getNextDeliveryDate());
        if (parsed != null) {
            return parsed;
        }

        String deliveryDay = sub.getDeliveryDay() != null ? sub.getDeliveryDay().trim() : "";
        if (deliveryDay.isEmpty()) {
            return null;
        }

        DayOfWeek targetDay;
        try {
            targetDay = DayOfWeek.valueOf(deliveryDay.toUpperCase(Locale.getDefault()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        int intervalDays = sub.getIntervalDays() != null && sub.getIntervalDays() > 0 ? sub.getIntervalDays() : 7;
        LocalDate today = LocalDate.now();
        LocalDate candidate = today;
        while (candidate.getDayOfWeek() != targetDay) {
            candidate = candidate.plusDays(1);
        }
        if (!candidate.isAfter(today)) {
            candidate = candidate.plusDays(Math.max(7, intervalDays));
        }
        return candidate;
    }

    @Nullable
    private LocalDate resolveDisplayDeliveryDate(@NonNull GrocerySubscription sub) {
        LocalDate nextDate = resolveNextDeliveryDate(sub);
        if (nextDate == null) {
            return null;
        }
        if (!sub.isSkipNext()) {
            return nextDate;
        }

        int intervalDays = sub.getIntervalDays() != null && sub.getIntervalDays() > 0 ? sub.getIntervalDays() : 7;
        return nextDate.plusDays(intervalDays);
    }

    private LocalDate parseIsoDate(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
package com.hansanie.greencart.adapter;

import android.content.Intent;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hansanie.greencart.R;
import com.hansanie.greencart.activity.MainActivity;
import com.hansanie.greencart.model.NotificationItem;
import com.hansanie.greencart.util.NotificationHelper;
import com.hansanie.greencart.worker.SubscriptionReminderWorker;
import com.hansanie.greencart.activity.SubscriptionActionActivity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotifViewHolder> {

    private final List<NotificationItem> list;

    public NotificationAdapter(List<NotificationItem> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public NotifViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new NotifViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_notification, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull NotifViewHolder holder, int position) {
        NotificationItem item = list.get(position);
        holder.tvTitle.setText(item.getTitle() != null ? item.getTitle() : "GreenCart");
        holder.tvBody.setText(item.getBody() != null ? item.getBody() : "");
        holder.tvTime.setText(DateUtils.getRelativeTimeSpanString(
                item.getTimestamp(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));

        // Dim read notifications slightly
        holder.itemView.setAlpha(item.isRead() ? 0.6f : 1.0f);

        holder.itemView.setOnClickListener(v -> {
            // ── Mark individual item as read on click ────────────────────────
            if (!item.isRead()) {
                item.setRead(true);
                holder.itemView.setAlpha(0.6f);
                com.hansanie.greencart.util.AppExecutors.DB.execute(() -> {
                    com.hansanie.greencart.database.AppDatabase
                            .getInstance(v.getContext().getApplicationContext())
                            .notificationDao()
                            .markReadById(item.getId());
                    // If payload contains a support message id, persist it as dismissed so it won't re-notify
                    String pl = item.getPayload();
                    if (pl != null && pl.contains("messageId=")) {
                        try {
                            int idx = pl.indexOf("messageId=");
                            int start = idx + "messageId=".length();
                            int end = pl.indexOf(';', start);
                            String mid = end > start ? pl.substring(start, end) : pl.substring(start);
                            if (mid != null && !mid.isEmpty()) {
                                com.hansanie.greencart.util.NotificationHelper.addDismissedSupportMessageId(
                                        v.getContext().getApplicationContext(), mid);
                            }
                        } catch (Exception ignored) {}
                    }
                    // Badge count update broadcast (use application context)
                    android.content.Intent broadcast = new android.content.Intent(
                            com.hansanie.greencart.network.MyFirebaseMessagingService
                                    .ACTION_NOTIFICATION_COUNT_UPDATED);
                    androidx.localbroadcastmanager.content.LocalBroadcastManager
                            .getInstance(v.getContext().getApplicationContext())
                            .sendBroadcast(broadcast);
                });
            }
            // ─────────────────────────────────────────────────────────────────

            String dest    = item.getDestination();
            String payload = item.getPayload();
            Intent intent;

            if (NotificationHelper.DEST_SUBSCRIPTIONS.equals(dest)) {
                intent = new Intent(v.getContext(), SubscriptionActionActivity.class);
                Map<String, String> map = parsePayload(payload);
                if (map.containsKey("firestoreId"))
                    intent.putExtra(SubscriptionReminderWorker.EXTRA_FIRESTORE_ID, map.get("firestoreId"));
                if (map.containsKey("mysqlId")) {
                    try {
                        intent.putExtra(SubscriptionReminderWorker.EXTRA_MYSQL_ID,
                                Long.parseLong(map.get("mysqlId")));
                    } catch (Exception ignored) {}
                }
                if (map.containsKey("name"))
                    intent.putExtra(SubscriptionReminderWorker.EXTRA_SUB_NAME, map.get("name"));
                if (map.containsKey("nextDate"))
                    intent.putExtra(SubscriptionReminderWorker.EXTRA_NEXT_DATE, map.get("nextDate"));
                if (map.containsKey("totalAmount")) {
                    try {
                        intent.putExtra(SubscriptionReminderWorker.EXTRA_TOTAL_AMOUNT,
                                Double.parseDouble(map.get("totalAmount")));
                    } catch (Exception ignored) {}
                }

            } else if (NotificationHelper.DEST_ORDERS.equals(dest)) {
                intent = new Intent(v.getContext(), MainActivity.class);
                intent.putExtra(NotificationHelper.EXTRA_OPEN_DESTINATION, NotificationHelper.DEST_ORDERS);
                Map<String, String> map = parsePayload(payload);
                if (map.containsKey("orderId"))
                    intent.putExtra(NotificationHelper.EXTRA_ORDER_ID, map.get("orderId"));

            } else if (NotificationHelper.DEST_SUPPORT_CHAT.equals(dest)) {
                intent = new Intent(v.getContext(), MainActivity.class);
                intent.putExtra(NotificationHelper.EXTRA_OPEN_DESTINATION, NotificationHelper.DEST_SUPPORT_CHAT);
                Map<String, String> map = parsePayload(payload);
                if (map.containsKey("conversationId"))
                    intent.putExtra(NotificationHelper.EXTRA_CONVERSATION_ID, map.get("conversationId"));
                if (map.containsKey("orderId"))
                    intent.putExtra(NotificationHelper.EXTRA_ORDER_ID, map.get("orderId"));
                if (map.containsKey("supportPhone"))
                    intent.putExtra(NotificationHelper.EXTRA_SUPPORT_PHONE, map.get("supportPhone"));

            } else {
                intent = new Intent(v.getContext(), MainActivity.class);
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class NotifViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvBody, tvTime;

        NotifViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNotifTitle);
            tvBody  = itemView.findViewById(R.id.tvNotifBody);
            tvTime  = itemView.findViewById(R.id.tvNotifTime);
        }
    }

    private Map<String, String> parsePayload(String payload) {
        Map<String, String> map = new HashMap<>();
        if (TextUtils.isEmpty(payload)) return map;
        String[] parts = payload.split(";");
        for (String p : parts) {
            if (p == null || p.trim().isEmpty()) continue;
            int idx = p.indexOf('=');
            if (idx <= 0) continue;
            String k = p.substring(0, idx).trim();
            String v = p.substring(idx + 1).trim();
            map.put(k, v);
        }
        return map;
    }
}


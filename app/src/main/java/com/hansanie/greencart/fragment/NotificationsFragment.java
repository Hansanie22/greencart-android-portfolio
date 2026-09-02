package com.hansanie.greencart.fragment;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hansanie.greencart.R;
import com.hansanie.greencart.adapter.NotificationAdapter;
import com.hansanie.greencart.database.AppDatabase;
import com.hansanie.greencart.model.NotificationItem;
import com.hansanie.greencart.network.MyFirebaseMessagingService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class NotificationsFragment extends Fragment {

    private RecyclerView rvNotifications;
    private TextView tvClearAll, tvItemCount;
    private LinearLayout layoutEmpty;
    private NotificationAdapter adapter;
    private List<NotificationItem> notificationList = new ArrayList<>();
    // Prevent rapid repeated loads when fragment opened via notification click or rapid broadcasts
    private volatile boolean isLoading = false;
    private volatile long lastLoadTime = 0L;

    public NotificationsFragment() {
    }

    private final android.content.BroadcastReceiver notificationReceiver =
            new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context context, android.content.Intent intent) {
                    // Only reload when fragment is visible; guard against rapid repeated broadcasts
                    if (isAdded() && isVisible()) {
                        long now = System.currentTimeMillis();
                        if (isLoading) return; // already loading
                        // ignore broadcasts that come within 2s of last load to avoid duplicate reloads
                        if ((now - lastLoadTime) < 2000L) return;
                        loadNotifications();
                    }
                }
            };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // View Initialization
        rvNotifications = view.findViewById(R.id.rvNotifications);
        tvClearAll = view.findViewById(R.id.tvClearAll);
        tvItemCount = view.findViewById(R.id.tvItemCount);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);

        rvNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NotificationAdapter(notificationList);
        rvNotifications.setAdapter(adapter);

        loadNotifications();

        // Clear All ලොජික් එක
        tvClearAll.setOnClickListener(v -> clearAllNotifications());
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                notificationReceiver,
                new android.content.IntentFilter(MyFirebaseMessagingService.ACTION_NOTIFICATION_COUNT_UPDATED)
        );
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(notificationReceiver);
        } catch (Exception ignored) {
        }
    }

    private void loadNotifications() {
        // Prevent concurrent loads
        if (isLoading) return;
        isLoading = true;
        lastLoadTime = System.currentTimeMillis();

        com.hansanie.greencart.util.AppExecutors.DB.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext().getApplicationContext());

            // Mark all read first so the subsequent query returns correct read state
            db.notificationDao().markAllRead();
            List<NotificationItem> items = db.notificationDao().getAll();

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    notificationList.clear();
                    notificationList.addAll(items);
                    adapter.notifyDataSetChanged();
                    updateUIState();

                    // Badge 0 — notify listeners after DB update
                    broadcastCountUpdate();
                    // mark completion
                    isLoading = false;
                    lastLoadTime = System.currentTimeMillis();
                });
            } else {
                // Ensure flags are reset even if fragment is detached
                isLoading = false;
                lastLoadTime = System.currentTimeMillis();
            }
        });
    }

    private void clearAllNotifications() {
        if (notificationList.isEmpty()) return;
        // Save dismissed identifiers so notifications won't reappear after clearing
        android.content.Context ctx = requireContext().getApplicationContext();
        for (NotificationItem n : notificationList) {
            if (n.getOfferId() != null) {
                com.hansanie.greencart.util.NotificationHelper.addDismissedOffer(ctx, n.getOfferId());
            } else {
                // If payload contains support message id, save it to dismissed support ids
                String payload = n.getPayload();
                if (payload != null && payload.contains("messageId=")) {
                    try {
                        int idx = payload.indexOf("messageId=");
                        int start = idx + "messageId=".length();
                        int end = payload.indexOf(';', start);
                        String mid = end > start ? payload.substring(start, end) : payload.substring(start);
                        if (mid != null && !mid.isEmpty()) {
                            com.hansanie.greencart.util.NotificationHelper.addDismissedSupportMessageId(ctx, mid);
                            continue;
                        }
                    } catch (Exception ignored) { }
                }
                com.hansanie.greencart.util.NotificationHelper.addDismissedMessage(ctx, n.getTitle(), n.getBody());
            }
        }

        com.hansanie.greencart.util.AppExecutors.DB.execute(() -> {
            AppDatabase.getInstance(requireContext().getApplicationContext()).notificationDao().deleteAll();

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    notificationList.clear();
                    adapter.notifyDataSetChanged();
                    updateUIState();
                    // Clear done — update badge
                    broadcastCountUpdate();
                });
            }
        });
    }

    private void broadcastCountUpdate() {
        if (getContext() != null) {
            LocalBroadcastManager.getInstance(requireContext().getApplicationContext())
                    .sendBroadcast(new Intent(
                            MyFirebaseMessagingService.ACTION_NOTIFICATION_COUNT_UPDATED));
        }
    }

    private void updateUIState() {
        if (!isAdded()) return;

        int count = notificationList.size();
        tvItemCount.setText(count + " notification" + (count != 1 ? "s" : ""));

        if (count == 0) {
            rvNotifications.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
            tvClearAll.setVisibility(View.GONE); // Notification නැතිවිට Clear All සඟවන්න
        } else {
            rvNotifications.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
            tvClearAll.setVisibility(View.VISIBLE);
        }
    }

}
package com.hansanie.greencart.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.hansanie.greencart.util.NotificationHelper;
import com.hansanie.greencart.worker.SubscriptionReminderWorker;

public class SubscriptionActionActivity extends AppCompatActivity {

    private static final String TAG = "SubscriptionActionActivity";

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Intent in = getIntent();
            String firestoreId   = null;
            long   mysqlId       = -1L;
            String subName       = null;
            String nextDate      = null;
            double totalAmount   = 0.0;

            if (in != null) {
                firestoreId  = in.getStringExtra(SubscriptionReminderWorker.EXTRA_FIRESTORE_ID);
                mysqlId      = in.getLongExtra(SubscriptionReminderWorker.EXTRA_MYSQL_ID, -1L);
                subName      = in.getStringExtra(SubscriptionReminderWorker.EXTRA_SUB_NAME);
                nextDate     = in.getStringExtra(SubscriptionReminderWorker.EXTRA_NEXT_DATE);
                totalAmount  = in.getDoubleExtra(SubscriptionReminderWorker.EXTRA_TOTAL_AMOUNT, 0.0);
            }

            // Log extracted subscription context for debugging when opened from notification
            Log.i(TAG, "Notification open - firestoreId=" + firestoreId
                    + ", mysqlId=" + mysqlId
                    + ", subName=" + subName
                    + ", nextDate=" + nextDate
                    + ", totalAmount=" + totalAmount);

            Intent main = new Intent(this, MainActivity.class);
            main.putExtra(NotificationHelper.EXTRA_OPEN_DESTINATION, NotificationHelper.DEST_SUBSCRIPTIONS);

            // Pass all subscription context so SubscriptionFragment can open the Review sheet directly
            if (firestoreId != null && !firestoreId.trim().isEmpty()) {
                main.putExtra("target_subscription_id", firestoreId.trim());
            } else if (mysqlId != -1L) {
                main.putExtra("target_subscription_id", String.valueOf(mysqlId));
            }
            if (firestoreId  != null) main.putExtra(SubscriptionReminderWorker.EXTRA_FIRESTORE_ID, firestoreId);
            if (mysqlId != -1L)       main.putExtra(SubscriptionReminderWorker.EXTRA_MYSQL_ID, mysqlId);
            if (subName   != null)    main.putExtra(SubscriptionReminderWorker.EXTRA_SUB_NAME, subName);
            if (nextDate  != null)    main.putExtra(SubscriptionReminderWorker.EXTRA_NEXT_DATE, nextDate);
            main.putExtra(SubscriptionReminderWorker.EXTRA_TOTAL_AMOUNT, totalAmount);
            // Signal that we want to auto-open the review bottom sheet
            main.putExtra("auto_open_review", true);

            main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(main);
            finish();
        }
    }

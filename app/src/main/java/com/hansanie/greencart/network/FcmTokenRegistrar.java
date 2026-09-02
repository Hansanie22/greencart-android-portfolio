package com.hansanie.greencart.network;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

public final class FcmTokenRegistrar {

    private static final String TAG = "FcmTokenRegistrar";

    private FcmTokenRegistrar() {
    }

    public static void syncToken(@NonNull Context context, @NonNull String firebaseUid) {
        if (TextUtils.isEmpty(firebaseUid)) {
            return;
        }

        Context appContext = context.getApplicationContext();
        appContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("firebase_uid", firebaseUid)
                .apply();

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.w(TAG, "Failed to fetch FCM token", task.getException());
                return;
            }

            String token = task.getResult();
            if (TextUtils.isEmpty(token)) {
                return;
            }

            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(firebaseUid)
                    .update("fcm_token", token)
                    .addOnFailureListener(e -> Log.w(TAG, "Firestore token update failed", e));

            FcmTokenSyncManager.syncTokenToMySql(firebaseUid, token);
        });
    }
}


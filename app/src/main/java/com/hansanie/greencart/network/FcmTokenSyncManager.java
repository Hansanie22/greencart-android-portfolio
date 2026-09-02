package com.hansanie.greencart.network;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class FcmTokenSyncManager {

    private static final String TAG = "FcmTokenSync";

    private FcmTokenSyncManager() {
    }

    public static void syncTokenToMySql(String uid, String token) {
        if (TextUtils.isEmpty(uid) || TextUtils.isEmpty(token)) {
            return;
        }

        RetrofitClient.getApiService().updateFcmToken(uid, token).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (!response.isSuccessful()) {
                    Log.w(TAG, "FCM token sync failed with code: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.w(TAG, "FCM token sync error: " + t.getMessage());
            }
        });
    }
}


package com.hansanie.greencart.fragment;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.hansanie.greencart.R;
import com.hansanie.greencart.activity.MainActivity;
import com.hansanie.greencart.model.User;
import com.hansanie.greencart.network.FcmTokenRegistrar;
import com.hansanie.greencart.network.RetrofitClient;
import com.hansanie.greencart.util.CustomToast;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VerifyFragment extends Fragment {

    private EditText otp1, otp2, otp3, otp4, otp5, otp6;
    private MaterialButton btnConfirm;
    private TextView tvResend;

    private String verificationId, phone, mode = "register";
    private String fName, lName, email;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Test numbers
    private final Map<String, String> testNumbersMap = new HashMap<String, String>() {{
        put("+94715607666", "231370");
        put("+94764253159", "112233");
        put("+94776849102", "654321");
        put("+94779303604", "196605");
        put("+94761624810", "200205");
    }};

    public VerifyFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_verify, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        otp1 = view.findViewById(R.id.otp1);
        otp2 = view.findViewById(R.id.otp2);
        otp3 = view.findViewById(R.id.otp3);
        otp4 = view.findViewById(R.id.otp4);
        otp5 = view.findViewById(R.id.otp5);
        otp6 = view.findViewById(R.id.otp6);
        btnConfirm = view.findViewById(R.id.btnConfirm);
        tvResend = view.findViewById(R.id.tvResend);

        if (getArguments() != null) {
            verificationId = getArguments().getString("vId");
            phone = getArguments().getString("phone");
            mode = getArguments().getString("mode", "register");
            fName = getArguments().getString("fName");
            lName = getArguments().getString("lName");
            email = getArguments().getString("email");
        }

        setupOTP();
        setupConfirmButton();
        setupResendOTP();
    }

    private void setupOTP() {
        otp1.addTextChangedListener(new GenericTextWatcher(otp1, otp2));
        otp2.addTextChangedListener(new GenericTextWatcher(otp2, otp3));
        otp3.addTextChangedListener(new GenericTextWatcher(otp3, otp4));
        otp4.addTextChangedListener(new GenericTextWatcher(otp4, otp5));
        otp5.addTextChangedListener(new GenericTextWatcher(otp5, otp6));
        otp6.addTextChangedListener(new GenericTextWatcher(otp6, null));
    }

    private void setupConfirmButton() {
        btnConfirm.setOnClickListener(v -> {
            String code = getOTP();
            if (code.length() < 6) {
                CustomToast.showWarning(getContext(), "Enter 6 digit OTP");
            } else {
                verifyCode(code);
            }
        });
    }

    private void setupResendOTP() {
        tvResend.setOnClickListener(v -> {
            resendOTP();
        });
    }

    private String getOTP() {
        return otp1.getText().toString() + otp2.getText().toString() +
                otp3.getText().toString() + otp4.getText().toString() +
                otp5.getText().toString() + otp6.getText().toString();
    }

    private void verifyCode(String code) {
        if (verificationId == null) {
            CustomToast.showError(getContext(), "Verification ID missing. Try again.");
            return;
        }

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String uid = mAuth.getCurrentUser().getUid();

                        // Token එක Update කිරීම (Register/Login දෙකටම පොදුයි)
                        updateFCMToken(uid);

                        if ("register".equals(mode)) {
                            saveUser(uid);
                        } else {
                            openMain();
                        }
                    } else {
                        CustomToast.showError(getContext(), "Invalid OTP");
                    }
                });
    }

    // 1. saveUser එක ඇතුළේදී Token එකත් එක්ක saveToMySQL call කරන්න
    private void saveUser(String uid) {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            String token = task.isSuccessful() ? task.getResult() : null;

            Map<String, Object> userMap = new HashMap<>();
            userMap.put("uid", uid);
            userMap.put("phone", phone);
            userMap.put("first_name", fName);
            userMap.put("last_name", lName);
            userMap.put("email", email);
            userMap.put("fcm_token", token);
            userMap.put("created_at", FieldValue.serverTimestamp());

            db.collection("users").document(uid)
                    .set(userMap)
                    .addOnSuccessListener(unused -> saveToMySQL(uid, token)) // මෙතනට token එක දුන්නා
                    .addOnFailureListener(e -> CustomToast.showError(getContext(), "Firebase Error: " + e.getMessage()));
        });
    }

    // 2. saveToMySQL මෙතඩ් එකට Token එක parameter එකක් ලෙස ගන්න
    private void saveToMySQL(String uid, String token) {
        User user = new User();
        user.setFirebaseUid(uid);
        user.setFirstName(fName);
        user.setLastName(lName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setStatus("active");
        user.setFcmToken(token); // දැන් MySQL වලටත් Token එක යනවා

        RetrofitClient.getApiService().registerUser(user).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (response.isSuccessful()) {
                    Log.d("MYSQL_SAVE", "Success");
                } else {
                    Log.e("MYSQL_SAVE", "Failed: " + response.code());
                }
                openMain();
            }
            @Override
            public void onFailure(Call<User> call, Throwable t) {
                Log.e("MYSQL_SAVE", "Error: " + t.getMessage());
                openMain();
            }
        });
    }

    private void openMain() {
        if (getActivity() != null) {
            startActivity(new Intent(getActivity(), MainActivity.class));
            getActivity().finish();
        }
    }

    private void resendOTP() {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
                phone,
                60,
                java.util.concurrent.TimeUnit.SECONDS,
                requireActivity(),
                new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onCodeSent(@NonNull String vId,
                                           @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        verificationId = vId;
                        String otpToShow = testNumbersMap.getOrDefault(phone, "123456");
                        showLocalNotification("Greencart OTP", "Your verification code is: " + otpToShow);
                        CustomToast.showSuccess(getContext(), "OTP Resent");
                    }

                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {}

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        CustomToast.showError(getContext(), "Resend failed: " + e.getMessage());
                    }
                });
    }

    private void showLocalNotification(String title, String message) {
        NotificationManager notificationManager = (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "otp_channel";

        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(requireContext(), 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "OTP Channel", NotificationManager.IMPORTANCE_HIGH);
            if (notificationManager != null) notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), channelId)
                .setSmallIcon(R.mipmap.ic_logo)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        if (notificationManager != null) notificationManager.notify(101, builder.build());
    }

    private class GenericTextWatcher implements TextWatcher {
        private EditText current, next;
        GenericTextWatcher(EditText current, EditText next) {
            this.current = current; this.next = next;
        }
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (s.length() == 1 && next != null) next.requestFocus();
        }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(Editable s) {}
    }

    private void updateFCMToken(String uid) {
        FcmTokenRegistrar.syncToken(requireContext().getApplicationContext(), uid);
    }
}
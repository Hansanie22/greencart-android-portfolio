package com.hansanie.greencart.fragment;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.hansanie.greencart.R;
import com.hansanie.greencart.activity.AuthActivity;
import com.hansanie.greencart.util.CustomToast;
import com.hbb20.CountryCodePicker;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RegisterFragment extends Fragment {

    private TextInputLayout tilFirstName, tilLastName, tilEmail, tilPhone;
    private EditText etFirstName, etLastName, etEmail, etRegPhone;
    private MaterialButton btnVerify;
    private TextView tvBackToLogin;
    private CountryCodePicker ccp;
    private FirebaseAuth mAuth;
    private static final int NOTIFICATION_PERMISSION_CODE = 101;

    private final Map<String, String> testNumbersMap = new HashMap<String, String>() {{
        put("+94715607666", "231370");
        put("+94764253159", "112233");
        put("+94776849102", "654321");
        put("+94779303604", "196605");
        put("+94761624810", "200205");
    }};

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mAuth = FirebaseAuth.getInstance();

        initViews(view);
        checkNotificationPermission();
        setupRealtimeValidations();

        btnVerify.setOnClickListener(v -> {
            if (validateInputs()) {
                startOTPProcess();
            }
        });

        tvBackToLogin.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });
    }

    private void initViews(View view) {
        tilFirstName = view.findViewById(R.id.tilFirstName);
        tilLastName = view.findViewById(R.id.tilLastName);
        tilEmail = view.findViewById(R.id.tilEmail);
        tilPhone = view.findViewById(R.id.tilPhone);
        etFirstName = view.findViewById(R.id.etFirstName);
        etLastName = view.findViewById(R.id.etLastName);
        etEmail = view.findViewById(R.id.etEmail);
        etRegPhone = view.findViewById(R.id.etRegPhone);
        btnVerify = view.findViewById(R.id.btnVerify);
        tvBackToLogin = view.findViewById(R.id.tvBackToLogin);
        ccp = view.findViewById(R.id.ccp);
        ccp.registerCarrierNumberEditText(etRegPhone);
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
            }
        }
    }

    private void startOTPProcess() {
        String phone = ccp.getFullNumberWithPlus();

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(getActivity())
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                    @Override
                    public void onCodeSent(@NonNull String verificationId,
                                           @NonNull PhoneAuthProvider.ForceResendingToken token) {

                        // Map එකෙන් OTP එක ගන්න, නැත්නම් default එකක් පෙන්වන්න
                        String otpToShow = testNumbersMap.getOrDefault(phone, "123456");

                        showLocalNotification("Greencart OTP", "Your verification code is: " + otpToShow);
                        moveToVerifyFragment(verificationId);
                    }

                    @Override
                    public void onVerificationCompleted(@NonNull com.google.firebase.auth.PhoneAuthCredential credential) {}

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        CustomToast.showErrorLong(getContext(), "Error: " + e.getMessage());
                        Log.e("AUTH_ERROR", e.getMessage());
                    }
                }).build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void showLocalNotification(String title, String message) {
        NotificationManager notificationManager = (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "otp_channel";

        Intent intent = new Intent(requireContext(), AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(requireContext(), 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "OTP Channel", NotificationManager.IMPORTANCE_HIGH);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), channelId)
                .setSmallIcon(R.mipmap.ic_logo)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        if (notificationManager != null) {
            notificationManager.notify(101, builder.build());
        }
    }

    private void moveToVerifyFragment(String vId) {
        Bundle bundle = new Bundle();
        bundle.putString("vId", vId);
        bundle.putString("fName", etFirstName.getText().toString().trim());
        bundle.putString("lName", etLastName.getText().toString().trim());
        bundle.putString("email", etEmail.getText().toString().trim());
        bundle.putString("phone", ccp.getFullNumberWithPlus());
        bundle.putString("mode", "register");

        VerifyFragment fragment = new VerifyFragment();
        fragment.setArguments(bundle);

        if (getActivity() instanceof AuthActivity) {
            ((AuthActivity) getActivity()).switchFragment(fragment);
        }
    }

    private void setupRealtimeValidations() {
        etFirstName.addTextChangedListener(new SimpleTextWatcher(tilFirstName));
        etLastName.addTextChangedListener(new SimpleTextWatcher(tilLastName));
        etEmail.addTextChangedListener(new SimpleTextWatcher(tilEmail));
        etRegPhone.addTextChangedListener(new SimpleTextWatcher(tilPhone));
    }

    private boolean validateInputs() {
        boolean isValid = true;
        if (etFirstName.getText().toString().trim().isEmpty()) {
            tilFirstName.setError("First name required");
            isValid = false;
        }
        if (etLastName.getText().toString().trim().isEmpty()) {
            tilLastName.setError("Last name required");
            isValid = false;
        }
        String email = etEmail.getText().toString().trim();
        if (!email.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Please enter valid email");
            isValid = false;
        }
        if (etRegPhone.getText().toString().trim().length() < 9) {
            tilPhone.setError("Enter valid phone number");
            isValid = false;
        }
        return isValid;
    }

    private class SimpleTextWatcher implements TextWatcher {
        private TextInputLayout til;
        public SimpleTextWatcher(TextInputLayout til) { this.til = til; }
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (s.length() > 0) { til.setError(null); }
        }
        @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
        @Override public void afterTextChanged(Editable editable) {}
    }
}
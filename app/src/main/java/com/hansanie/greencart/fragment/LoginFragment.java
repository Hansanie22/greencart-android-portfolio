package com.hansanie.greencart.fragment;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
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
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.hansanie.greencart.R;
import com.hansanie.greencart.activity.AuthActivity;
import com.hansanie.greencart.util.CustomToast;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginFragment extends Fragment {

    private TextInputLayout tilLoginPhone;
    private EditText etLoginPhone;
    private MaterialButton btnLogin;
    private TextView tvSwitchToRegister;
    private FirebaseAuth mAuth;

    // Test numbers and OTPs (same as RegisterFragment)
    private final Map<String, String> testNumbersMap = new HashMap<String, String>() {{
        put("+94715607666", "231370");
        put("+94764253159", "112233");
        put("+94776849102", "654321");
        put("+94779303604", "196605");
        put("+94761624810", "200205");
    }};

    public LoginFragment(){}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        tilLoginPhone = view.findViewById(R.id.tilLoginPhone);
        etLoginPhone = view.findViewById(R.id.etLoginPhone);
        btnLogin = view.findViewById(R.id.btnLogin);
        tvSwitchToRegister = view.findViewById(R.id.tvSwitchToRegister);

        tvSwitchToRegister.setOnClickListener(v -> {
            if(getActivity() instanceof AuthActivity){
                ((AuthActivity)getActivity()).switchFragment(new RegisterFragment());
            }
        });

        btnLogin.setOnClickListener(v -> validateAndSendOTP());
    }

    /** Validate phone number and send OTP */
    private void validateAndSendOTP() {
        String phone = etLoginPhone.getText().toString().trim();

        if(TextUtils.isEmpty(phone)){
            showError("Enter phone number");
            return;
        }

        if(phone.length() < 9){
            showError("Enter valid phone number");
            return;
        }

        tilLoginPhone.setError(null);
        startOTP(phone);
    }

    /** Show error with shake animation */
    private void showError(String message){
        tilLoginPhone.setError(message);
        Animation shake = AnimationUtils.loadAnimation(getContext(), R.anim.shake);
        tilLoginPhone.startAnimation(shake);
    }

    /** Start OTP verification and send local notification */
    private void startOTP(String phone){
        // Normalize phone
        if(phone.startsWith("0")){
            phone = "+94" + phone.substring(1);
        } else if(!phone.startsWith("+")){
            phone = "+94" + phone;
        }

        final String finalPhone = phone;

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(finalPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(requireActivity())
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                    @Override
                    public void onCodeSent(@NonNull String verificationId,
                                           @NonNull PhoneAuthProvider.ForceResendingToken token){

                        // Get OTP for test numbers or default
                        String otpToShow = testNumbersMap.getOrDefault(finalPhone, "123456");

                        sendLocalOTPNotification("Greencart OTP", "Your verification code is: " + otpToShow);

                        // Pass to VerifyFragment
                        Bundle bundle = new Bundle();
                        bundle.putString("vId", verificationId);
                        bundle.putString("phone", finalPhone);
                        bundle.putString("mode", "login");

                        VerifyFragment fragment = new VerifyFragment();
                        fragment.setArguments(bundle);

                        if(getActivity() instanceof AuthActivity){
                            ((AuthActivity)getActivity()).switchFragment(fragment);
                        }
                    }

                    @Override
                    public void onVerificationCompleted(@NonNull com.google.firebase.auth.PhoneAuthCredential credential){}

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e){
                        CustomToast.showErrorLong(getContext(), "OTP Failed: " + e.getMessage());
                    }
                }).build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    /** Send local notification with OTP */
    private void sendLocalOTPNotification(String title, String message){
        NotificationManager notificationManager = (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "otp_channel";

        Intent intent = new Intent(requireContext(), AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(requireContext(), 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel channel = new NotificationChannel(channelId, "OTP Channel", NotificationManager.IMPORTANCE_HIGH);
            if(notificationManager != null){
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

        if(notificationManager != null){
            notificationManager.notify(101, builder.build());
        }
    }
}
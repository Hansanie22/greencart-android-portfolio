package com.hansanie.greencart.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.hansanie.greencart.R;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION = 2500; // 2.5 seconds

    private ImageView logoImage;
    private TextView appName;
    private TextView tagline;
    private CircularProgressIndicator progressIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        startAnimations();

        new Handler(Looper.getMainLooper()).postDelayed(this::navigateNext, SPLASH_DURATION);
    }

    private void initViews() {
        logoImage = findViewById(R.id.logoImage);
        appName = findViewById(R.id.appName);
        tagline = findViewById(R.id.tagline);
        progressIndicator = findViewById(R.id.progressIndicator);
    }

    private void startAnimations() {
        if (logoImage != null) {
            logoImage.setAlpha(0f);
            logoImage.setScaleX(0.8f);
            logoImage.setScaleY(0.8f);
            logoImage.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).setInterpolator(new DecelerateInterpolator()).setStartDelay(200).start();
        }
        if (appName != null) {
            appName.setAlpha(0f);
            appName.setTranslationY(30f);
            appName.animate().alpha(1f).translationY(0f).setDuration(600).setInterpolator(new DecelerateInterpolator()).setStartDelay(500).start();
        }
        if (tagline != null) {
            tagline.setAlpha(0f);
            tagline.animate().alpha(1f).setDuration(500).setInterpolator(new DecelerateInterpolator()).setStartDelay(800).start();
        }
        if (progressIndicator != null) {
            progressIndicator.setAlpha(0f);
            progressIndicator.animate().alpha(1f).setDuration(400).setInterpolator(new DecelerateInterpolator()).setStartDelay(1000).start();
        }
    }

    private void navigateNext() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            // ලොගින් වී ඇත්නම් කෙලින්ම MainActivity වෙත
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
        } else {
            // ලොගින් වී නැත්නම් Onboarding පෙන්වා ඉන්පසු MainActivity (Guest) වෙත යාමට ඉඩ සලසයි
            // සටහන: OnboardingActivity එක අවසානයේ MainActivity එකට යොමු කළ යුතුය.
            startActivity(new Intent(SplashActivity.this, OnboardingActivity.class));
        }

        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
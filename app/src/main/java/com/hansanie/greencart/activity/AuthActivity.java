package com.hansanie.greencart.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import com.hansanie.greencart.BuildConfig;
import com.hansanie.greencart.R;
import com.hansanie.greencart.fragment.LoginFragment;

public class AuthActivity extends AppCompatActivity {

    private static final String TAG = "APP_CHECK_DEBUG";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Firebase Initialize කිරීම
        FirebaseApp.initializeApp(this);

        // 2. App Check Setup - Robot check එක අයින් කිරීමට මෙතැන නිවැරදි විය යුතුය
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();

        // ඔබ Testing කරන නිසා දැනට DebugProvider එක අනිවාර්යයෙන්ම අවශ්‍ය වේ
        if (BuildConfig.DEBUG) {
            firebaseAppCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance());
            Log.d(TAG, "Firebase App Check: Debug Mode enabled. Check Logcat for Debug Token!");
        } else {
            firebaseAppCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance());
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_auth);

        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        if (savedInstanceState == null) {
            loadInitialFragment();
        }
    }

    private void loadInitialFragment() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.auth_fragment_container, new LoginFragment())
                .commit();
    }

    public void switchFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(
                        R.anim.flip_right_in,
                        R.anim.flip_right_out,
                        R.anim.flip_left_in,
                        R.anim.flip_left_out
                )
                .replace(R.id.auth_fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }
}
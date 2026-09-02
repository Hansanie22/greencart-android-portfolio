package com.hansanie.greencart.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.hansanie.greencart.R;
import com.hansanie.greencart.util.ThemePreferenceManager;

public class SettingFragment extends Fragment implements SensorEventListener {

    private SwitchMaterial switchDarkMode, switchNotifications, switchAutoTheme;
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    private SensorManager sensorManager;
    private Sensor lightSensor;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_setting, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        switchDarkMode = view.findViewById(R.id.switchDarkMode);
        switchNotifications = view.findViewById(R.id.switchNotifications);
        switchAutoTheme = view.findViewById(R.id.switchAutoTheme);

        sharedPreferences = requireActivity().getSharedPreferences(ThemePreferenceManager.PREFS_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) : null;

        // If light sensor is not available, disable auto theme and show message
        if (lightSensor == null) {
            switchAutoTheme.setEnabled(false);
        }

        loadSettings();

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (switchAutoTheme != null && switchAutoTheme.isChecked()) {
                return;
            }
            boolean currentDark = (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES);
            if (isChecked && !currentDark) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                editor.putBoolean(ThemePreferenceManager.KEY_DARK_MODE, true);
                editor.apply();
                requireActivity().recreate(); // Only recreate if mode actually changes
            } else if (!isChecked && currentDark) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                editor.putBoolean(ThemePreferenceManager.KEY_DARK_MODE, false);
                editor.apply();
                requireActivity().recreate();
            }
        });

        switchAutoTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putBoolean(ThemePreferenceManager.KEY_AUTO_THEME, isChecked).apply();
            switchDarkMode.setEnabled(!isChecked);
            if (!isChecked) {
                ThemePreferenceManager.applyTheme(requireContext());
                requireActivity().recreate(); // Only recreate when leaving auto mode
            } else if (lightSensor == null) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
        });

        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putBoolean(ThemePreferenceManager.KEY_NOTIFICATIONS_ENABLED, isChecked);
            editor.apply();
        });
    }

    private void loadSettings() {
        boolean isDarkMode = sharedPreferences.getBoolean(ThemePreferenceManager.KEY_DARK_MODE, false);
        boolean isNotifyEnabled = sharedPreferences.getBoolean(ThemePreferenceManager.KEY_NOTIFICATIONS_ENABLED, true);
        boolean isAutoTheme = sharedPreferences.getBoolean(ThemePreferenceManager.KEY_AUTO_THEME, false);

        switchDarkMode.setChecked(isDarkMode);
        switchNotifications.setChecked(isNotifyEnabled);
        switchAutoTheme.setChecked(isAutoTheme);
        switchDarkMode.setEnabled(!isAutoTheme);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (switchAutoTheme != null && switchAutoTheme.isChecked() && sensorManager != null && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onPause() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (switchAutoTheme == null || !switchAutoTheme.isChecked()) {
            return;
        }
        float lux = event.values[0];
        boolean dark = lux < 25f;
        int currentMode = AppCompatDelegate.getDefaultNightMode();
        int desiredMode = dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        if (currentMode != desiredMode) {
            AppCompatDelegate.setDefaultNightMode(desiredMode);
            editor.putBoolean(ThemePreferenceManager.KEY_DARK_MODE, dark).apply();
            if (switchDarkMode.isChecked() != dark) {
                switchDarkMode.setChecked(dark);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
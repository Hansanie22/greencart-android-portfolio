package com.hansanie.greencart.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemePreferenceManager {

    public static final String PREFS_NAME = "SettingsPrefs";
    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    public static final String KEY_AUTO_THEME = "auto_theme";

    private ThemePreferenceManager() {
    }

    public static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void applyTheme(Context context) {
        SharedPreferences prefs = getPreferences(context);
        boolean autoTheme = prefs.getBoolean(KEY_AUTO_THEME, false);
        boolean darkMode = prefs.getBoolean(KEY_DARK_MODE, false);
        if (!autoTheme) {
            AppCompatDelegate.setDefaultNightMode(
                    darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
        }
    }
}


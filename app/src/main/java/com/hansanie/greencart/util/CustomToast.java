package com.hansanie.greencart.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.hansanie.greencart.R;

public class CustomToast {

    public enum Type {
        SUCCESS,
        ERROR,
        WARNING,
        INFO
    }

    // ── Convenience shortcuts ─────────────────────────────────────────────

    /** Success toast with LENGTH_SHORT. */
    public static void show(Context context, String message) {
        show(context, message, Type.SUCCESS, Toast.LENGTH_SHORT);
    }

    /** Success toast with LENGTH_LONG. */
    public static void showLong(Context context, String message) {
        show(context, message, Type.SUCCESS, Toast.LENGTH_LONG);
    }

    /** Success toast with LENGTH_SHORT. */
    public static void showSuccess(Context context, String message) {
        show(context, message, Type.SUCCESS, Toast.LENGTH_SHORT);
    }

    /** Error toast with LENGTH_SHORT. */
    public static void showError(Context context, String message) {
        show(context, message, Type.ERROR, Toast.LENGTH_SHORT);
    }

    /** Error toast with LENGTH_LONG. */
    public static void showErrorLong(Context context, String message) {
        show(context, message, Type.ERROR, Toast.LENGTH_LONG);
    }

    /** Warning toast with LENGTH_SHORT. */
    public static void showWarning(Context context, String message) {
        show(context, message, Type.WARNING, Toast.LENGTH_SHORT);
    }

    /** Info toast with LENGTH_SHORT. */
    public static void showInfo(Context context, String message) {
        show(context, message, Type.INFO, Toast.LENGTH_SHORT);
    }

    // ── Core method ───────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")   // Toast.setView() deprecated at API 30 but still works for foreground apps
    public static void show(Context context, String message, Type type, int duration) {
        if (context == null) return;

        View layout = LayoutInflater.from(context)
                .inflate(R.layout.custom_toast_layout, null, false);

        TextView toastText = layout.findViewById(R.id.toast_text);
        ImageView toastIcon = layout.findViewById(R.id.toast_icon);

        toastText.setText(message);

        switch (type) {
            case ERROR:
                toastIcon.setImageResource(R.drawable.ic_close);
                toastIcon.setColorFilter(ContextCompat.getColor(context, R.color.md_theme_error));
                break;

            case WARNING:
                toastIcon.setImageResource(R.drawable.ic_flash);
                toastIcon.setColorFilter(ContextCompat.getColor(context, R.color.toast_warning));
                break;

            case INFO:
                toastIcon.setImageResource(R.drawable.ic_notification);
                toastIcon.setColorFilter(ContextCompat.getColor(context, R.color.md_theme_tertiary));
                break;

            default: // SUCCESS
                toastIcon.setImageResource(R.drawable.ic_check);
                toastIcon.setColorFilter(ContextCompat.getColor(context, R.color.md_theme_primary));
                break;
        }

        // Enter animation
        Animation slideUp = AnimationUtils.loadAnimation(context, R.anim.toast_slide_up);
        layout.startAnimation(slideUp);

        Toast toast = new Toast(context);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }
}


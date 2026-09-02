package com.hansanie.greencart.fragment;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.ClipData;
import android.content.ClipboardManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hansanie.greencart.R;
import com.hansanie.greencart.model.Offer;
import com.hansanie.greencart.util.CustomToast;

import java.util.HashMap;
import java.util.Map;

public class ShakeToWinFragment extends Fragment implements SensorEventListener {

    // ── Shake sensitivity (lower = more sensitive) ──────────────────────────
    private static final float SHAKE_THRESHOLD_G   = 2.7f;   // g-force threshold
    private static final long  SHAKE_SUSTAIN_MS    = 2000L;  // must shake for 2 s
    private static final long  SHAKE_DEBOUNCE_MS   = 300L;   // ignore tiny pauses

    // ── Sensor state ────────────────────────────────────────────────────────
    private SensorManager sensorManager;
    private long shakeStartTime   = 0L;
    private long lastAboveTs      = 0L;
    private boolean dealRipened   = false;

    // ── Views ────────────────────────────────────────────────────────────────
    private ImageView            basketImageView;
    private ImageView            goldenCarrotImageView;
    private MaterialCardView     offerCard;
    private TextView             discountCodeText;
    private TextView             discountAmountText;
    private TextView             shakeHintText;
    private Button               claimButton;
    private CircularProgressIndicator shakeProgress;
    private View                 confettiOverlay;

    // ── Offer data from args ─────────────────────────────────────────────────
    private long   offerId       = -1L;
    private String promoCode     = "";
    private double discountPct   = 0.0;

    // ── Animations ───────────────────────────────────────────────────────────
    private Animation wiggleAnim;
    private Animation floatAnim;
    private boolean   isWiggling = false;

    private FirebaseFirestore firestore;
    private Offer loadedOffer;

    public ShakeToWinFragment() {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_shake_to_win, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        readArgs();
        bindViews(view);
        loadAnimations();
        setupSensor();
        setupClaimButton();

        // Start the idle float on the basket
        basketImageView.startAnimation(floatAnim);

        firestore = FirebaseFirestore.getInstance();
        if (offerId > 0) {
            fetchOfferDetails(offerId);
        } else {
            // fallback: show minimal data
            updateOfferUI();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!dealRipened) {
            registerSensor();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterSensor();
    }

    // ── Args ──────────────────────────────────────────────────────────────────

    private void readArgs() {
        Bundle args = getArguments();
        if (args != null) {
            offerId     = args.getLong("offer_id", -1L);
            promoCode   = args.getString("promo_code", "FRESH20");
            discountPct = args.getDouble("discount", 20.0);
        }
    }

    // ── Firestore fetch ───────────────────────────────────────────────────────
    private void fetchOfferDetails(long id) {
        firestore.collection("offers")
                .whereEqualTo("id", id)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        Offer offer = snapshot.getDocuments().get(0).toObject(Offer.class);
                        if (offer != null) {
                            loadedOffer = offer;
                            // Overwrite fields with loaded data
                            promoCode = offer.getPromoCode() != null ? offer.getPromoCode() : promoCode;
                            discountPct = offer.getDiscountPercentage() != null ? offer.getDiscountPercentage() : discountPct;
                        }
                    }
                    updateOfferUI();
                })
                .addOnFailureListener(e -> updateOfferUI());
    }

    // ── Update UI with offer data ─────────────────────────────────────────────
    private void updateOfferUI() {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            // Set promo code and discount
            String codeDisplay = promoCode == null || promoCode.isEmpty() ? "FRESH20" : promoCode;
            String amountDisplay = (int) discountPct + "% OFF";
            if (discountCodeText != null) discountCodeText.setText(codeDisplay);
            if (discountAmountText != null) discountAmountText.setText(amountDisplay);

            // Set additional fields if available
            if (loadedOffer != null) {
                TextView titleView = getView() != null ? getView().findViewById(R.id.offerTitleText) : null;
                TextView descView = getView() != null ? getView().findViewById(R.id.offerDescriptionText) : null;
                TextView expiryView = getView() != null ? getView().findViewById(R.id.offerExpiryText) : null;
                if (titleView != null) titleView.setText(loadedOffer.getTitle());
                if (descView != null) descView.setText(loadedOffer.getDescription());
                if (expiryView != null && loadedOffer.getExpiryDate() != null && !loadedOffer.getExpiryDate().isEmpty()) {
                    String formattedExpiry = null;
                    String expiryRaw = loadedOffer.getExpiryDate();
                    try {
                        // Try java.time (API 26+)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            java.time.format.DateTimeFormatter isoFormatter = java.time.format.DateTimeFormatter.ISO_DATE_TIME;
                            java.time.ZonedDateTime zdt = java.time.ZonedDateTime.parse(expiryRaw, isoFormatter);
                            java.time.format.DateTimeFormatter outFmt = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy");
                            formattedExpiry = zdt.format(outFmt);
                        } else {
                            // Fallback for pre-API 26, try multiple patterns
                            java.util.Date date = null;
                            try {
                                java.text.SimpleDateFormat isoFmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault());
                                isoFmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                                date = isoFmt.parse(expiryRaw);
                            } catch (Exception e1) {
                                try {
                                    java.text.SimpleDateFormat isoFmt2 = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault());
                                    isoFmt2.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                                    date = isoFmt2.parse(expiryRaw);
                                } catch (Exception e2) {
                                    // fallback below
                                }
                            }
                            if (date != null) {
                                java.text.SimpleDateFormat outFmt = new java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault());
                                formattedExpiry = outFmt.format(date);
                            }
                        }
                    } catch (Exception e) {
                        // fallback: try to show only date part if possible
                        if (expiryRaw.length() >= 10) {
                            formattedExpiry = expiryRaw.substring(0, 10);
                        } else {
                            formattedExpiry = expiryRaw;
                        }
                    }
                    if (formattedExpiry == null || formattedExpiry.isEmpty()) {
                        formattedExpiry = expiryRaw;
                    }
                    expiryView.setText(getString(R.string.offer_expires, formattedExpiry));
                }
            }
        });
    }

    // ── View Binding ──────────────────────────────────────────────────────────

    private void bindViews(View view) {
        basketImageView      = view.findViewById(R.id.basketImageView);
        goldenCarrotImageView = view.findViewById(R.id.goldenCarrotImageView);
        offerCard            = view.findViewById(R.id.offerCard);
        discountCodeText     = view.findViewById(R.id.discountCodeText);
        discountAmountText   = view.findViewById(R.id.discountAmountText);
        shakeHintText        = view.findViewById(R.id.shakeHintText);
        claimButton          = view.findViewById(R.id.claimButton);
        shakeProgress        = view.findViewById(R.id.shakeProgress);
        confettiOverlay      = view.findViewById(R.id.confettiOverlay);

        // Hide until reveal
        offerCard.setVisibility(View.GONE);
        goldenCarrotImageView.setVisibility(View.GONE);
        if (confettiOverlay != null) confettiOverlay.setVisibility(View.GONE);
        if (shakeProgress != null)   shakeProgress.setVisibility(View.GONE);

        // Back button (top-left) wiring if present
        View backBtn = view.findViewById(R.id.btnBack);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        }

        // Promo code chip: copy to clipboard when tapped
        if (discountCodeText != null) {
            discountCodeText.setOnClickListener(v -> {
                String code = promoCode == null || promoCode.isEmpty() ? discountCodeText.getText().toString() : promoCode;
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newPlainText("Promo Code", code);
                    clipboard.setPrimaryClip(clip);
                    CustomToast.showSuccess(getContext(), "Code copied!");
                } else {
                    CustomToast.showWarning(getContext(), "Could not access clipboard");
                }
            });
        }
    }

    // ── Animations ────────────────────────────────────────────────────────────

    private void loadAnimations() {
        // Gentle up/down float for idle basket
        floatAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.float_idle);

        // Rapid left/right wiggle when shaking
        wiggleAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.shake);
        wiggleAnim.setRepeatCount(Animation.INFINITE);
    }

    private void startWiggle() {
        if (isWiggling) return;
        isWiggling = true;
        basketImageView.clearAnimation();
        basketImageView.startAnimation(wiggleAnim);
    }

    private void stopWiggle() {
        isWiggling = false;
        basketImageView.clearAnimation();
        basketImageView.setRotation(0f);
        basketImageView.startAnimation(floatAnim);
    }

    // ── Sensor ────────────────────────────────────────────────────────────────

    private void setupSensor() {
        sensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
    }

    private void registerSensor() {
        if (sensorManager == null) return;
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accel != null) {
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void unregisterSensor() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    // ── SensorEventListener ───────────────────────────────────────────────────

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (dealRipened || event == null || event.values == null) return;

        float x = event.values[0] / SensorManager.GRAVITY_EARTH;
        float y = event.values[1] / SensorManager.GRAVITY_EARTH;
        float z = event.values[2] / SensorManager.GRAVITY_EARTH;
        float gForce = (float) Math.sqrt(x * x + y * y + z * z);

        long now = System.currentTimeMillis();

        if (gForce > SHAKE_THRESHOLD_G) {
            lastAboveTs = now;

            if (shakeStartTime == 0L) {
                shakeStartTime = now;
                startWiggle();
                showShakeProgress();
            }

            // Update progress ring (0–100 over SHAKE_SUSTAIN_MS)
            long elapsed = now - shakeStartTime;
            int progress = (int) Math.min(100, elapsed * 100 / SHAKE_SUSTAIN_MS);
            updateShakeProgress(progress);

            if (elapsed >= SHAKE_SUSTAIN_MS) {
                ripenDeal();
            }

        } else {
            // Allow a brief gap (debounce) before resetting
            if (shakeStartTime != 0L && (now - lastAboveTs) > SHAKE_DEBOUNCE_MS) {
                shakeStartTime = 0L;
                stopWiggle();
                hideShakeProgress();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ── Progress ring helpers ─────────────────────────────────────────────────

    private void showShakeProgress() {
        if (shakeProgress == null || !isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            shakeProgress.setVisibility(View.VISIBLE);
            shakeProgress.setProgress(0, false);
        });
    }

    private void updateShakeProgress(int progress) {
        if (shakeProgress == null || !isAdded()) return;
        requireActivity().runOnUiThread(() -> shakeProgress.setProgress(progress, true));
    }

    private void hideShakeProgress() {
        if (shakeProgress == null || !isAdded()) return;
        requireActivity().runOnUiThread(() -> shakeProgress.setVisibility(View.GONE));
    }

    // ── Deal Reveal ───────────────────────────────────────────────────────────

    private void ripenDeal() {
        if (dealRipened) return;
        dealRipened = true;
        unregisterSensor();

        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            // 1. Vibrate
            vibrate();

            // 2. Hide basket + progress
            basketImageView.clearAnimation();
            basketImageView.animate()
                    .scaleX(0f).scaleY(0f).alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        basketImageView.setVisibility(View.GONE);
                        if (shakeProgress != null) shakeProgress.setVisibility(View.GONE);
                        if (shakeHintText != null) shakeHintText.setVisibility(View.GONE);
                        revealCarrot();
                    })
                    .start();
        });
    }

    private void revealCarrot() {
        goldenCarrotImageView.setVisibility(View.VISIBLE);
        goldenCarrotImageView.setScaleX(0f);
        goldenCarrotImageView.setScaleY(0f);
        goldenCarrotImageView.setRotation(-30f);
        goldenCarrotImageView.setAlpha(0f);

        goldenCarrotImageView.animate()
                .scaleX(1.2f).scaleY(1.2f)
                .rotation(0f)
                .alpha(1f)
                .setDuration(500)
                .setInterpolator(new OvershootInterpolator(2f))
                .withEndAction(() -> {
                    // Settle back to normal size
                    goldenCarrotImageView.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(200)
                            .withEndAction(this::revealOfferCard)
                            .start();
                })
                .start();
    }

    private void revealOfferCard() {
        // Fill in the dynamic offer data (now handled by updateOfferUI)
        updateOfferUI();

        // Slide card up
        offerCard.setVisibility(View.VISIBLE);
        offerCard.setTranslationY(300f);
        offerCard.setAlpha(0f);
        offerCard.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(500)
                .setInterpolator(new OvershootInterpolator(1.5f))
                .start();

        // Show confetti overlay if present
        if (confettiOverlay != null) {
            confettiOverlay.setVisibility(View.VISIBLE);
            confettiOverlay.setAlpha(0f);
            confettiOverlay.animate().alpha(1f).setDuration(400).start();
        }
    }

    // ── Vibration ─────────────────────────────────────────────────────────────

    private void vibrate() {
        if (!isAdded()) return;
        Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Pattern: short-short-long (celebratory)
            vibrator.vibrate(VibrationEffect.createWaveform(
                    new long[]{0, 80, 60, 80, 60, 250}, -1));
        } else {
            vibrator.vibrate(new long[]{0, 80, 60, 80, 60, 250}, -1);
        }
    }

    // ── Claim Offer ───────────────────────────────────────────────────────────

    private void setupClaimButton() {
        claimButton.setOnClickListener(v -> saveOfferToFirestore());
    }

    private void saveOfferToFirestore() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            CustomToast.showWarning(getContext(), "Please log in to claim your offer");
            return;
        }

        claimButton.setEnabled(false);
        claimButton.setText("Saving…");

        // Check if offer already claimed and not used
        FirebaseFirestore.getInstance()
                .collection("user_offers")
                .whereEqualTo("firebaseUid", uid)
                .whereEqualTo("offerId", offerId)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        // Already claimed, check if used
                        Map<String, Object> userOffer = snapshot.getDocuments().get(0).getData();
                        Boolean used = userOffer != null && userOffer.get("used") instanceof Boolean ? (Boolean) userOffer.get("used") : null;
                        if (used != null && !used) {
                            // Already claimed and not used, show message
                            CustomToast.showInfo(getContext(), getString(R.string.promo_code_claimed));
                            claimButton.setText(getString(R.string.claimed_with_emoji));
                            claimButton.setEnabled(false);
                            return;
                        } else if (used != null && used) {
                            // Already claimed and used
                            CustomToast.showWarning(getContext(), getString(R.string.promo_code_invalid));
                            claimButton.setText(getString(R.string.claim_now));
                            claimButton.setEnabled(false);
                            return;
                        }
                    }
                    // Not claimed yet, save new offer with used=false
                    Map<String, Object> data = new HashMap<>();
                    data.put("firebaseUid", uid);
                    data.put("offerId",     offerId);
                    data.put("promoCode",   promoCode != null ? promoCode.trim().toUpperCase(java.util.Locale.getDefault()) : null);
                    data.put("discount",    discountPct);
                    data.put("claimedAt",   com.google.firebase.Timestamp.now());
                    data.put("status",      "ACTIVE");
                    data.put("used",        false);

                    FirebaseFirestore.getInstance()
                            .collection("user_offers")
                            .add(data)
                            .addOnSuccessListener(ref -> {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() -> {
                                    if (claimButton != null) claimButton.setText(getString(R.string.claimed_with_emoji));
                                    CustomToast.showSuccess(getContext(),
                                            getString(R.string.offer_claimed_toast, promoCode));
                                    claimButton.postDelayed(() -> {
                                        if (isAdded()) {
                                            requireActivity().getSupportFragmentManager().popBackStack();
                                        }
                                    }, 1800L);
                                });
                            })
                            .addOnFailureListener(e -> {
                                if (!isAdded()) return;
                                requireActivity().runOnUiThread(() -> {
                                    if (claimButton != null) {
                                        claimButton.setEnabled(true);
                                        claimButton.setText(getString(R.string.claim_now));
                                    }
                                    CustomToast.showError(getContext(), "Could not save offer. Try again.");
                                });
                            });
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        claimButton.setEnabled(true);
                        claimButton.setText(getString(R.string.claim_now));
                        CustomToast.showError(getContext(), "Could not check offer. Try again.");
                    });
                });
    }
}

package com.hansanie.greencart.fragment;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import com.google.firebase.auth.FirebaseAuth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.FieldValue;

import android.content.Intent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.ActivityResult;
import com.hansanie.greencart.network.RetrofitClient;
import com.hansanie.greencart.network.ApiService;
import com.hansanie.greencart.database.AppDatabase;
import com.hansanie.greencart.model.SubscriptionOrder;
import com.hansanie.greencart.model.Order;
import com.hansanie.greencart.model.Payment;
import com.hansanie.greencart.model.OrderItem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import com.hansanie.greencart.R;
import com.hansanie.greencart.model.GrocerySubscription;
import com.hansanie.greencart.model.GrocerySubscriptionItem;
import com.hansanie.greencart.util.CustomToast;
import java.lang.reflect.Field;
import java.lang.reflect.Method;


public class SubscriptionReviewFragment extends BottomSheetDialogFragment {

    // ── Factory ──────────────────────────────────────────────────────────────
    private static final String ARG_FIRESTORE_ID  = "firestoreId";
    private static final String ARG_MYSQL_ID      = "mysqlId";
    private static final String ARG_SUB_NAME      = "subName";
    private static final String ARG_NEXT_DATE     = "nextDate";
    private static final String ARG_TOTAL_AMOUNT  = "totalAmount";

    public static SubscriptionReviewFragment newInstance(
            @Nullable String firestoreId,
            long mysqlId,
            @Nullable String subName,
            @Nullable String nextDate,
            double totalAmount) {

        SubscriptionReviewFragment f = new SubscriptionReviewFragment();
        Bundle b = new Bundle();
        b.putString(ARG_FIRESTORE_ID, firestoreId);
        b.putLong(ARG_MYSQL_ID, mysqlId);
        b.putString(ARG_SUB_NAME, subName);
        b.putString(ARG_NEXT_DATE, nextDate);
        b.putDouble(ARG_TOTAL_AMOUNT, totalAmount);
        f.setArguments(b);
        return f;
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private String firestoreId;
    private String subName;
    private String nextDate;
    private double passedTotal;

    private GrocerySubscription loadedSubscription;
    private final List<GrocerySubscriptionItem> editableItems = new ArrayList<>();

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextView tvSheetTitle, tvDeliveryDate, tvSubtotal,
            tvBaseDiscount, tvGreenPointsValue, tvDeliveryFee, tvTotalPayable, tvPointsEarned;
    private LinearLayout layoutGreenPointsRow, layoutPointsEarned;
    private EditText etPromoCodeSubReview;
    private MaterialButton btnApplyPromoSubReview;
    private TextView tvPromoDiscount;
    private EditText etRedeemPointsSubReview;
    private MaterialButton btnApplyPointsSubReview;
    private TextView tvPointsBalanceSubReview;
    private RecyclerView rvItems;
    private LinearLayout layoutLoading, layoutContent;
    private MaterialButton btnPayNow, btnEditOrder, btnCancelReview;
    private ReviewItemAdapter itemAdapter;

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final double SUBSCRIPTION_DISCOUNT_PERCENT = 0.05;
    private static final double DELIVERY_FEE                  = 300.0;
    private static final double GREEN_POINT_VALUE             = 1.0; // Rs. per point

    // ── Promo / Points State ──────────────────────────────────────────────────
    private int    availableGreenPoints = 0;
    private int    pointsToRedeem       = 0;
    private double promoCodeDiscount    = 0.0;
    private String appliedPromoCode     = null;

    // --- Network & PayHere fields ---
    private ApiService apiService;
    private ActivityResultLauncher<Intent> paymentResultLauncher;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final DateTimeFormatter ORDER_CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // Callback used to generate order codes
    private interface OrderCodeCallback { void onReady(@NonNull String code); void onError(@NonNull String message); }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog);

        if (getArguments() != null) {
            firestoreId = getArguments().getString(ARG_FIRESTORE_ID);
            subName     = getArguments().getString(ARG_SUB_NAME, "Subscription");
            nextDate    = getArguments().getString(ARG_NEXT_DATE, "");
            passedTotal = getArguments().getDouble(ARG_TOTAL_AMOUNT, 0.0);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            View sheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                ViewGroup.LayoutParams lp = sheet.getLayoutParams();
                lp.height = WindowManager.LayoutParams.MATCH_PARENT;
                sheet.setLayoutParams(lp);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_subscription_review, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Initialize ApiService and payment result launcher
        apiService = RetrofitClient.getApiService();
        paymentResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::handlePayHereResult);

        bindViews(view);
        setupRecycler();
        setupClickListeners();
        loadSubscriptionData();
    }

    // ── View Binding ──────────────────────────────────────────────────────────

    private void bindViews(View v) {
        tvSheetTitle            = v.findViewById(R.id.tvReviewTitle);
        tvDeliveryDate          = v.findViewById(R.id.tvReviewDeliveryDate);
        tvSubtotal              = v.findViewById(R.id.tvSubtotal);
        tvBaseDiscount          = v.findViewById(R.id.tvBaseDiscount);
        tvGreenPointsValue      = v.findViewById(R.id.tvGreenPointsValue);
        layoutGreenPointsRow    = v.findViewById(R.id.layoutGreenPointsRow);
        tvDeliveryFee           = v.findViewById(R.id.tvDeliveryFee);
        tvTotalPayable          = v.findViewById(R.id.tvTotalPayable);
        layoutPointsEarned      = v.findViewById(R.id.layoutPointsEarned);
        tvPointsEarned          = v.findViewById(R.id.tvPointsEarned);
        rvItems                 = v.findViewById(R.id.rvReviewItems);
        layoutLoading           = v.findViewById(R.id.layoutReviewLoading);
        layoutContent           = v.findViewById(R.id.layoutReviewContent);
        btnPayNow               = v.findViewById(R.id.btnPayNow);
        btnEditOrder            = v.findViewById(R.id.btnEditOrder);
        btnCancelReview         = v.findViewById(R.id.btnCancelReview);
        etPromoCodeSubReview    = v.findViewById(R.id.etPromoCodeSubReview);
        btnApplyPromoSubReview  = v.findViewById(R.id.btnApplyPromoSubReview);
        tvPromoDiscount         = v.findViewById(R.id.tvPromoDiscount);
        etRedeemPointsSubReview = v.findViewById(R.id.etRedeemPointsSubReview);
        btnApplyPointsSubReview  = v.findViewById(R.id.btnApplyPointsSubReview);
        tvPointsBalanceSubReview = v.findViewById(R.id.tvPointsBalanceSubReview);

        if (tvSheetTitle != null && subName != null)
            tvSheetTitle.setText(getString(R.string.review_title, subName));

        if (tvDeliveryDate != null) {
            tvDeliveryDate.setText(
                    (nextDate != null && !nextDate.isEmpty())
                            ? getString(R.string.delivery_scheduled, nextDate)
                            : getString(R.string.upcoming_delivery));
        }

        // Green points row starts hidden — only shown when points are actually redeemed
        if (layoutGreenPointsRow != null) layoutGreenPointsRow.setVisibility(View.GONE);
        if (layoutPointsEarned  != null) layoutPointsEarned.setVisibility(View.GONE);
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────

    private void setupRecycler() {
        itemAdapter = new ReviewItemAdapter(editableItems, this::recalculate);
        if (rvItems != null) {
            rvItems.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvItems.setAdapter(itemAdapter);
            rvItems.setNestedScrollingEnabled(false);
        }
    }

    // ── Click Listeners ───────────────────────────────────────────────────────

    private void setupClickListeners() {
        if (btnPayNow != null)
            btnPayNow.setOnClickListener(v -> showPaymentMethodDialog());

        if (btnEditOrder != null)
            btnEditOrder.setOnClickListener(v -> SubscriptionReviewFragment.this.openEditSheet());

        if (btnCancelReview != null)
            btnCancelReview.setOnClickListener(v -> dismiss());

        if (btnApplyPromoSubReview != null) {
            btnApplyPromoSubReview.setOnClickListener(v -> {
                String code = etPromoCodeSubReview != null && etPromoCodeSubReview.getText() != null
                        ? etPromoCodeSubReview.getText().toString().trim().toUpperCase(Locale.getDefault()) : "";
                applyPromoCode(code);
            });
        }

        if (btnApplyPointsSubReview != null) {
            btnApplyPointsSubReview.setOnClickListener(v -> applyRedeemFromInput());
        }

        if (etRedeemPointsSubReview != null) {
            etRedeemPointsSubReview.setOnFocusChangeListener((vv, hasFocus) -> {
                if (!hasFocus) applyRedeemFromInput();
            });
        }
    }

    // Minimal stub for editing the subscription order items. Implement full editor later.
    private void openEditSheet() {
        if (!isAdded()) return;
        CustomToast.showInfo(requireContext(), "Edit order - coming soon");
    }

    // ── Data Loading ──────────────────────────────────────────────────────────

    private void loadSubscriptionData() {
        showLoading(true);

        if (firestoreId == null || firestoreId.trim().isEmpty()) {
            showLoading(false);
            recalculateFromPassedTotal();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("grocery_subscriptions")
                .document(firestoreId.trim())
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) return;
                    showLoading(false);

                    if (doc.exists()) {
                        try {
                            GrocerySubscription sub = new GrocerySubscription();
                            sub.setFirestoreId(doc.getId());
                            sub.setId(readLong(doc.get("id")));
                            sub.setFirebaseUid(readString(doc.get("firebaseUid"), readString(doc.get("firebase_uid"), null)));
                            sub.setName(readString(doc.get("name"), readString(doc.get("title"), "")));
                            sub.setStartDate(readString(doc.get("start_date"), readString(doc.get("startDate"), "")));
                            sub.setNextDeliveryDate(readString(doc.get("next_delivery_date"), readString(doc.get("nextDeliveryDate"), "")));
                            sub.setDeliveryDay(readString(doc.get("deliveryDay"), readString(doc.get("delivery_day"), "Monday")));
                            sub.setIntervalDays(readInt(doc.get("intervalDays"), readInt(doc.get("interval_days"), 7)));
                            if (sub.getIntervalDays() == null || sub.getIntervalDays() <= 0) {
                                String freq = readString(doc.get("frequency"), "WEEKLY").toUpperCase(Locale.getDefault());
                                if (freq.contains("BI") || freq.contains("2W")) sub.setIntervalDays(14);
                                else if (freq.contains("MONTH")) sub.setIntervalDays(30);
                                else sub.setIntervalDays(7);
                            }
                            sub.setBonusPoints(readInt(doc.get("bonusPoints"), readInt(doc.get("bonus_points"), 0)));
                            sub.setSkipNext(readBoolean(doc.get("skip_next")) || readBoolean(doc.get("skipNext")));
                            List<GrocerySubscriptionItem> parsedItems = parseItems(doc.get("items"));
                            sub.setItems(parsedItems);
                            sub.setItemCount(parsedItems != null ? parsedItems.size() : 0);
                            sub.setStatus(readString(doc.get("status"), "ACTIVE"));
                            Double total = readDouble(doc.get("totalAmount"));
                            if (total == null) total = readDouble(doc.get("total_amount"));
                            sub.setTotalAmount(total);

                            // ── Address fields — Firestore එකෙන් explicitly read කරන්න ──
                            String addrId = readString(doc.get("deliveryAddressId"), readString(doc.get("addressId"), null));
                            if (addrId != null) sub.setDeliveryAddressId(addrId);
                            String billingId = readString(doc.get("billingAddressId"), null);
                            if (billingId != null) sub.setBillingAddressId(billingId);
                            // ─────────────────────────────────────────────────────────────

                            loadedSubscription = sub;

                            editableItems.clear();
                            Object rawItems = doc.get("items");
                            editableItems.addAll(parseItems(rawItems));
                            enrichItemsWithProductMetadata(editableItems);
                            itemAdapter.notifyDataSetChanged();
                            if (itemAdapter != null) itemAdapter.ensureMetadataForAll();
                            loadAvailableGreenPoints();
                            recalculate();
                            animateContentIn();
                            return;
                        } catch (Exception ex) {
                            android.util.Log.e("SubscriptionReview", "Failed to map subscription doc", ex);
                        }
                    }
                    recalculateFromPassedTotal();
                    animateContentIn();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    showLoading(false);
                    recalculateFromPassedTotal();
                    animateContentIn();
                    CustomToast.showInfo(requireContext(), "Loaded from cache — offline mode");
                });
    }

    // ── Price Calculation ─────────────────────────────────────────────────────

    @SuppressLint("DefaultLocale")
    private void recalculate() {
        // 1. Raw subtotal
        double itemsTotal = computeItemsTotal();

        // 2. Subscription discount (5%)
        double baseDiscount = itemsTotal * SUBSCRIPTION_DISCOUNT_PERCENT;

        // 3. After promo
        double afterPromo = Math.max(0.0, itemsTotal - baseDiscount - promoCodeDiscount);

        // 4. Clamp points: cannot exceed payable amount or available balance
        int maxRedeemByAmount = (int) Math.floor(afterPromo / GREEN_POINT_VALUE);
        int appliedRedeem     = Math.max(0, Math.min(pointsToRedeem,
                Math.min(maxRedeemByAmount, availableGreenPoints)));
        double redeemedValue  = appliedRedeem * GREEN_POINT_VALUE;

        // 5. Final payable
        double finalPayable = Math.max(0.0, afterPromo - redeemedValue + DELIVERY_FEE);

        // 6. Earned points: floor(payable / 1000) * 5  — simple, no inflated bonus
        int earnedPoints = (int) Math.floor(finalPayable / 1000.0) * 5;

        // ── Update UI ────────────────────────────────────────────────────────

        if (tvSubtotal     != null) tvSubtotal.setText(fmt(itemsTotal));
        if (tvBaseDiscount != null) tvBaseDiscount.setText(getString(R.string.minus_amount, fmt(baseDiscount)));
        if (tvPromoDiscount != null) tvPromoDiscount.setText(getString(R.string.minus_amount, fmt(promoCodeDiscount)));
        if (tvDeliveryFee  != null) tvDeliveryFee.setText(fmt(DELIVERY_FEE));
        if (tvTotalPayable != null) tvTotalPayable.setText(fmt(finalPayable));

        // Green points row: only visible when points are actually being redeemed
        if (layoutGreenPointsRow != null) {
            if (appliedRedeem > 0) {
                layoutGreenPointsRow.setVisibility(View.VISIBLE);
                if (tvGreenPointsValue != null)
                    tvGreenPointsValue.setText(getString(R.string.minus_amount, fmt(redeemedValue)));
            } else {
                layoutGreenPointsRow.setVisibility(View.GONE);
            }
        }

        // Points earned banner below total — visible once we have a real total
        if (layoutPointsEarned != null) {
            if (itemsTotal > 0) {
                layoutPointsEarned.setVisibility(View.VISIBLE);
                if (tvPointsEarned != null)
                    tvPointsEarned.setText(String.format(Locale.getDefault(),
                            "%d Green Points", earnedPoints));
            } else {
                layoutPointsEarned.setVisibility(View.GONE);
            }
        }
    }

    // ── Promo / Points Helpers ────────────────────────────────────────────────

    private void loadAvailableGreenPoints() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            availableGreenPoints = 0;
            refreshPointsUi();
            return;
        }
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("wallet")
                .document("green_points")
                .get()
                .addOnSuccessListener(doc -> {
                    Long stored = readLong(doc.get("pointsBalance"));
                    availableGreenPoints = (int) Math.max(0L, stored != null ? stored : 0L);
                    refreshPointsUi();
                })
                .addOnFailureListener(e -> {
                    availableGreenPoints = 0;
                    refreshPointsUi();
                });
    }

    private void refreshPointsUi() {
        // Centralized display: show remaining points after redeem (so UI is consistent)
        int remaining = availableGreenPoints - pointsToRedeem;
        if (remaining < 0) remaining = 0;
        if (tvPointsBalanceSubReview != null)
            tvPointsBalanceSubReview.setText(String.format(Locale.getDefault(), "Balance: %d pts", remaining));
        recalculate();
    }

    private void applyRedeemFromInput() {
        if (etRedeemPointsSubReview == null) return;
        String raw      = etRedeemPointsSubReview.getText() != null
                ? etRedeemPointsSubReview.getText().toString().trim() : "";
        int requested   = 0;
        try { requested = raw.isEmpty() ? 0 : Integer.parseInt(raw); }
        catch (NumberFormatException ignored) { requested = 0; }

        if (requested < 0) {
            CustomToast.showWarning(requireContext(), "Points cannot be negative");
            return;
        }
        if (requested > availableGreenPoints) {
            CustomToast.showWarning(requireContext(), "Not enough points in your balance");
            return;
        }

        // Clamp by payable after subscription + promo discounts
        double itemsTotal   = computeItemsTotal();
        double baseDiscount = itemsTotal * SUBSCRIPTION_DISCOUNT_PERCENT;
        double afterPromo   = Math.max(0.0, itemsTotal - baseDiscount - promoCodeDiscount);
        int    maxPts       = (int) Math.floor(afterPromo / GREEN_POINT_VALUE);

        pointsToRedeem = Math.max(0, Math.min(requested, Math.min(maxPts, availableGreenPoints)));

        if (pointsToRedeem < requested) {
            CustomToast.showInfo(requireContext(),
                    String.format(Locale.getDefault(),
                            "Max redeemable: %d pts (Rs. %.2f)", pointsToRedeem, pointsToRedeem * GREEN_POINT_VALUE));
        } else {
            CustomToast.showSuccess(requireContext(),
                    String.format(Locale.getDefault(), "%d pts applied!", pointsToRedeem));
        }

        // Update balance display through central method so it's consistent with loader
        refreshPointsUi();

        recalculate();
    }

    private void applyPromoCode(String code) {
        if (code == null || code.isEmpty()) {
            CustomToast.showInfo(requireContext(), "Enter a promo code first");
            return;
        }

        java.util.Map<String, Double> codes = new java.util.HashMap<>();
        codes.put("FRESH30",   0.30);
        codes.put("WELCOME10", 0.10);

        double itemsTotal = computeItemsTotal();

        if (codes.containsKey(code)) {
            double pct        = codes.get(code);
            promoCodeDiscount = itemsTotal * pct;
            appliedPromoCode  = code;
            CustomToast.showSuccess(requireContext(),
                    "Promo applied: " + (int)(pct * 100) + "% off!");
        } else {
            promoCodeDiscount = 0.0;
            appliedPromoCode  = null;
            CustomToast.showWarning(requireContext(), "Invalid or expired promo code");
        }
        recalculate();
    }

    /** Sum of (unitPrice × qty) across all current items. */
    private double computeItemsTotal() {
        double total = 0.0;
        for (GrocerySubscriptionItem it : editableItems) {
            double price = it.getUnitPrice() != null ? it.getUnitPrice() : 0.0;
            int    qty   = it.getQuantity()  != null ? it.getQuantity()  : 1;
            total       += price * qty;
        }
        return total;
    }

    /**
     * Points earned for a completed order.
     * Rule: floor(payable / 1000) × 5  +  subscription bonus
     */
    private int calculateOrderEarnedPoints(double payableTotal, double itemsTotal) {
        int base     = (int) Math.floor(Math.max(0.0, payableTotal) / 1000.0) * 5;
        int subBonus = calculateSubscriptionBonusPoints(itemsTotal);
        return Math.max(0, base + subBonus);
    }

    /**
     * Subscription bonus points: min 5, then +2 per 500 LKR of discounted total.
     */
    private int calculateSubscriptionBonusPoints(double itemsTotal) {
        double discountedTotal = Math.max(0.0,
                itemsTotal + DELIVERY_FEE - (itemsTotal * SUBSCRIPTION_DISCOUNT_PERCENT));
        return Math.max(5, (int) Math.floor(discountedTotal / 500.0) * 2);
    }

    private void recalculateFromPassedTotal() {
        double baseDiscount = passedTotal * SUBSCRIPTION_DISCOUNT_PERCENT;
        double finalPayable = Math.max(0.0, passedTotal - baseDiscount + DELIVERY_FEE);

        if (tvSubtotal         != null) tvSubtotal.setText(fmt(passedTotal));
        if (tvBaseDiscount     != null) tvBaseDiscount.setText(getString(R.string.minus_amount, fmt(baseDiscount)));
        if (tvPromoDiscount    != null) tvPromoDiscount.setText(getString(R.string.minus_amount, fmt(0.0)));
        if (tvDeliveryFee      != null) tvDeliveryFee.setText(fmt(DELIVERY_FEE));
        if (tvTotalPayable     != null) tvTotalPayable.setText(fmt(finalPayable));
        // Green points hidden in fallback path too
        if (tvGreenPointsValue != null) tvGreenPointsValue.setVisibility(View.GONE);

        animateContentIn();
    }

    private String fmt(double amount) {
        return String.format(Locale.getDefault(), "Rs. %,.2f", amount);
    }

    // ── Payment ───────────────────────────────────────────────────────────────

    private void showPaymentMethodDialog() {
        if (!isAdded()) return;
        PaymentMethodSheet paymentSheet = new PaymentMethodSheet(selectedMethod -> {
            if ("CARD".equals(selectedMethod)) processCardPayment();
            else                               processCODPayment();
        });
        paymentSheet.show(getChildFragmentManager(), "PaymentMethod");
    }

    private void processCardPayment() {
        // Launch PayHere checkout flow for card payments
        askSaveCardAndStartPayment();
    }

    private void processCODPayment() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            CustomToast.showWarning(getContext(), "Please sign in to place an order");
            return;
        }

        // Persist promo/points to subscription, then create an order record for COD.
        persistPromoAndPoints(() -> {
            // Success writing promo/points — create order with PENDING payment status and COD method
            generateNextOrderCode(uid, new OrderCodeCallback() {
                @Override
                public void onReady(@NonNull String orderCode) {
                    Map<String, Object> orderData = buildOrderData(uid, orderCode, "PENDING", null);
                    orderData.put("paymentMethod", "COD");
                    orderData.put("paymentStatus", "PENDING");
                    orderData.put("paymentReference", null);
                    persistSubscriptionOrder(orderData);
                }

                @Override
                public void onError(@NonNull String message) {
                    CustomToast.showError(getContext(), message);
                }
            });
        }, () -> {
            // Failed to persist promo/points — still attempt to create the order but warn the user
            CustomToast.showWarning(requireContext(), "Order placed but failed to persist promo/points");
            generateNextOrderCode(uid, new OrderCodeCallback() {
                @Override
                public void onReady(@NonNull String orderCode) {
                    Map<String, Object> orderData = buildOrderData(uid, orderCode, "PENDING", null);
                    orderData.put("paymentMethod", "COD");
                    orderData.put("paymentStatus", "PENDING");
                    orderData.put("paymentReference", null);
                    persistSubscriptionOrder(orderData);
                }

                @Override
                public void onError(@NonNull String message) {
                    CustomToast.showError(getContext(), message);
                }
            });
        });
    }

    private void persistPromoAndPoints(Runnable onSuccess, Runnable onFailure) {
        if (loadedSubscription == null || loadedSubscription.getFirestoreId() == null) {
            if (onSuccess != null) onSuccess.run();
            return;
        }
        Map<String, Object> patch = new java.util.HashMap<>();
        patch.put("appliedPromoCode",  appliedPromoCode);
        patch.put("promoCodeDiscount", promoCodeDiscount);
        patch.put("pointsRedeemed",    pointsToRedeem);
        patch.put("redeemValue",       pointsToRedeem * GREEN_POINT_VALUE);

        FirebaseFirestore.getInstance()
                .collection("grocery_subscriptions")
                .document(loadedSubscription.getFirestoreId())
                .set(patch, SetOptions.merge())
                .addOnSuccessListener(u -> { if (onSuccess != null) onSuccess.run(); })
                .addOnFailureListener(e -> { if (onFailure != null) onFailure.run(); });
    }

    // --- PayHere / payment helpers copied/adapted from CheckoutFragment ---
    private void askSaveCardAndStartPayment() {
        if (getContext() == null) return;
        try {
            if (TextUtils.isEmpty(com.hansanie.greencart.BuildConfig.PAYHERE_MERCHANT_ID) || TextUtils.isEmpty(com.hansanie.greencart.BuildConfig.PAYHERE_MERCHANT_SECRET)) {
                CustomToast.showError(getContext(), "Configure PAYHERE_MERCHANT_ID and PAYHERE_MERCHANT_SECRET");
                return;
            }
        } catch (Exception ignored) {}
        if (getContext() == null) return;
        try {
            if (TextUtils.isEmpty(com.hansanie.greencart.BuildConfig.PAYHERE_MERCHANT_ID) || TextUtils.isEmpty(com.hansanie.greencart.BuildConfig.PAYHERE_MERCHANT_SECRET)) {
                CustomToast.showError(getContext(), "Configure PAYHERE_MERCHANT_ID and PAYHERE_MERCHANT_SECRET");
                return;
            }
        } catch (Exception ignored) {}

        // No saved-card concept here — directly launch checkout
        launchPayHereCheckout();
    }

    private void launchPayHereCheckout() {
        try {
            Intent intent = buildPayHereIntent();
            if (intent == null) {
                CustomToast.showError(getContext(), "PayHere SDK is not available");
                return;
            }
            paymentResultLauncher.launch(intent);
        } catch (Exception e) {
            CustomToast.showError(getContext(), "Unable to open PayHere checkout");
        }
    }

    @Nullable
    private Intent buildPayHereIntent() {
        try {
            Class<?> initRequestClass = Class.forName("lk.payhere.androidsdk.model.InitRequest");
            Object initRequest = initRequestClass.getDeclaredConstructor().newInstance();

            invokeSetter(initRequest, "setSandBox", com.hansanie.greencart.BuildConfig.PAYHERE_SANDBOX);
            invokeSetter(initRequest, "setMerchantId", com.hansanie.greencart.BuildConfig.PAYHERE_MERCHANT_ID);
            invokeSetter(initRequest, "setMerchantSecret", com.hansanie.greencart.BuildConfig.PAYHERE_MERCHANT_SECRET);
            invokeSetter(initRequest, "setCurrency", "LKR");

            double itemsTotal = computeItemsTotal();
            double baseDiscount = itemsTotal * SUBSCRIPTION_DISCOUNT_PERCENT;
            double amount = Math.max(0.0, itemsTotal - baseDiscount - promoCodeDiscount - (pointsToRedeem * GREEN_POINT_VALUE) + DELIVERY_FEE);

            invokeSetter(initRequest, "setAmount", amount);
            invokeSetter(initRequest, "setOrderId", "SUBPAY-" + System.currentTimeMillis());
            invokeSetter(initRequest, "setItemsDescription", "GreenCart Subscription - " + (subName != null ? subName : "Subscription"));
            invokeSetter(initRequest, "setNotifyUrl", "https://e_shop.requestcatcher.com/");

            Object customer = invokeMethod(initRequest, "getCustomer");
            if (customer != null) {
                String fullName = (loadedSubscription != null && loadedSubscription.getName() != null) ? loadedSubscription.getName() : "GreenCart User";
                invokeSetter(customer, "setFirstName", fullName);
                invokeSetter(customer, "setLastName", "");
                invokeSetter(customer, "setEmail", "customer@greencart.lk");
                invokeSetter(customer, "setPhone", "0710000000");

                Object address = invokeMethod(customer, "getAddress");
                if (address != null) {
                    invokeSetter(address, "setAddress", loadedSubscription != null ? (loadedSubscription.getDeliveryAddressId() != null ? loadedSubscription.getDeliveryAddressId() : "Colombo") : "Colombo");
                    invokeSetter(address, "setCity", "Colombo");
                    invokeSetter(address, "setCountry", "Sri Lanka");
                }
            }

            Class<?> mainActivityClass = Class.forName("lk.payhere.androidsdk.PHMainActivity");
            Intent intent = new Intent(requireActivity(), mainActivityClass);
            String extraDataKey = resolvePayHereConstant("INTENT_EXTRA_DATA", "PH_EXTRA_DATA");
            putIntentExtraReflective(intent, extraDataKey, initRequest);
            return intent;
        } catch (Exception e) {
            return null;
        }
    }

    private void handlePayHereResult(@NonNull ActivityResult result) {
        if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
            Intent data = result.getData();
            String extraResultKey = resolvePayHereConstant("INTENT_EXTRA_RESULT", "PH_EXTRA_RESULT");
            Object response = data.getSerializableExtra(extraResultKey);
            boolean success = readBoolean(response, "isSuccess");
            if (success) {
                String paymentRef = readNestedString(response, "getData", "getPaymentNo");
                onPaymentSuccess(paymentRef);
                CustomToast.showSuccess(getContext(), "Payment successful");
            } else {
                CustomToast.showWarning(getContext(), "Payment failed or cancelled");
            }
            return;
        }

        if (result.getResultCode() == android.app.Activity.RESULT_CANCELED) {
            CustomToast.showInfo(getContext(), "Payment cancelled");
        }
    }

    private boolean readBoolean(@Nullable Object target, @NonNull String methodName) {
        if (target == null) return false;
        try {
            Method method = target.getClass().getMethod(methodName);
            Object out = method.invoke(target);
            return out instanceof Boolean && (Boolean) out;
        } catch (Exception ignored) {}
        return false;
    }

    @Nullable
    private String readNestedString(@Nullable Object root, @NonNull String firstMethod, @NonNull String secondMethod) {
        if (root == null) return null;
        try {
            Object first = root.getClass().getMethod(firstMethod).invoke(root);
            if (first == null) return null;
            Object second = first.getClass().getMethod(secondMethod).invoke(first);
            return second != null ? String.valueOf(second) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void invokeSetter(@NonNull Object target, @NonNull String methodName, @Nullable Object value) {
        if (value == null) return;
        try {
            Method[] methods = target.getClass().getMethods();
            for (Method method : methods) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) continue;
                Class<?> param = method.getParameterTypes()[0];
                if (param.isAssignableFrom(value.getClass())
                        || (param == boolean.class && value instanceof Boolean)
                        || (param == double.class && value instanceof Number)) {
                    method.invoke(target, value);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    @Nullable
    private Object invokeMethod(@NonNull Object target, @NonNull String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void putIntentExtraReflective(@NonNull Intent intent, @NonNull String key, @NonNull Object payload) {
        try {
            for (Method method : Intent.class.getMethods()) {
                if (!"putExtra".equals(method.getName()) || method.getParameterCount() != 2) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params[0] != String.class) continue;
                if (params[1].isAssignableFrom(payload.getClass())) {
                    method.invoke(intent, key, payload);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    @NonNull
    private String resolvePayHereConstant(@NonNull String name, @NonNull String fallback) {
        try {
            Class<?> constants = Class.forName("lk.payhere.androidsdk.PHConstants");
            Field field = constants.getField(name);
            Object value = field.get(null);
            if (value != null) return String.valueOf(value);
        } catch (Exception ignored) {}
        return fallback;
    }

    // --- Order generation & persistence for subscription payment ---
    private void onPaymentSuccess(@Nullable String paymentReference) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            CustomToast.showWarning(getContext(), "Please sign in to place an order");
            return;
        }

        generateNextOrderCode(uid, new OrderCodeCallback() {
            @Override
            public void onReady(@NonNull String orderCode) {
                Map<String, Object> orderData = buildOrderData(uid, orderCode, "PAID", paymentReference);
                persistSubscriptionOrder(orderData);
            }

            @Override
            public void onError(@NonNull String message) {
                CustomToast.showError(getContext(), message);
            }
        });
    }

    private void generateNextOrderCode(@NonNull String uid, @NonNull OrderCodeCallback callback) {
        String dateKey = LocalDate.now().format(ORDER_CODE_DATE);
        db.runTransaction((com.google.firebase.firestore.Transaction.Function<String>) transaction -> {
            com.google.firebase.firestore.DocumentReference counterRef = db.collection("order_sequence").document(dateKey);
            com.google.firebase.firestore.DocumentSnapshot snapshot = transaction.get(counterRef);
            long next = 1L;
            if (snapshot.exists()) {
                Long last = snapshot.getLong("last");
                next = (last != null ? last : 0L) + 1L;
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("last", next);
            payload.put("updatedAt", FieldValue.serverTimestamp());
            payload.put("updatedBy", uid);
            transaction.set(counterRef, payload, SetOptions.merge());
            return String.format(Locale.getDefault(), "%s-%03d", dateKey, next);
        }).addOnSuccessListener(callback::onReady)
                .addOnFailureListener(e -> callback.onError("Unable to generate order id"));
    }

    private Map<String, Object> buildOrderData(@NonNull String uid,
                                               @NonNull String orderCode,
                                               @NonNull String paymentStatus,
                                               @Nullable String paymentReference) {
        double itemsTotal           = computeItemsTotal();
        double subscriptionDiscount = itemsTotal * SUBSCRIPTION_DISCOUNT_PERCENT;
        double shipping             = DELIVERY_FEE;
        double preRedeem            = Math.max(0.0, itemsTotal + shipping - subscriptionDiscount - promoCodeDiscount);
        int    redeemPts            = Math.max(0, pointsToRedeem);
        double redeemValue          = redeemPts * GREEN_POINT_VALUE;
        double total                = Math.max(0.0, preRedeem - redeemValue);
        int    earnedPoints         = calculateOrderEarnedPoints(total, itemsTotal);

        // ── Address — model String fields ────────────────────────────────────────
        String deliveryAddressId = null;
        String billingAddressId  = null;
        if (loadedSubscription != null) {
            // deliveryAddressId: prefer dedicated field, fall back to addressId
            deliveryAddressId = loadedSubscription.getDeliveryAddressId();
            if (deliveryAddressId == null || deliveryAddressId.trim().isEmpty())
                deliveryAddressId = loadedSubscription.getAddressId();

            billingAddressId = loadedSubscription.getBillingAddressId();
        }
        // ─────────────────────────────────────────────────────────────────────────

        List<Map<String, Object>> orderItems = new ArrayList<>();
        for (GrocerySubscriptionItem it : editableItems) {
            Map<String, Object> im = new HashMap<>();
            im.put("productId",          it.getProductId());
            im.put("variantId",          it.getVariantId());
            im.put("name",               it.getName());
            im.put("variantName",        it.getVariantName());
            im.put("unitPrice",          it.getUnitPrice());
            im.put("price",              it.getUnitPrice());
            im.put("quantity",           it.getQuantity());
            im.put("lineTotal",          (it.getUnitPrice() != null ? it.getUnitPrice() : 0.0)
                    * (it.getQuantity()  != null ? it.getQuantity()  : 1));
            im.put("imageUrl",           it.getImageUrl());
            im.put("isSubscriptionItem", true);
            orderItems.add(im);
        }

        Map<String, Object> orderData = new HashMap<>();
        orderData.put("firebaseUid",              uid);
        orderData.put("orderCode",                orderCode);

        // ── Address fields correctly populated ───────────────────────────────────
        orderData.put("addressId",                deliveryAddressId);   // was: null
        orderData.put("deliveryAddressId",        deliveryAddressId);
        orderData.put("billingAddressId",         billingAddressId);
        orderData.put("deliveryAddress",          "");                  // text address if needed later
        // ─────────────────────────────────────────────────────────────────────────

        orderData.put("paymentMethod",            "CARD");
        orderData.put("paymentStatus",            paymentStatus);
        orderData.put("paymentReference",         paymentReference);
        orderData.put("subtotal",                 itemsTotal);
        orderData.put("shipping",                 shipping);
        orderData.put("subscriptionDiscount",     subscriptionDiscount);
        orderData.put("promoDiscount",            promoCodeDiscount);
        orderData.put("greenPointsRedeemed",      redeemPts);
        orderData.put("greenPointsRedeemValue",   redeemValue);
        orderData.put("greenPointsEarned",        earnedPoints);
        orderData.put("totalAmount",              total);
        orderData.put("isSubscription",           true);
        orderData.put("subscriptionFirestoreId",  firestoreId);
        orderData.put("items",                    orderItems);
        orderData.put("promoCode",                appliedPromoCode);
        orderData.put("createdAtIso",             LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        return orderData;
    }

    private void persistSubscriptionOrder(@NonNull Map<String, Object> orderData) {
        String orderCode = String.valueOf(orderData.get("orderCode"));
        if (orderCode == null || orderCode.isEmpty()) return;
        orderData.put("firestoreDocumentId", orderCode);

        Map<String, Object> firebasePayload = new HashMap<>(orderData);
        firebasePayload.put("createdAt", FieldValue.serverTimestamp());

        db.collection("orders").document(orderCode)
                .set(firebasePayload)
                .addOnSuccessListener(aVoid -> {
                    // Attempt MySQL sync
                    Order mysqlOrder = buildMySqlOrderFromMap(orderData);
                    apiService.saveOrderToMySql(mysqlOrder).enqueue(new Callback<Order>() {
                        @Override
                        public void onResponse(Call<Order> call, Response<Order> response) {
                            // Don't bail out just because the UI fragment was dismissed; we still need to
                            // persist the subscription_order mapping to Firestore and backend.
                            if (!response.isSuccessful() || response.body() == null) {
                                String err = response.errorBody() != null ? "HTTP " + response.code() : "MySQL error";
                                db.collection("orders").document(orderCode).update("mysqlSyncStatus", "FAILED", "mysqlSyncError", err);
                                // Still save subscription_orders to Firestore/local with null MySQL id
                                saveSubscriptionOrderLink(null, null);
                                return;
                            }
                            Long mysqlOrderId = response.body().getId();
                            Map<String, Object> patch = new HashMap<>();
                            patch.put("mysqlSyncStatus", "SYNCED");
                            patch.put("mysqlSyncAt", FieldValue.serverTimestamp());
                            patch.put("id", mysqlOrderId);
                            db.collection("orders").document(orderCode).update(patch);

                            // Save subscription -> order link
                            saveSubscriptionOrderLink(mysqlOrderId, null);

                            // Also save payment row in payments collection and sync to MySQL similar to checkout
                            Map<String, Object> paymentDoc = new HashMap<>();
                            String pm = readString(orderData.get("paymentMethod"), "CARD");
                            String paymentStatus = readString(orderData.get("paymentStatus"), "PENDING");
                            paymentDoc.put("firebaseUid", orderData.get("firebaseUid"));
                            paymentDoc.put("orderCode", orderCode);
                            paymentDoc.put("mysqlOrderId", mysqlOrderId);
                            paymentDoc.put("amount", orderData.get("totalAmount"));
                            paymentDoc.put("currency", "LKR");
                            paymentDoc.put("paymentMethod", pm);
                            // For card payments that succeeded mark SUCCESS; for COD mark PENDING
                            String docStatus = ("CARD".equals(pm) && "PAID".equalsIgnoreCase(paymentStatus)) ? "SUCCESS" : "PENDING";
                            paymentDoc.put("status", docStatus);
                            paymentDoc.put("payherePaymentId", orderData.get("paymentReference"));
                            paymentDoc.put("createdAt", FieldValue.serverTimestamp());

                            db.collection("payments").add(paymentDoc).addOnSuccessListener(doc -> {
                                // Always attempt to sync the payment to MySQL backend (best-effort)
                                try {
                                    Payment p = new Payment();
                                    p.setId(java.util.UUID.randomUUID().toString());
                                    p.setOrderId(mysqlOrderId);
                                    p.setFirebaseUid((String) orderData.get("firebaseUid"));
                                    p.setAmount(orderData.get("totalAmount") instanceof Number ? ((Number) orderData.get("totalAmount")).doubleValue() : 0.0);
                                    p.setCurrency("LKR");
                                    p.setPaymentMethod(pm);
                                    // Preserve payment status: SUCCESS for succeeded card payments, PENDING for COD
                                    p.setStatus(docStatus);
                                    p.setPayherePaymentId((String) orderData.get("paymentReference"));
                                    p.setPayhereAmount(orderData.get("totalAmount") instanceof Number ? ((Number) orderData.get("totalAmount")).doubleValue() : 0.0);
                                    if (apiService != null) {
                                        apiService.savePayment(p).enqueue(new Callback<Payment>() {
                                            @Override
                                            public void onResponse(Call<Payment> call, Response<Payment> response) {
                                                if (!response.isSuccessful()) {
                                                    String err = null;
                                                    try { if (response.errorBody() != null) err = response.errorBody().string(); } catch (java.io.IOException ex) { err = ex.getMessage(); }
                                                    android.util.Log.w("SUB_REMOTE", "savePayment failed HTTP " + response.code() + (err != null ? " - " + err : ""));
                                                    try { if (isAdded()) CustomToast.showWarning(requireContext(), "Payment sync failed: HTTP " + response.code()); } catch (Exception ignored) {}
                                                } else {
                                                    android.util.Log.i("SUB_REMOTE", "Payment saved to backend for orderId=" + mysqlOrderId + " status=" + p.getStatus());
                                                }
                                            }

                                            @Override
                                            public void onFailure(Call<Payment> call, Throwable t) {
                                                android.util.Log.e("SUB_REMOTE", "savePayment failed: " + (t != null ? t.getMessage() : "unknown"), t);
                                                try { if (isAdded()) CustomToast.showWarning(requireContext(), "Payment sync error: " + (t != null ? t.getMessage() : "unknown")); } catch (Exception ignored) {}
                                            }
                                        });
                                    }
                                } catch (Exception ex) {
                                    android.util.Log.e("SUB_REMOTE", "Exception preparing payment save: " + ex.getMessage(), ex);
                                }
                            });
                        }

                        @Override
                        public void onFailure(Call<Order> call, Throwable t) {
                            if (!isAdded()) return;
                            db.collection("orders").document(orderCode).update("mysqlSyncStatus", "FAILED", "mysqlSyncError", t.getMessage() != null ? t.getMessage() : "network error");
                            saveSubscriptionOrderLink(null, null);
                        }
                    });

                    advanceNextDeliveryDate();
                        // Remove any reminder notification for this subscription (so user can't pay twice)
                        try {
                            // payload stores 'firestoreId=<id>;' when notification was persisted
                            String rmPayloadPart = null;
                            if (loadedSubscription != null && loadedSubscription.getFirestoreId() != null) {
                                rmPayloadPart = "firestoreId=" + loadedSubscription.getFirestoreId();
                            }
                            final String payloadPart = rmPayloadPart;
                                if (payloadPart != null) {
                                    final android.content.Context appCtx = requireContext().getApplicationContext();
                                    java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                                        com.hansanie.greencart.database.AppDatabase db = com.hansanie.greencart.database.AppDatabase.getInstance(appCtx);
                                        db.notificationDao().deleteByDestinationAndPayloadContains(com.hansanie.greencart.util.NotificationHelper.DEST_SUBSCRIPTIONS, payloadPart);
                                        // Broadcast update so badge and notifications list refresh
                                        android.content.Intent broadcast = new android.content.Intent(com.hansanie.greencart.network.MyFirebaseMessagingService.ACTION_NOTIFICATION_COUNT_UPDATED);
                                        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(appCtx).sendBroadcast(broadcast);
                                    });
                                }

                            double total = orderData.get("totalAmount") instanceof Number ? ((Number) orderData.get("totalAmount")).doubleValue() : 0.0;
                            openOrderSuccess(orderCode, total);
                            // Close the review sheet
                            dismiss();
                        } catch (Exception ignored) {}
                })
                .addOnFailureListener(e -> CustomToast.showError(getContext(), "Unable to create order record"));
    }

      private void openOrderSuccess(@NonNull String orderCode, double total) {
          if (!isAdded()) return;
          OrderSuccessFragment fragment = OrderSuccessFragment.newInstance(orderCode, total);
          try {
              // Prefer the activity's fragment container if present
              android.app.Activity act = requireActivity();
              View containerView = act.findViewById(R.id.fragmentContainer);
              androidx.fragment.app.FragmentManager fm = null;
              if (act instanceof androidx.fragment.app.FragmentActivity) {
                  fm = ((androidx.fragment.app.FragmentActivity) act).getSupportFragmentManager();
              } else {
                  fm = getParentFragmentManager();
              }
              if (fm != null) {
                  androidx.fragment.app.FragmentTransaction tx = fm.beginTransaction();
                  if (containerView != null) {
                      tx.replace(R.id.fragmentContainer, fragment).addToBackStack(null).commitAllowingStateLoss();
                      return;
                  }
                  // Fallback to top-level content view id which should always exist
                  tx.replace(android.R.id.content, fragment).addToBackStack(null).commitAllowingStateLoss();
                  return;
              }
          } catch (Exception ignored) {
              // ignore and fallback to parent fragment manager
          }
          try {
              getParentFragmentManager().beginTransaction()
                      .replace(android.R.id.content, fragment)
                      .addToBackStack(null)
                      .commitAllowingStateLoss();
          } catch (Exception ignored) {}
      }

    private Order buildMySqlOrderFromMap(@NonNull Map<String, Object> orderData) {
        Order order = new Order();
        order.setFirebaseUid((String) orderData.get("firebaseUid"));
        order.setOrderCode((String) orderData.get("orderCode"));
        order.setAddressId(readString(orderData.get("addressId"), null));
        order.setDeliveryAddress(readString(orderData.get("deliveryAddress"), ""));
        order.setSubtotal(orderData.get("subtotal") instanceof Number
                ? ((Number) orderData.get("subtotal")).doubleValue() : 0.0);
        order.setShipping(orderData.get("shipping") instanceof Number
                ? ((Number) orderData.get("shipping")).doubleValue() : 0.0);
        order.setTotalAmount(orderData.get("totalAmount") instanceof Number ? ((Number) orderData.get("totalAmount")).doubleValue() : 0.0);
        double subDiscount = orderData.get("subscriptionDiscount") instanceof Number ? ((Number) orderData.get("subscriptionDiscount")).doubleValue() : 0.0;
        double promoDiscount = orderData.get("promoDiscount") instanceof Number ? ((Number) orderData.get("promoDiscount")).doubleValue() : 0.0;
        double pointsDiscount = orderData.get("greenPointsRedeemValue") instanceof Number ? ((Number) orderData.get("greenPointsRedeemValue")).doubleValue() : 0.0;
        order.setDiscountAmount(subDiscount + promoDiscount + pointsDiscount);
        order.setOrderStatus("PENDING");
        order.setStatus("PENDING");
        order.setSubscriptionOrder(true);
        order.setPaymentStatus(readString(orderData.get("paymentStatus"), "PENDING"));
        order.setCreatedAt(orderData.get("createdAtIso") instanceof String ? (String) orderData.get("createdAtIso") : LocalDateTime.now().format(DATE_TIME_FORMATTER));
        // Items
        List<OrderItem> items = new ArrayList<>();
        Object raw = orderData.get("items");
        if (raw instanceof List<?>) {
            for (Object o : (List<?>) raw) {
                if (!(o instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> im = (Map<String, Object>) o;
                OrderItem it = new OrderItem();
                Long pid = parseLongOrNull(im.get("productId"));
                if (pid == null) continue;
                it.setProductId(pid);
                it.setVariantId(parseLongOrNull(im.get("variantId")));
                int qty = im.get("quantity") instanceof Number ? ((Number) im.get("quantity")).intValue() : 0;
                if (qty <= 0) continue;
                it.setQuantity(qty);
                double unit = im.get("unitPrice") instanceof Number ? ((Number) im.get("unitPrice")).doubleValue() : 0.0;
                it.setUnitPrice(unit);
                it.setPriceAtPurchase(unit * qty);
                items.add(it);
            }
        }
        order.setItems(items);
        if (orderData.containsKey("promoCode")) order.setPromoCode(readString(orderData.get("promoCode"), null));
        if (orderData.containsKey("promoDiscount")) {
            Object pd = orderData.get("promoDiscount"); if (pd instanceof Number) order.setPromoDiscount(((Number) pd).doubleValue());
        }
        return order;
    }

    private Long parseLongOrNull(@Nullable Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            String t = ((String) value).trim();
            if (!t.matches("\\d+")) return null;
            try { return Long.parseLong(t); } catch (Exception ignored) {}
        }
        return null;
    }

    private void saveSubscriptionOrderLink(@Nullable Long mysqlOrderId, @Nullable Long deliveryDateId) {

        // ── Guard: orderId නැතිව MySQL table save කිරීම අර්ථ රහිතයි ──────────────
        if (mysqlOrderId == null || mysqlOrderId <= 0) {
            android.util.Log.w("SUB_REMOTE",
                    "saveSubscriptionOrderLink skipped — mysqlOrderId is null or invalid");
            return;
        }

        // ── Subscription Firestore id resolve ─────────────────────────────────────
        final String subsFirestoreId =
                (loadedSubscription != null && loadedSubscription.getFirestoreId() != null)
                        ? loadedSubscription.getFirestoreId()
                        : firestoreId;

        final Long subscriptionMysqlId =
                (loadedSubscription != null) ? loadedSubscription.getId() : null;

        // ── 1. Local Room DB save ─────────────────────────────────────────────────
        new Thread(() -> {
            try {
                android.content.Context ctx = null;
                try {
                    ctx = requireContext().getApplicationContext();
                } catch (Exception ignored) {
                    ctx = (getContext() != null) ? getContext().getApplicationContext() : null;
                }

                if (ctx == null) {
                    android.util.Log.w("SUB_LOCAL",
                            "Application context unavailable — skipped local save for firestoreId=" + subsFirestoreId);
                    return;
                }

                com.hansanie.greencart.model.SubscriptionOrder local =
                        com.hansanie.greencart.model.SubscriptionOrder.builder()
                                .subscriptionId(subscriptionMysqlId)
                                .orderId(mysqlOrderId)
                                .deliveryDate(nextDate)
                                .build();

                AppDatabase.getInstance(ctx).subscriptionOrderDao().insert(local);
                android.util.Log.i("SUB_LOCAL",
                        "Saved locally: firestoreId=" + subsFirestoreId
                                + ", subscriptionId=" + subscriptionMysqlId
                                + ", orderId=" + mysqlOrderId);

            } catch (Exception e) {
                android.util.Log.e("SUB_LOCAL",
                        "Failed to save local subscription order: "
                                + (e.getMessage() != null ? e.getMessage() : "unknown"), e);
            }
        }).start();

        // ── 2. Firestore mirror save ───────────────────────────────────────────────
        Map<String, Object> firestoreDoc = new HashMap<>();
        firestoreDoc.put("subscriptionFirestoreId", subsFirestoreId);
        firestoreDoc.put("subscriptionId",          subscriptionMysqlId);
        firestoreDoc.put("orderId",                 mysqlOrderId);
        firestoreDoc.put("deliveryDate",            nextDate);
        firestoreDoc.put("createdAt",               FieldValue.serverTimestamp());

        db.collection("subscription_orders")
                .add(firestoreDoc)
                .addOnSuccessListener(ref -> {
                    android.util.Log.i("SUB_REMOTE",
                            "Firestore subscription_orders saved: docId=" + ref.getId()
                                    + ", firestoreId=" + subsFirestoreId
                                    + ", orderId=" + mysqlOrderId);

                    // ── 3. Backend (MySQL) typed save ─────────────────────────────
                    saveSubscriptionOrderToBackend(
                            subscriptionMysqlId, mysqlOrderId, subsFirestoreId, ref.getId());
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("SUB_REMOTE",
                            "Firestore subscription_orders save failed: "
                                    + (e.getMessage() != null ? e.getMessage() : "unknown"), e);

                    // Firestore fail වුනත් backend save attempt කරන්න
                    saveSubscriptionOrderToBackend(
                            subscriptionMysqlId, mysqlOrderId, subsFirestoreId, null);
                });
    }

    // ── Backend typed save — helper method ───────────────────────────────────────
    private void saveSubscriptionOrderToBackend(
            @Nullable Long subscriptionMysqlId,
            @NonNull  Long mysqlOrderId,
            @Nullable String subsFirestoreId,
            @Nullable String firestoreDocRef) {

        if (apiService == null) {
            android.util.Log.w("SUB_REMOTE", "apiService is null — skipping backend save");
            return;
        }

        // subscriptionId නැතිනම් backend save skip කරන්න
        // (MySQL foreign key constraint: subscription_id NOT NULL)
        if (subscriptionMysqlId == null) {
            android.util.Log.w("SUB_REMOTE",
                    "subscriptionMysqlId is null — skipping backend save. "
                            + "Subscription may not have synced to MySQL yet.");
            return;
        }

        com.hansanie.greencart.model.SubscriptionOrder payload =
                com.hansanie.greencart.model.SubscriptionOrder.builder()
                        .subscriptionId(subscriptionMysqlId)
                        .orderId(mysqlOrderId)
                        .deliveryDate(nextDate)
                        .build();

        apiService.saveSubscriptionOrder(payload).enqueue(new retrofit2.Callback<Void>() {
            @Override
            public void onResponse(retrofit2.Call<Void> call,
                                   retrofit2.Response<Void> response) {
                if (response.isSuccessful()) {
                    android.util.Log.i("SUB_REMOTE",
                            "Backend subscription_order saved: subscriptionId=" + subscriptionMysqlId
                                    + ", orderId=" + mysqlOrderId);
                } else {
                    String errBody = "";
                    try {
                        if (response.errorBody() != null)
                            errBody = response.errorBody().string();
                    } catch (java.io.IOException ex) {
                        errBody = ex.getMessage();
                    }
                    android.util.Log.e("SUB_REMOTE",
                            "Backend save failed HTTP " + response.code()
                                    + " — " + errBody
                                    + " | subscriptionId=" + subscriptionMysqlId
                                    + ", orderId=" + mysqlOrderId);
                    try {
                        if (isAdded())
                            CustomToast.showWarning(requireContext(),
                                    "Subscription order backend save failed: HTTP " + response.code());
                    } catch (Exception ignored) {}
                }
            }

            @Override
            public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                android.util.Log.e("SUB_REMOTE",
                        "Backend save network error: "
                                + (t.getMessage() != null ? t.getMessage() : "unknown"), t);
                try {
                    if (isAdded())
                        CustomToast.showWarning(requireContext(),
                                "Subscription order backend save error: "
                                        + (t.getMessage() != null ? t.getMessage() : "unknown"));
                } catch (Exception ignored) {}
            }
        });
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    private void showLoading(boolean show) {
        if (layoutLoading != null)
            layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        if (layoutContent != null)
            layoutContent.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void animateContentIn() {
        if (layoutContent == null) return;
        layoutContent.setAlpha(0f);
        layoutContent.setTranslationY(40f);
        layoutContent.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setStartDelay(80)
                .start();
    }

    // ── Item Parsing ──────────────────────────────────────────────────────────

    @NonNull
    private List<GrocerySubscriptionItem> parseItems(@Nullable Object raw) {
        List<GrocerySubscriptionItem> out = new ArrayList<>();
        if (raw == null) return out;

        Iterable<?> iterable = null;
        if (raw instanceof List<?>) {
            iterable = (List<?>) raw;
        } else if (raw instanceof java.util.Map) {
            iterable = ((java.util.Map<?, ?>) raw).values();
        }
        if (iterable == null) return out;

        for (Object obj : iterable) {
            if (!(obj instanceof java.util.Map)) continue;
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> m = (java.util.Map<String, Object>) obj;

            Long   pid         = readLong(m.get("productId"));
            Long   vid         = readLong(m.get("variantId"));
            String name        = readString(m.get("name"), null);
            if (name == null) name = readString(m.get("productName"), "Item");
            String variantName = readString(m.get("variantName"), "");
            if (variantName == null || variantName.isEmpty())
                variantName = readString(m.get("variant_name"), "");
            int    qty         = readInt(m.get("quantity"), 1);
            Double unitPrice   = firstDouble(m, "unitPrice", "price", "priceAtPurchase", "effectivePrice", "finalPrice");
            String image       = firstImage(m, "imageUrl", "image", "productImage");
            if ((image == null || image.isEmpty()) && m.get("images") instanceof java.util.List) {
                java.util.List<?> imgs = (java.util.List<?>) m.get("images");
                if (!imgs.isEmpty() && imgs.get(0) != null) image = String.valueOf(imgs.get(0));
            }

            GrocerySubscriptionItem item = GrocerySubscriptionItem.builder()
                    .productId(pid)
                    .variantId(vid)
                    .name(name != null ? name : "Item")
                    .variantName(variantName != null ? variantName : "")
                    .quantity(qty)
                    .unitPrice(unitPrice)
                    .imageUrl(image != null ? image : "")
                    .build();
            out.add(item);
        }
        return out;
    }

    // ── Type Helpers ──────────────────────────────────────────────────────────

    private String readString(Object v, String fb) {
        if (v == null) return fb;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? fb : s;
    }

    private int readInt(Object v, int fb) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) { try { return Integer.parseInt(((String) v).trim()); } catch (Exception ignored) {} }
        return fb;
    }

    private Long readLong(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) { try { return Long.parseLong(((String) v).trim()); } catch (Exception ignored) {} }
        return null;
    }

    private Double readDouble(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) { try { return Double.parseDouble(((String) v).trim()); } catch (Exception ignored) {} }
        return null;
    }

    // Simple boolean reader to support Firestore field values (Boolean, Number, String)
    private boolean readBoolean(@Nullable Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        if (value instanceof String) {
            String normalized = ((String) value).trim();
            return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
        }
        return false;
    }

    private Double firstDouble(java.util.Map<String, Object> m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            if (!m.containsKey(k)) continue;
            Double d = readDouble(m.get(k));
            if (d != null) return d;
        }
        return null;
    }

    private String firstImage(java.util.Map<String, Object> m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            Object o = m.get(k);
            if (o == null) continue;
            String s = String.valueOf(o).trim();
            if (!s.isEmpty()) return s;
        }
        return null;
    }

    private void enrichItemsWithProductMetadata(List<GrocerySubscriptionItem> items) {
        if (items == null || items.isEmpty()) return;

        java.util.Set<Long> productIds = new java.util.HashSet<>();
        for (GrocerySubscriptionItem it : items) {
            if (it != null && it.getProductId() != null) productIds.add(it.getProductId());
        }
        if (productIds.isEmpty()) return;

        java.util.List<Long> allIds = new java.util.ArrayList<>(productIds);
        final int BATCH = 10;
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        for (int start = 0; start < allIds.size(); start += BATCH) {
            int end = Math.min(allIds.size(), start + BATCH);
            java.util.List<Long> sub = allIds.subList(start, end);

            db.collection("products")
                    .whereIn("id", sub)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (!isAdded()) return;
                        java.util.Map<Long, java.util.Map<String, Object>> products = new java.util.HashMap<>();
                        for (var d : snapshot.getDocuments()) {
                            if (!d.exists()) continue;
                            Object idObj = d.get("id");
                            Long pid = null;
                            if (idObj instanceof Number) pid = ((Number) idObj).longValue();
                            else if (idObj instanceof String) {
                                try { pid = Long.parseLong(((String) idObj).trim()); } catch (Exception ignored) {}
                            }
                            if (pid == null) continue;
                            products.put(pid, d.getData());
                        }

                        boolean changed = false;
                        for (GrocerySubscriptionItem it : items) {
                            if (it == null || it.getProductId() == null) continue;
                            java.util.Map<String, Object> pd = products.get(it.getProductId());
                            if (pd == null) continue;

                            // name
                            if (TextUtils.isEmpty(it.getName())) {
                                Object nameObj = pd.get("name");
                                if (nameObj == null) nameObj = pd.get("title");
                                if (nameObj != null) { it.setName(String.valueOf(nameObj)); changed = true; }
                            }

                            // unitPrice
                            if (it.getUnitPrice() == null) {
                                boolean priceSet = false;
                                if (it.getVariantId() != null) {
                                    Object variantsObj = pd.get("variants");
                                    if (variantsObj instanceof java.util.List) {
                                        for (Object vo : (java.util.List<?>) variantsObj) {
                                            if (!(vo instanceof java.util.Map)) continue;
                                            java.util.Map<?,?> vm = (java.util.Map<?,?>) vo;
                                            Object vid = vm.get("id");
                                            Long vidLong = null;
                                            if (vid instanceof Number) vidLong = ((Number) vid).longValue();
                                            else if (vid instanceof String) {
                                                try { vidLong = Long.parseLong(((String) vid).trim()); } catch (Exception ignored) {}
                                            }
                                            if (vidLong != null && vidLong.equals(it.getVariantId())) {
                                                Object vp = vm.get("effectivePrice");
                                                if (vp == null) vp = vm.get("price");
                                                if (vp == null) vp = vm.get("unitPrice");
                                                if (vp == null) vp = vm.get("finalPrice");
                                                if (vp instanceof Number) { it.setUnitPrice(((Number) vp).doubleValue()); priceSet = true; changed = true; }
                                                else if (vp instanceof String) { try { it.setUnitPrice(Double.parseDouble(((String) vp).trim())); priceSet = true; changed = true; } catch (Exception ignored) {} }
                                                if (priceSet) break;
                                            }
                                        }
                                    }
                                }
                                if (!priceSet) {
                                    Object priceObj = pd.get("effectivePrice");
                                    if (priceObj == null) priceObj = pd.get("price");
                                    if (priceObj == null) priceObj = pd.get("unitPrice");
                                    if (priceObj == null) priceObj = pd.get("finalPrice");
                                    if (priceObj instanceof Number) { it.setUnitPrice(((Number) priceObj).doubleValue()); changed = true; }
                                    else if (priceObj instanceof String) { try { it.setUnitPrice(Double.parseDouble(((String) priceObj).trim())); changed = true; } catch (Exception ignored) {} }
                                }
                            }

                            // imageUrl
                            if (TextUtils.isEmpty(it.getImageUrl())) {
                                Object imagesObj = pd.get("images");
                                String resolved  = null;
                                if (imagesObj instanceof java.util.List && !((java.util.List<?>) imagesObj).isEmpty()) {
                                    Object first = ((java.util.List<?>) imagesObj).get(0);
                                    if (first != null) resolved = String.valueOf(first);
                                }
                                if (resolved == null && pd.get("imageUrl") != null)
                                    resolved = String.valueOf(pd.get("imageUrl"));
                                if (resolved != null) { it.setImageUrl(resolved); changed = true; }
                            }

                            // variantName
                            if (TextUtils.isEmpty(it.getVariantName()) && it.getVariantId() != null) {
                                Object variantsObj = pd.get("variants");
                                if (variantsObj instanceof java.util.List) {
                                    for (Object vv : (java.util.List<?>) variantsObj) {
                                        if (!(vv instanceof java.util.Map)) continue;
                                        java.util.Map<?,?> vm = (java.util.Map<?,?>) vv;
                                        Object vid = vm.get("id"), vname = vm.get("name");
                                        if (vid == null || vname == null) continue;
                                        Long vidLong = null;
                                        if (vid instanceof Number) vidLong = ((Number) vid).longValue();
                                        else if (vid instanceof String) { try { vidLong = Long.parseLong(((String) vid).trim()); } catch (Exception ignored) {} }
                                        if (vidLong != null && vidLong.equals(it.getVariantId())) {
                                            it.setVariantName(String.valueOf(vname));
                                            changed = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        if (changed) {
                            if (itemAdapter != null) itemAdapter.notifyDataSetChanged();
                            recalculate();
                        }
                    });
        }
    }

    private void advanceNextDeliveryDate() {
        if (loadedSubscription == null || loadedSubscription.getFirestoreId() == null) return;

        // 1. Resolve interval days
        int interval = 7; // default: weekly
        if (loadedSubscription.getIntervalDays() != null && loadedSubscription.getIntervalDays() > 0) {
            interval = loadedSubscription.getIntervalDays();
        } else if (loadedSubscription.getFrequency() != null) {
            String freq = loadedSubscription.getFrequency().toUpperCase(Locale.getDefault());
            if (freq.contains("BI") || freq.contains("2W") || freq.contains("BIWEEKLY")) {
                interval = 14;
            }
        }

        // 2. Determine base date: use existing nextDate, fall back to today
        LocalDate base;
        try {
            base = (nextDate != null && !nextDate.isEmpty())
                    ? LocalDate.parse(nextDate, DateTimeFormatter.ISO_LOCAL_DATE)
                    : LocalDate.now();
        } catch (Exception e) {
            base = LocalDate.now();
        }

        // 3. Advance by interval
        LocalDate newDate = base.plusDays(interval);
        String newDateStr = newDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 4. Update in-memory state so recalculate() stays consistent if sheet is reused
        nextDate = newDateStr;
        if (loadedSubscription != null) {
            loadedSubscription.setNextDeliveryDate(newDateStr);
        }
        if (tvDeliveryDate != null && isAdded()) {
            tvDeliveryDate.setText(getString(R.string.delivery_scheduled, newDateStr));
        }

        // 5. Patch Firestore
        Map<String, Object> patch = new HashMap<>();
        patch.put("next_delivery_date", newDateStr);
        patch.put("nextDeliveryDate",   newDateStr);   // both field names for safety
        patch.put("updatedAt",          FieldValue.serverTimestamp());

        db.collection("grocery_subscriptions")
                .document(loadedSubscription.getFirestoreId())
                .set(patch, SetOptions.merge())
                .addOnSuccessListener(unused ->
                        android.util.Log.i("SUB_ADVANCE", "Next delivery advanced to " + newDateStr))
                .addOnFailureListener(e ->
                        android.util.Log.e("SUB_ADVANCE", "Failed to advance next delivery date: " + e.getMessage()));

        // 6. Sync to MySQL (best-effort)
        if (loadedSubscription.getId() != null && apiService != null) {
            final String finalDate = newDateStr;
            final long subId = loadedSubscription.getId();
            Map<String, Object> mysqlPatch = new HashMap<>();
            mysqlPatch.put("nextDeliveryDate", finalDate);
            // Re-use the existing updateSubscriptionStatus endpoint if no dedicated date endpoint exists,
            // otherwise call a dedicated endpoint like apiService.updateNextDeliveryDate(subId, finalDate)
            apiService.updateNextDeliveryDate(subId, finalDate).enqueue(new retrofit2.Callback<Void>() {
                @Override
                public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                    if (!response.isSuccessful())
                        android.util.Log.w("SUB_ADVANCE", "MySQL date update failed: HTTP " + response.code());
                }
                @Override
                public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                    android.util.Log.e("SUB_ADVANCE", "MySQL date update error: " + t.getMessage());
                }
            });
        }
    }
    // ════════════════════════════════════════════════════════════════════════════
    // Payment Method Bottom Sheet
    // ════════════════════════════════════════════════════════════════════════════

    public static class PaymentMethodSheet extends BottomSheetDialogFragment {

        public interface OnMethodSelected { void onSelected(String method); }

        private final OnMethodSelected callback;

        public PaymentMethodSheet(OnMethodSelected cb) { this.callback = cb; }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater,
                                 @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.dialog_paymethod, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            MaterialCardView cardCard   = view.findViewById(R.id.cardPaymentCard);
            MaterialCardView cardCOD    = view.findViewById(R.id.cardPaymentCOD);
            MaterialButton   btnConfirm = view.findViewById(R.id.btnConfirmPayment);
            ImageView ivCardSel  = view.findViewById(R.id.ivCardSelected);
            ImageView        ivCODSel   = view.findViewById(R.id.ivCODSelected);

            android.content.Context ctx = view.getContext();
            int colorPrimary            = androidx.core.content.ContextCompat.getColor(ctx, R.color.md_theme_primary);
            int colorPrimaryContainer   = androidx.core.content.ContextCompat.getColor(ctx, R.color.md_theme_primaryContainer);
            int colorSurfaceLow         = androidx.core.content.ContextCompat.getColor(ctx, R.color.md_theme_surfaceContainerLow);
            int colorOutlineVariant     = androidx.core.content.ContextCompat.getColor(ctx, R.color.md_theme_outlineVariant);
            float density               = ctx.getResources().getDisplayMetrics().density;
            int dp3 = Math.round(3 * density);
            int dp1 = Math.round(1 * density);

            final String[] chosen = {"CARD"};

            // Apply selected/unselected appearance to a card + its icon
            Runnable selectCard = () -> {
                if (cardCard != null) {
                    cardCard.setStrokeWidth(dp3);
                    cardCard.setStrokeColor(android.content.res.ColorStateList.valueOf(colorPrimary));
                    cardCard.setCardBackgroundColor(colorPrimaryContainer);
                }
                if (cardCOD != null) {
                    cardCOD.setStrokeWidth(dp1);
                    cardCOD.setStrokeColor(android.content.res.ColorStateList.valueOf(colorOutlineVariant));
                    cardCOD.setCardBackgroundColor(colorSurfaceLow);
                }
                if (ivCardSel != null) { ivCardSel.setImageResource(R.drawable.ic_check_circle);    ivCardSel.setImageTintList(android.content.res.ColorStateList.valueOf(colorPrimary)); }
                if (ivCODSel  != null) { ivCODSel.setImageResource(R.drawable.ic_radio_unchecked);  ivCODSel.setImageTintList(android.content.res.ColorStateList.valueOf(colorOutlineVariant)); }
            };

            Runnable selectCOD = () -> {
                if (cardCOD != null) {
                    cardCOD.setStrokeWidth(dp3);
                    cardCOD.setStrokeColor(android.content.res.ColorStateList.valueOf(colorPrimary));
                    cardCOD.setCardBackgroundColor(colorPrimaryContainer);
                }
                if (cardCard != null) {
                    cardCard.setStrokeWidth(dp1);
                    cardCard.setStrokeColor(android.content.res.ColorStateList.valueOf(colorOutlineVariant));
                    cardCard.setCardBackgroundColor(colorSurfaceLow);
                }
                if (ivCODSel  != null) { ivCODSel.setImageResource(R.drawable.ic_check_circle);    ivCODSel.setImageTintList(android.content.res.ColorStateList.valueOf(colorPrimary)); }
                if (ivCardSel != null) { ivCardSel.setImageResource(R.drawable.ic_radio_unchecked); ivCardSel.setImageTintList(android.content.res.ColorStateList.valueOf(colorOutlineVariant)); }
            };

            // Default: CARD selected
            selectCard.run();

            if (cardCard != null) cardCard.setOnClickListener(v -> { chosen[0] = "CARD"; selectCard.run(); });
            if (cardCOD  != null) cardCOD.setOnClickListener(v  -> { chosen[0] = "COD";  selectCOD.run(); });

            if (btnConfirm != null) btnConfirm.setOnClickListener(v -> {
                dismiss();
                if (callback != null) callback.onSelected(chosen[0]);
            });
        }
    }
}

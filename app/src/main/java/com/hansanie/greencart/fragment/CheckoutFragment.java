package com.hansanie.greencart.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.hansanie.greencart.BuildConfig;
import com.hansanie.greencart.R;
import com.hansanie.greencart.adapter.AddressAdapter;
import com.hansanie.greencart.adapter.PaymentCardAdapter;
import com.hansanie.greencart.dto.SubscriptionItemUpsertRequest;
import com.hansanie.greencart.dto.SubscriptionSaveRequest;
import com.hansanie.greencart.model.Address;
import com.hansanie.greencart.model.AddressType;
import com.hansanie.greencart.model.CartItem;
import com.hansanie.greencart.model.GrocerySubscription;
import com.hansanie.greencart.model.GrocerySubscriptionItem;
import com.hansanie.greencart.model.SubscriptionOrder;
import com.hansanie.greencart.model.Offer;
import com.hansanie.greencart.database.AppDatabase;
import com.hansanie.greencart.model.Order;
import com.hansanie.greencart.model.OrderItem;
import com.hansanie.greencart.model.Payment;
import com.hansanie.greencart.model.PaymentCard;
import com.hansanie.greencart.model.ProductStockRequest;
import com.hansanie.greencart.network.ApiService;
import com.hansanie.greencart.network.CartManager;
import com.hansanie.greencart.network.RetrofitClient;
import com.hansanie.greencart.util.CustomToast;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CheckoutFragment extends Fragment {
    private String appliedPromoCode;
    private double promoCodeDiscount = 0.0; // Store promo code discount amount

    // Promo code fields
    private Long appliedPromoOfferId = null;
    private double appliedPromoDiscountPercent = 0.0;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 2010;
    private static final String PAYMENT_CARD = "CARD";
    private static final String PAYMENT_CASH = "CASH";
    private static final String ORDER_STATUS_PENDING = "pending";
    private static final String ORDER_ROW_STATUS_PENDING = "pending";
    private static final String PAYMENT_STATUS_PAID = "paid";
    private static final String PAYMENT_STATUS_UNPAID = "unpaid";
    private static final double SHIPPING_LKR = 300.00;
    private static final double SUBSCRIBE_DISCOUNT_PERCENT = 5.0;
    private static final String SUBSCRIPTION_FREQ_WEEKLY = "WEEKLY";
    private static final String SUBSCRIPTION_FREQ_BI_WEEKLY = "BI_WEEKLY";
    private static final String[] WEEK_DAYS = new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final double GREEN_POINT_VALUE_LKR = 1.0;
    private static final int MIN_REDEEM_POINTS = 10;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter ORDER_CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String DEFAULT_HUB_NAME = "Green Cart Hub";
    private static final double DEFAULT_HUB_LAT = 6.9156395;
    private static final double DEFAULT_HUB_LON = 79.8667641;

    private static final String ARG_BUY_NOW_MODE = "arg_buy_now_mode";
    private static final String ARG_PRODUCT_NAME = "arg_product_name";
    private static final String ARG_VARIANT_NAME = "arg_variant_name";
    private static final String ARG_PRODUCT_PRICE = "arg_product_price";
    private static final String ARG_PRODUCT_QTY = "arg_product_qty";
    private static final String ARG_PRODUCT_ID = "arg_product_id";
    private static final String ARG_VARIANT_ID = "arg_variant_id";          // FIX: variant FK
    private static final String ARG_PRODUCT_IMAGE = "arg_product_image";
    private static final String ARG_SUBSCRIPTION_ITEM = "arg_subscription_item";
    private static final String ARG_SUBSCRIPTION_FREQUENCY = "arg_subscription_frequency";

    private ImageButton btnBack;
    private ImageButton btnChangeLocation;
    private ImageButton btnChangeCard;
    private ImageButton btnChangeBilling;
    private MaterialButton btnAddAnotherAddress;
    private MaterialButton btnAddBillingAddress;
    private SwitchMaterial switchSubscription;
    private MaterialCheckBox cbDifferentBilling;
    private MaterialCardView cardPaymentCard;
    private MaterialCardView cardPaymentCash;
    private MaterialCardView cardBillingAddress;
    private MaterialRadioButton radioCard;
    private MaterialRadioButton radioCash;
    private TextView tvSubscriptionHint;
    private TextView btnEditSchedule;
    private TextView tvTotal;
    private TextView tvDiscount;
    private TextView tvPointsBalance;
    private TextView tvPointsRedeemValue;
    private TextView tvSubtotal;
    private TextView tvShipping;
    private TextView tvShippingLabel;
    private TextView tvLocationTitle;
    private TextView tvLocationAddress;
    private TextView tvOfferTitle;
    private TextView tvOfferHint;
    private TextView tvCardDetails;
    private TextView tvBillingAddress;
    private TextView tvDiscountMeta;
    private LinearLayout layoutDiscount;
    private View cardOfferClaim;
    private MaterialButton btnClaimOfferInCheckout;
    private MaterialButton btnApplyPoints;
    private LinearLayout layoutPromoDiscount;
    private TextView tvPromoDiscount;
    private TextInputEditText etPromoCode;
    private TextInputLayout tilPromoCode;
    private TextInputEditText etRedeemPoints;
    private TextInputLayout tilRedeemPoints;
    private MaterialButton btnPlaceOrder;
    private MaterialButton btnClaimPromoCode;

    private final List<CartItem> checkoutItems = new ArrayList<>();
    private final Set<Long> subscriptionEligibleProductIds = new HashSet<>();

    private ApiService apiService;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;
    private Offer checkoutOffer;
    private boolean isCheckoutOfferClaimed = false;
    private Address selectedDeliveryAddress;
    private Address selectedBillingAddress;
    private final List<Address> checkoutAddressList = new ArrayList<>();

    private double subtotalAmount = 0.0;
    private double eligibleSubtotalAmount = 0.0;
    private boolean isBuyNowMode = false;
    private boolean hasSubscriptionEligibleItem = false;
    private boolean subscriptionToggleTouched = false;
    private boolean subscriptionScheduleConfigured = false;
    private String selectedSubscriptionFrequency = SUBSCRIPTION_FREQ_WEEKLY;
    private String selectedSubscriptionDeliveryDay = "Monday";
    private String selectedPaymentMethod = PAYMENT_CARD;
    private int availableGreenPoints = 0;
    private int pointsToRedeem = 0;
    private final List<PaymentCard> savedCards = new ArrayList<>();
    @Nullable
    private PaymentCard selectedSavedCard;
    @Nullable
    private PaymentCardAdapter paymentCardAdapter;

    private interface OrderCodeCallback {
        void onReady(@NonNull String orderCode);
        void onError(@NonNull String message);
    }

    private interface DeliveryDaySelectCallback {
        void onSelected(@NonNull String day);
    }

    private final ActivityResultLauncher<Intent> paymentResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            this::handlePayHereResult
    );

    private enum AddressSelectionTarget {
        DELIVERY,
        BILLING
    }

    public CheckoutFragment() {
        // Required empty public constructor
    }

    public static CheckoutFragment newBuyNowInstance(Long productId,
                                                     Long variantId,
                                                     String productName,
                                                     String variantName,
                                                     double price,
                                                     int quantity,
                                                     String imageUrl,
                                                     boolean isSubscriptionItem,
                                                     @Nullable String subscriptionFrequency) {
        CheckoutFragment fragment = new CheckoutFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_BUY_NOW_MODE, true);
        args.putLong(ARG_PRODUCT_ID, productId != null ? productId : 0L);
        if (variantId != null) {
            args.putLong(ARG_VARIANT_ID, variantId);
        }
        args.putString(ARG_PRODUCT_NAME, productName);
        args.putString(ARG_VARIANT_NAME, variantName);
        args.putDouble(ARG_PRODUCT_PRICE, price);
        args.putInt(ARG_PRODUCT_QTY, quantity);
        args.putString(ARG_PRODUCT_IMAGE, imageUrl);
        args.putBoolean(ARG_SUBSCRIPTION_ITEM, isSubscriptionItem);
        args.putString(ARG_SUBSCRIPTION_FREQUENCY, subscriptionFrequency);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_checkout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        apiService = RetrofitClient.getApiService();
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        isBuyNowMode = getArguments() != null && getArguments().getBoolean(ARG_BUY_NOW_MODE, false);

        initViews(view);
        setupClickListeners();
        loadCheckoutData();
        loadDefaultAddress();
        loadCheckoutOffers();
        loadSavedCards();
        loadGreenPointsWallet();
    }

    private void initViews(View view) {
        btnBack = view.findViewById(R.id.btnBack);
        btnChangeLocation = view.findViewById(R.id.btnChangeLocation);
        btnChangeCard = view.findViewById(R.id.btnChangeCard);
        btnChangeBilling = view.findViewById(R.id.btnChangeBilling);
        btnAddAnotherAddress = view.findViewById(R.id.btnAddAnotherAddress);
        btnAddBillingAddress = view.findViewById(R.id.btnAddBillingAddress);
        switchSubscription = view.findViewById(R.id.switchSubscription);
        cbDifferentBilling = view.findViewById(R.id.cbDifferentBilling);
        tvSubscriptionHint = view.findViewById(R.id.tvSubscriptionHint);
        btnEditSchedule = view.findViewById(R.id.btnEditSchedule);
        tvTotal = view.findViewById(R.id.tvTotal);
        tvDiscount = view.findViewById(R.id.tvDiscount);
        tvPointsBalance = view.findViewById(R.id.tvPointsBalance);
        tvPointsRedeemValue = view.findViewById(R.id.tvPointsRedeemValue);
        tvSubtotal = view.findViewById(R.id.tvSubtotal);
        tvShipping = view.findViewById(R.id.tvShipping);
        tvShippingLabel = view.findViewById(R.id.tvShippingLabel);
        tvLocationTitle = view.findViewById(R.id.tvLocationTitle);
        tvLocationAddress = view.findViewById(R.id.tvLocationAddress);
        tvOfferTitle = view.findViewById(R.id.tvOfferTitle);
        tvOfferHint = view.findViewById(R.id.tvOfferHint);
        tvCardDetails = view.findViewById(R.id.tvCardDetails);
        tvBillingAddress = view.findViewById(R.id.tvBillingAddress);
        tvDiscountMeta = view.findViewById(R.id.tvDiscountMeta);
        layoutDiscount = view.findViewById(R.id.layoutDiscount);
        cardOfferClaim = view.findViewById(R.id.cardOfferClaim);
        btnClaimOfferInCheckout = view.findViewById(R.id.btnClaimOfferInCheckout);
        btnApplyPoints = view.findViewById(R.id.btnApplyPoints);
        layoutPromoDiscount = view.findViewById(R.id.layoutPromoDiscount);
        tvPromoDiscount = view.findViewById(R.id.tvPromoDiscount);
        etPromoCode = view.findViewById(R.id.etPromoCode);
        tilPromoCode = view.findViewById(R.id.tilPromoCode);
        etRedeemPoints = view.findViewById(R.id.etRedeemPoints);
        tilRedeemPoints = view.findViewById(R.id.tilRedeemPoints);
        btnPlaceOrder = view.findViewById(R.id.btnPlaceOrder);
        cardPaymentCard = view.findViewById(R.id.cardPaymentCard);
        cardPaymentCash = view.findViewById(R.id.cardPaymentCash);
        cardBillingAddress = view.findViewById(R.id.cardBillingAddress);
        radioCard = view.findViewById(R.id.radioCard);
        radioCash = view.findViewById(R.id.radioCash);
        btnClaimPromoCode = view.findViewById(R.id.btnClaimPromoCode);

        tvShipping.setText(formatCurrency(calculateShippingFee()));
        if (cardBillingAddress != null) {
            cardBillingAddress.setVisibility(View.GONE);
        }
        if (cbDifferentBilling != null) {
            cbDifferentBilling.setChecked(false);
        }
        selectedSubscriptionDeliveryDay = toDayLabel(LocalDate.now().getDayOfWeek());
        refreshSubscribeHint();
        refreshGreenPointsUi();
        selectPaymentMethod(PAYMENT_CARD, false);
        updateTotal();
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        switchSubscription.setOnCheckedChangeListener((buttonView, isChecked) -> {
            subscriptionToggleTouched = true;
            tvSubscriptionHint.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            btnEditSchedule.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked && PAYMENT_CASH.equals(selectedPaymentMethod)) {
                CustomToast.showWarning(getContext(), "Subscriptions require Card payments for recurring orders");
                selectPaymentMethod(PAYMENT_CARD, false);
            }
            if (isChecked && !subscriptionScheduleConfigured) {
                showSubscribeSaveBottomSheet();
            }
            refreshSubscribeHint();
            updateTotal();
        });

        btnEditSchedule.setOnClickListener(v -> showSubscribeSaveBottomSheet());

        cardPaymentCard.setOnClickListener(v -> selectPaymentMethod(PAYMENT_CARD, true));
        cardPaymentCash.setOnClickListener(v -> selectPaymentMethod(PAYMENT_CASH, true));
        radioCard.setOnClickListener(v -> selectPaymentMethod(PAYMENT_CARD, true));
        radioCash.setOnClickListener(v -> selectPaymentMethod(PAYMENT_CASH, true));

        cbDifferentBilling.setOnCheckedChangeListener((buttonView, isChecked) -> {
            cardBillingAddress.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (btnAddBillingAddress != null) {
                btnAddBillingAddress.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
            if (!isChecked) {
                selectedBillingAddress = selectedDeliveryAddress;
                if (selectedBillingAddress != null) {
                    tvBillingAddress.setText(selectedBillingAddress.getFullAddress());
                }
            } else if (selectedBillingAddress == null) {
                loadAlternateBillingAddress();
            }
        });

        btnPlaceOrder.setOnClickListener(v -> placeOrder());

        if (btnApplyPoints != null) {
            btnApplyPoints.setOnClickListener(v -> applyRedeemPointsFromInput());
        }
        if (etRedeemPoints != null) {
            etRedeemPoints.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (tilRedeemPoints != null) {
                        tilRedeemPoints.setError(null);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        btnChangeLocation.setOnClickListener(v -> fetchLiveLocationAndUpdateAddress());
        if (btnAddAnotherAddress != null) {
            btnAddAnotherAddress.setOnClickListener(v -> showAddressBottomSheet(AddressSelectionTarget.DELIVERY));
        }
        if (btnAddBillingAddress != null) {
            btnAddBillingAddress.setOnClickListener(v -> showAddressBottomSheet(AddressSelectionTarget.BILLING));
        }
        btnChangeCard.setOnClickListener(v -> showPaymentMethodsBottomSheet());
        if (btnChangeBilling != null) {
            btnChangeBilling.setOnClickListener(v -> showAddressBottomSheet(AddressSelectionTarget.BILLING));
        }
        if (btnClaimOfferInCheckout != null) {
            btnClaimOfferInCheckout.setOnClickListener(v -> claimCheckoutOffer());
        }
        if (btnClaimPromoCode != null) {
            btnClaimPromoCode.setOnClickListener(v -> applyPromoCode());
        }
    }

    private void showSubscribeSaveBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_subscribe_save, null);
        dialog.setContentView(sheet);

        ImageButton btnClose = sheet.findViewById(R.id.btnCloseSubscribeSheet);
        RadioGroup rgFrequency = sheet.findViewById(R.id.rgSubscribeFrequency);
        LinearLayout daysRow = sheet.findViewById(R.id.layoutDeliveryDays);
        TextView tvYouSave = sheet.findViewById(R.id.tvSubscribeYouSave);
        TextView tvPoints = sheet.findViewById(R.id.tvSubscribePoints);
        MaterialButton btnConfirm = sheet.findViewById(R.id.btnConfirmSubscribe);

        rgFrequency.check(SUBSCRIPTION_FREQ_BI_WEEKLY.equals(selectedSubscriptionFrequency)
                ? R.id.rbBiWeekly
                : R.id.rbWeekly);
        bindDeliveryDayButtons(daysRow, selectedSubscriptionDeliveryDay, day -> {
            selectedSubscriptionDeliveryDay = day;
            updateSubscribePreviewText(tvYouSave, tvPoints);
        });
        updateSubscribePreviewText(tvYouSave, tvPoints);

        rgFrequency.setOnCheckedChangeListener((group, checkedId) -> {
            selectedSubscriptionFrequency = checkedId == R.id.rbBiWeekly
                    ? SUBSCRIPTION_FREQ_BI_WEEKLY
                    : SUBSCRIPTION_FREQ_WEEKLY;
            updateSubscribePreviewText(tvYouSave, tvPoints);
        });

        btnClose.setOnClickListener(v -> {
            if (switchSubscription != null && !subscriptionScheduleConfigured) {
                switchSubscription.setChecked(false);
            }
            dialog.dismiss();
        });

        btnConfirm.setOnClickListener(v -> {
            subscriptionScheduleConfigured = true;
            refreshSubscribeHint();
            updateTotal();
            CustomToast.showSuccess(getContext(), "Subscription schedule saved");
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateSubscribePreviewText(@NonNull TextView tvYouSave, @NonNull TextView tvPoints) {
        double saving = calculateSubscribeSavingsPreview();
        int points = calculateSubscriptionBonusPoints();
        tvYouSave.setText("You Save: " + formatCurrency(saving));
        tvPoints.setText(String.format(Locale.getDefault(), "Points: +%d", points));
    }

    private void bindDeliveryDayButtons(@NonNull LinearLayout container,
                                        @NonNull String selectedDay,
                                        @NonNull DeliveryDaySelectCallback onSelected) {
        container.removeAllViews();
        for (String day : WEEK_DAYS) {
            MaterialButton button = new MaterialButton(
                    requireContext(),
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle
            );

            button.setText(day.substring(0, 3));
            button.setAllCaps(false);
            button.setCornerRadius(dpToPx(50));

            // Font & size
            button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);

            // Padding
            int hPad = dpToPx(12);
            int vPad = dpToPx(8);
            button.setPadding(hPad, vPad, hPad, vPad);

            // Margin between chips
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMarginEnd(dpToPx(8));
            button.setLayoutParams(lp);

            // ---- SELECTED vs UNSELECTED styling ----
            boolean isSelected = day.equalsIgnoreCase(selectedDay);
            applyChipStyle(button, isSelected);

            button.setOnClickListener(v -> {
                onSelected.onSelected(day);
                // Re-draw all chips with updated selection
                bindDeliveryDayButtons(container, day, onSelected);
            });

            container.addView(button);
        }
    }

    // Helper: apply fill color for selected, outline for unselected
    private void applyChipStyle(@NonNull MaterialButton button, boolean selected) {
        int primaryColor   = ContextCompat.getColor(requireContext(), R.color.md_theme_primary);
        int onPrimaryColor = ContextCompat.getColor(requireContext(), R.color.md_theme_onPrimary);
        int surfaceColor   = ContextCompat.getColor(requireContext(), R.color.md_theme_surface);
        int outlineColor   = ContextCompat.getColor(requireContext(), R.color.md_theme_outline);
        int onSurfaceColor = ContextCompat.getColor(requireContext(), R.color.md_theme_onSurface);

        if (selected) {
            // Filled primary chip
            button.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(primaryColor));
            button.setTextColor(onPrimaryColor);
            button.setStrokeColor(
                    android.content.res.ColorStateList.valueOf(primaryColor));
            button.setStrokeWidth(dpToPx(2));
        } else {
            // Outlined unselected chip
            button.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(surfaceColor));
            button.setTextColor(onSurfaceColor);
            button.setStrokeColor(
                    android.content.res.ColorStateList.valueOf(outlineColor));
            button.setStrokeWidth(dpToPx(1));
        }
    }

    // Helper: dp → px
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void refreshSubscribeHint() {
        if (tvSubscriptionHint == null || btnEditSchedule == null || switchSubscription == null) {
            return;
        }
        if (!switchSubscription.isChecked()) {
            tvSubscriptionHint.setText("Enable to get free delivery + 5% off + bonus points");
            btnEditSchedule.setText("Choose Schedule");
            return;
        }
        String prettyFrequency = SUBSCRIPTION_FREQ_BI_WEEKLY.equals(selectedSubscriptionFrequency)
                ? "Every 2 Weeks"
                : "Weekly";
        tvSubscriptionHint.setText(prettyFrequency + " delivery on " + selectedSubscriptionDeliveryDay);
        btnEditSchedule.setText(subscriptionScheduleConfigured ? "Edit Schedule" : "Choose Schedule");
    }

    private void loadGreenPointsWallet() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            availableGreenPoints = 0;
            pointsToRedeem = 0;
            refreshGreenPointsUi();
            updateTotal();
            return;
        }

        db.collection("users")
                .document(uid)
                .collection("wallet")
                .document("green_points")
                .get()
                .addOnSuccessListener(doc -> {
                    Long storedBalance = readLong(doc.get("pointsBalance"));
                    availableGreenPoints = (int) Math.max(0L, storedBalance != null ? storedBalance : 0L);
                    pointsToRedeem = Math.min(pointsToRedeem, availableGreenPoints);
                    refreshGreenPointsUi();
                    updateTotal();
                })
                .addOnFailureListener(e -> {
                    availableGreenPoints = 0;
                    pointsToRedeem = 0;
                    refreshGreenPointsUi();
                    updateTotal();
                });
    }

    private void refreshGreenPointsUi() {
        if (tvPointsBalance != null) {
            tvPointsBalance.setText(String.format(Locale.getDefault(), "Balance: %d pts", availableGreenPoints));
        }
        if (tvPointsRedeemValue != null) {
            double value = pointsToRedeem * GREEN_POINT_VALUE_LKR;
            tvPointsRedeemValue.setText("-" + formatCurrency(value));
        }
        if (etRedeemPoints != null) {
            String current = etRedeemPoints.getText() != null ? etRedeemPoints.getText().toString().trim() : "";
            String expected = pointsToRedeem > 0 ? String.valueOf(pointsToRedeem) : "";
            if (!expected.equals(current)) {
                etRedeemPoints.setText(expected);
                etRedeemPoints.setSelection(expected.length());
            }
        }
    }

    private void applyRedeemPointsFromInput() {
        if (tilRedeemPoints != null) {
            tilRedeemPoints.setError(null);
        }
        int requestedPoints;
        try {
            String raw = etRedeemPoints != null && etRedeemPoints.getText() != null
                    ? etRedeemPoints.getText().toString().trim()
                    : "0";
            requestedPoints = raw.isEmpty() ? 0 : Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            if (tilRedeemPoints != null) {
                tilRedeemPoints.setError("Enter a valid number");
            }
            return;
        }

        if (requestedPoints < 0) {
            if (tilRedeemPoints != null) {
                tilRedeemPoints.setError("Points cannot be negative");
            }
            return;
        }

        if (requestedPoints > 0 && requestedPoints < MIN_REDEEM_POINTS) {
            if (tilRedeemPoints != null) {
                tilRedeemPoints.setError("Minimum redeem is " + MIN_REDEEM_POINTS + " points");
            }
            return;
        }

        if (requestedPoints > availableGreenPoints) {
            if (tilRedeemPoints != null) {
                tilRedeemPoints.setError("Not enough points in wallet");
            }
            return;
        }

        pointsToRedeem = requestedPoints;
        refreshGreenPointsUi();
        updateTotal();
    }

    private void validateGreenPointsBeforeCheckout(@NonNull Runnable onValid) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || pointsToRedeem <= 0) {
            onValid.run();
            return;
        }

        db.collection("users")
                .document(uid)
                .collection("wallet")
                .document("green_points")
                .get()
                .addOnSuccessListener(doc -> {
                    Long storedBalance = readLong(doc.get("pointsBalance"));
                    int latestBalance = (int) Math.max(0L, storedBalance != null ? storedBalance : 0L);
                    if (pointsToRedeem > latestBalance) {
                        availableGreenPoints = latestBalance;
                        pointsToRedeem = 0;
                        refreshGreenPointsUi();
                        updateTotal();
                        CustomToast.showWarning(getContext(), "Points balance changed. Please apply points again.");
                        return;
                    }
                    onValid.run();
                })
                .addOnFailureListener(e -> CustomToast.showWarning(getContext(), "Unable to validate points right now"));
    }

    @NonNull
    private String toDayLabel(@NonNull DayOfWeek day) {
        switch (day) {
            case MONDAY:
                return "Monday";
            case TUESDAY:
                return "Tuesday";
            case WEDNESDAY:
                return "Wednesday";
            case THURSDAY:
                return "Thursday";
            case FRIDAY:
                return "Friday";
            case SATURDAY:
                return "Saturday";
            default:
                return "Sunday";
        }
    }

    private void selectPaymentMethod(@NonNull String paymentMethod, boolean fromUser) {
        if (PAYMENT_CASH.equals(paymentMethod)
                && fromUser
                && switchSubscription != null
                && switchSubscription.isChecked()) {
            CustomToast.showWarning(getContext(), "Card payment is required when subscription is enabled");
            paymentMethod = PAYMENT_CARD;
        }

        selectedPaymentMethod = paymentMethod;
        boolean isCard = PAYMENT_CARD.equals(paymentMethod);
        radioCard.setChecked(isCard);
        radioCash.setChecked(!isCard);

        int primaryColor = ContextCompat.getColor(requireContext(), R.color.md_theme_primary);
        cardPaymentCard.setStrokeWidth(isCard ? 2 : 0);
        cardPaymentCard.setStrokeColor(primaryColor);
        cardPaymentCash.setStrokeWidth(isCard ? 0 : 2);
        cardPaymentCash.setStrokeColor(primaryColor);

        if (tvCardDetails != null) {
            tvCardDetails.setText(isCard ? resolveCardSummaryText() : "Card disabled for this order");
        }
    }

    private void loadSavedCards() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            return;
        }

        db.collection("users").document(uid).collection("payment_cards")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) {
                        return;
                    }

                    savedCards.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        PaymentCard card;
                        try {
                            card = doc.toObject(PaymentCard.class);
                        } catch (RuntimeException ignored) {
                            card = null;
                        }
                        if (card == null) {
                            card = new PaymentCard();
                        }

                        String masked = !TextUtils.isEmpty(card.getCardMasked())
                                ? card.getCardMasked()
                                : readString(doc.get("cardMasked"));
                        if (TextUtils.isEmpty(masked)) {
                            masked = readString(doc.get("maskedNumber"));
                        }
                        if (TextUtils.isEmpty(masked)) {
                            continue;
                        }

                        card.setCardMasked(masked);
                        if (TextUtils.isEmpty(card.getCardBrand())) {
                            card.setCardBrand(readString(doc.get("cardBrand")));
                        }
                        if (TextUtils.isEmpty(card.getCardHolderName())) {
                            card.setCardHolderName(readString(doc.get("cardHolderName")));
                        }
                        if (TextUtils.isEmpty(card.getExpiryDate())) {
                            card.setExpiryDate(readString(doc.get("expiryDate")));
                        }
                        if (!card.isDefault()) {
                            card.setDefault(asBoolean(doc.get("default")) || asBoolean(doc.get("isDefault")));
                        }
                        card.setFirebaseUid(uid);
                        card.setFirestoreDocId(doc.getId());
                        savedCards.add(card);
                    }

                    pickDefaultSavedCard();
                    refreshCardDetailsText();
                    notifySavedCardAdapter();
                    enrichSavedCardIdsFromApi(uid);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    savedCards.clear();
                    pickDefaultSavedCard();
                    refreshCardDetailsText();
                    notifySavedCardAdapter();
                    CustomToast.showWarning(getContext(), "Unable to load saved cards from Firebase");
                });
    }

    private void enrichSavedCardIdsFromApi(@NonNull String uid) {
        apiService.getSavedCards(uid).enqueue(new Callback<List<PaymentCard>>() {
            @Override
            public void onResponse(Call<List<PaymentCard>> call, Response<List<PaymentCard>> response) {
                if (!isAdded() || !response.isSuccessful() || response.body() == null) {
                    return;
                }
                for (PaymentCard apiCard : response.body()) {
                    if (apiCard == null || TextUtils.isEmpty(apiCard.getCardMasked())) {
                        continue;
                    }
                    PaymentCard target = findSavedCardByMasked(apiCard.getCardMasked());
                    if (target != null) {
                        target.setId(apiCard.getId());
                        if (TextUtils.isEmpty(target.getCardBrand())) {
                            target.setCardBrand(apiCard.getCardBrand());
                        }
                        if (TextUtils.isEmpty(target.getCardHolderName())) {
                            target.setCardHolderName(apiCard.getCardHolderName());
                        }
                    }
                }
                notifySavedCardAdapter();
            }

            @Override
            public void onFailure(Call<List<PaymentCard>> call, Throwable t) {
                // Firebase is the source for sheet rendering; API enrichment is optional.
            }
        });
    }

    private void notifySavedCardAdapter() {
        if (paymentCardAdapter != null) {
            paymentCardAdapter.replaceData(savedCards);
        }
    }

    private boolean asBoolean(@Nullable Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean(((String) value).trim());
        }
        return false;
    }

    @Nullable
    private PaymentCard findSavedCardByMasked(@NonNull String masked) {
        for (PaymentCard card : savedCards) {
            if (masked.equalsIgnoreCase(card.getCardMasked())) {
                return card;
            }
        }
        return null;
    }

    private void pickDefaultSavedCard() {
        if (savedCards.isEmpty()) {
            selectedSavedCard = null;
            return;
        }
        for (PaymentCard card : savedCards) {
            if (card.isDefault()) {
                selectedSavedCard = card;
                return;
            }
        }
        selectedSavedCard = savedCards.get(0);
    }

    private void refreshCardDetailsText() {
        if (tvCardDetails == null) {
            return;
        }
        if (PAYMENT_CASH.equals(selectedPaymentMethod)) {
            tvCardDetails.setText("Card disabled for this order");
            return;
        }
        tvCardDetails.setText(resolveCardSummaryText());
    }

    @NonNull
    private String resolveCardSummaryText() {
        if (selectedSavedCard == null) {
            return "No saved card selected";
        }
        String brand = !TextUtils.isEmpty(selectedSavedCard.getCardBrand())
                ? selectedSavedCard.getCardBrand().toUpperCase(Locale.getDefault())
                : "CARD";
        String masked = !TextUtils.isEmpty(selectedSavedCard.getCardMasked())
                ? selectedSavedCard.getCardMasked()
                : "**** **** ****";
        return brand + " " + masked;
    }

    private void showPaymentMethodsBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_payments, null);
        dialog.setContentView(sheet);

        MaterialCardView cardCod = sheet.findViewById(R.id.cardCOD);
        RadioButton rbCod = sheet.findViewById(R.id.rbCOD);
        RecyclerView rvSavedCards = sheet.findViewById(R.id.rvSavedCards);
        MaterialButton btnAddNewCard = sheet.findViewById(R.id.btnAddNewCard);

        rvSavedCards.setLayoutManager(new LinearLayoutManager(getContext()));
        final PaymentCardAdapter[] adapterHolder = new PaymentCardAdapter[1];
        // Build adapter with whatever is already in savedCards (may be empty on first open).
        PaymentCardAdapter adapter = new PaymentCardAdapter(savedCards, card -> deleteSavedCard(card, adapterHolder[0]));
        adapterHolder[0] = adapter;
        paymentCardAdapter = adapter;
        adapter.setOnCardSelectListener(card -> {
            selectedSavedCard = card;
            selectPaymentMethod(PAYMENT_CARD, true);
            dialog.dismiss();
        });
        rvSavedCards.setAdapter(adapter);
        dialog.setOnDismissListener(d -> paymentCardAdapter = null);

        rbCod.setChecked(PAYMENT_CASH.equals(selectedPaymentMethod));
        cardCod.setOnClickListener(v -> {
            selectPaymentMethod(PAYMENT_CASH, true);
            dialog.dismiss();
        });

        btnAddNewCard.setOnClickListener(v -> showAddCardDialog(dialog, adapter));

        // Refresh from Firestore now; notifySavedCardAdapter() will update the adapter when done.
        loadSavedCards();

        dialog.show();
    }

    private void showAddCardDialog(@NonNull BottomSheetDialog parentDialog, @NonNull PaymentCardAdapter adapter) {
        View form = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_payment_card, null);
        EditText etCardNumber = form.findViewById(R.id.etCardNumber);
        EditText etCardExpiry = form.findViewById(R.id.etCardExpiry);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add Card")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .show();

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String rawNumber = etCardNumber.getText() != null ? etCardNumber.getText().toString().replace(" ", "").trim() : "";
            String expiry = etCardExpiry.getText() != null ? etCardExpiry.getText().toString().trim() : "";
            if (rawNumber.length() < 12) {
                etCardNumber.setError("Enter a valid card number");
                return;
            }
            if (!expiry.matches("(0[1-9]|1[0-2])/\\d{2}")) {
                etCardExpiry.setError("Use MM/YY");
                return;
            }

            String uid = FirebaseAuth.getInstance().getUid();
            if (uid == null) {
                CustomToast.showWarning(getContext(), "Please sign in to save cards");
                return;
            }

            PaymentCard card = PaymentCard.builder()
                    .firebaseUid(uid)
                    .cardHolderName("GreenCart User")
                    .cardMasked(maskCardNumber(rawNumber))
                    .cardBrand(detectCardBrand(rawNumber))
                    .expiryDate(expiry)
                    .isDefault(savedCards.isEmpty())
                    .build();

            persistCardToFirebaseAndMySql(card, new Runnable() {
                @Override
                public void run() {
                    savedCards.add(card);
                    selectedSavedCard = card;
                    adapter.notifyDataSetChanged();
                    selectPaymentMethod(PAYMENT_CARD, true);
                    dialog.dismiss();
                    parentDialog.dismiss();
                }
            });
        });
    }

    @NonNull
    private String maskCardNumber(@NonNull String rawNumber) {
        String digits = rawNumber.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return "****";
        }
        String last4 = digits.substring(digits.length() - 4);
        return "**** **** **** " + last4;
    }

    @NonNull
    private String detectCardBrand(@NonNull String rawNumber) {
        String digits = rawNumber.replaceAll("\\D", "");
        if (digits.startsWith("4")) {
            return "VISA";
        }
        if (digits.startsWith("5")) {
            return "MASTERCARD";
        }
        if (digits.startsWith("34") || digits.startsWith("37")) {
            return "AMEX";
        }
        return "CARD";
    }

    private void persistCardToFirebaseAndMySql(@NonNull PaymentCard card, @NonNull Runnable onDone) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            onDone.run();
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("firebaseUid", uid);
        payload.put("cardHolderName", card.getCardHolderName());
        payload.put("cardMasked", card.getCardMasked());
        payload.put("cardBrand", card.getCardBrand());
        payload.put("expiryDate", card.getExpiryDate());
        payload.put("default", card.isDefault());
        payload.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid).collection("payment_cards")
                .add(payload)
                .addOnSuccessListener(ref -> {
                    card.setFirestoreDocId(ref.getId());
                    apiService.saveCard(card).enqueue(new Callback<PaymentCard>() {
                        @Override
                        public void onResponse(Call<PaymentCard> call, Response<PaymentCard> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                card.setId(response.body().getId());
                            }
                            onDone.run();
                        }

                        @Override
                        public void onFailure(Call<PaymentCard> call, Throwable t) {
                            onDone.run();
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    apiService.saveCard(card).enqueue(new Callback<PaymentCard>() {
                        @Override
                        public void onResponse(Call<PaymentCard> call, Response<PaymentCard> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                card.setId(response.body().getId());
                            }
                            onDone.run();
                        }

                        @Override
                        public void onFailure(Call<PaymentCard> call, Throwable t) {
                            onDone.run();
                        }
                    });
                });
    }

    private void deleteSavedCard(@NonNull PaymentCard card, @NonNull PaymentCardAdapter adapter) {
        if (!TextUtils.isEmpty(card.getFirestoreDocId())) {
            String uid = FirebaseAuth.getInstance().getUid();
            if (uid != null) {
                db.collection("users").document(uid).collection("payment_cards")
                        .document(card.getFirestoreDocId())
                        .delete();
            }
        }

        if (card.getId() != null) {
            apiService.deleteCard(card.getId()).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    // no-op
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    // no-op
                }
            });
        }

        savedCards.remove(card);
        if (selectedSavedCard != null && TextUtils.equals(selectedSavedCard.getCardMasked(), card.getCardMasked())) {
            selectedSavedCard = savedCards.isEmpty() ? null : savedCards.get(0);
        }
        adapter.notifyDataSetChanged();
        refreshCardDetailsText();
    }

    private void loadCheckoutData() {
        checkoutItems.clear();
        subscriptionEligibleProductIds.clear();
        hasSubscriptionEligibleItem = false;
        eligibleSubtotalAmount = 0.0;

        if (isBuyNowMode && getArguments() != null) {
            Bundle a = getArguments();
            Long variantId = a.containsKey(ARG_VARIANT_ID) ? a.getLong(ARG_VARIANT_ID) : null;
            CartItem buyNow = new CartItem(
                    0,
                    FirebaseAuth.getInstance().getUid() != null ? FirebaseAuth.getInstance().getUid() : "guest",
                    a.getLong(ARG_PRODUCT_ID, 0L),
                    variantId,
                    a.getString(ARG_PRODUCT_NAME, "Product"),
                    a.getString(ARG_VARIANT_NAME, "Default"),
                    a.getDouble(ARG_PRODUCT_PRICE, 0.0),
                    a.getInt(ARG_PRODUCT_QTY, 1),
                    a.getString(ARG_PRODUCT_IMAGE, ""),
                    a.getBoolean(ARG_SUBSCRIPTION_ITEM, false),
                    a.getString(ARG_SUBSCRIPTION_FREQUENCY)
            );
            checkoutItems.add(buyNow);
            recalculateSubtotal();
            evaluateSubscriptionEligibility();
            updateTotal();
            return;
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "guest";

        CartManager.getCartItems(requireContext(), userId, entities -> {
            checkoutItems.clear();
            for (var entity : entities) {
                checkoutItems.add(CartItem.from(entity));
            }
            recalculateSubtotal();
            evaluateSubscriptionEligibility();
            if (isAdded()) {
                requireActivity().runOnUiThread(this::updateTotal);
            }
        });
    }

    private void recalculateSubtotal() {
        subtotalAmount = 0.0;
        for (CartItem item : checkoutItems) {
            subtotalAmount += item.getPrice() * item.getQuantity();
        }
        tvSubtotal.setText(formatCurrency(subtotalAmount));
    }

    private void evaluateSubscriptionEligibility() {
        for (CartItem item : checkoutItems) {
            if ((item.isSubscriptionItem() || isWeeklyOrDailyVariant(item.getVariantName()))
                    && !subscriptionToggleTouched && switchSubscription != null) {
                switchSubscription.setChecked(true);
                break;
            }
        }

        if (checkoutItems.isEmpty()) {
            hasSubscriptionEligibleItem = false;
            eligibleSubtotalAmount = 0.0;
            updateTotal();
            return;
        }

        for (CartItem item : checkoutItems) {
            long productId = item.getProductId();
            db.collection("products")
                    .whereEqualTo("id", productId)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (!snapshot.isEmpty()) {
                            DocumentSnapshot doc = snapshot.getDocuments().get(0);
                            Boolean eligible = doc.getBoolean("isSubscriptionEligible");
                            if (Boolean.TRUE.equals(eligible)) {
                                subscriptionEligibleProductIds.add(productId);
                            }
                        }
                        recomputeEligibilityFromSignals();
                    })
                    .addOnFailureListener(e -> recomputeEligibilityFromSignals());
        }
    }

    private void recomputeEligibilityFromSignals() {
        hasSubscriptionEligibleItem = false;
        eligibleSubtotalAmount = 0.0;

        for (CartItem item : checkoutItems) {
            if (isItemSubscriptionEligible(item)) {
                hasSubscriptionEligibleItem = true;
                eligibleSubtotalAmount += item.getPrice() * item.getQuantity();
            }
        }
        updateTotal();
    }

    private boolean isItemSubscriptionEligible(@NonNull CartItem item) {
        return item.isSubscriptionItem()
                || subscriptionEligibleProductIds.contains(item.getProductId())
                || isWeeklyOrDailyVariant(item.getVariantName());
    }

    private boolean hasExplicitSubscriptionItem() {
        for (CartItem item : checkoutItems) {
            if (item.isSubscriptionItem()) {
                return true;
            }
        }
        return false;
    }

    private boolean isWeeklyOrDailyVariant(@Nullable String variantName) {
        if (variantName == null) {
            return false;
        }
        String normalized = variantName.toLowerCase(Locale.getDefault());
        return normalized.contains("daily") || normalized.contains("weekly");
    }

    private void loadDefaultAddress() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            return;
        }

        apiService.getUserAddresses(uid).enqueue(new Callback<List<Address>>() {
            @Override
            public void onResponse(Call<List<Address>> call, Response<List<Address>> response) {
                if (!isAdded() || !response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    return;
                }
                Address selected = response.body().get(0);
                for (Address address : response.body()) {
                    if (address.isDefault()) {
                        selected = address;
                        break;
                    }
                }

                selectedDeliveryAddress = selected;
                tvLocationTitle.setText(selected.getTitle() != null ? selected.getTitle() : "Address");
                tvLocationAddress.setText(selected.getFullAddress());

                if (!cbDifferentBilling.isChecked()) {
                    selectedBillingAddress = selected;
                    if (tvBillingAddress != null) {
                        tvBillingAddress.setText(selected.getFullAddress());
                    }
                }
            }

            @Override
            public void onFailure(Call<List<Address>> call, Throwable t) {
                // Keep placeholder if API is unavailable.
            }
        });
    }

    private void loadAlternateBillingAddress() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            return;
        }

        apiService.getUserAddresses(uid).enqueue(new Callback<List<Address>>() {
            @Override
            public void onResponse(Call<List<Address>> call, Response<List<Address>> response) {
                if (!isAdded() || !response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    return;
                }
                Address selected = response.body().get(0);
                for (Address address : response.body()) {
                    if (!address.isDefault()) {
                        selected = address;
                        break;
                    }
                }
                selectedBillingAddress = selected;
                if (tvBillingAddress != null) {
                    tvBillingAddress.setText(selected.getFullAddress());
                }
            }

            @Override
            public void onFailure(Call<List<Address>> call, Throwable t) {
                CustomToast.showWarning(getContext(), "Unable to load billing address");
            }
        });
    }

    private void showAddressBottomSheet(@NonNull AddressSelectionTarget target) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_addresses, null);
        dialog.setContentView(sheet);

        RecyclerView rvAddresses = sheet.findViewById(R.id.rvAddresses);
        LinearLayout layoutForm = sheet.findViewById(R.id.layoutAddressForm);
        TextView sheetTitle = sheet.findViewById(R.id.sheetTitle);
        MaterialButton btnAddNew = sheet.findViewById(R.id.btnAddNewAddress);
        MaterialButton btnCancelAddress = sheet.findViewById(R.id.btnCancelAddress);

        rvAddresses.setLayoutManager(new LinearLayoutManager(getContext()));
        AddressAdapter adapter = new AddressAdapter(checkoutAddressList, new AddressAdapter.OnAddressActionListener() {
            @Override
            public void onEdit(Address address) {
                showAddressForm(layoutForm, rvAddresses, btnAddNew, sheetTitle, address, dialog, target);
            }

            @Override
            public void onDelete(Address address) {
                // Checkout sheet keeps delete disabled to avoid accidental removal.
            }
        });
        adapter.setOnAddressSelectListener(address -> {
            applySelectedAddress(address, target);
            dialog.dismiss();
        });
        rvAddresses.setAdapter(adapter);

        loadAddressListForCheckout(adapter);

        btnAddNew.setOnClickListener(v -> showAddressForm(layoutForm, rvAddresses, btnAddNew, sheetTitle, null, dialog, target));
        btnCancelAddress.setOnClickListener(v -> hideAddressForm(layoutForm, rvAddresses, btnAddNew, sheetTitle));

        dialog.show();
    }

    private void loadAddressListForCheckout(@NonNull AddressAdapter adapter) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            return;
        }

        db.collection("users").document(uid).collection("addresses")
                .get()
                .addOnSuccessListener(snapshot -> {
                    checkoutAddressList.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Address address = doc.toObject(Address.class);
                        if (address == null) {
                            continue;
                        }
                        address.setFirestoreDocId(doc.getId());
                        checkoutAddressList.add(address);
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    private void applySelectedAddress(@NonNull Address address, @NonNull AddressSelectionTarget target) {
        if (target == AddressSelectionTarget.DELIVERY) {
            selectedDeliveryAddress = address;
            tvLocationTitle.setText(address.getTitle() != null ? address.getTitle() : "Address");
            tvLocationAddress.setText(address.getFullAddress());
            if (!cbDifferentBilling.isChecked()) {
                selectedBillingAddress = address;
                tvBillingAddress.setText(address.getFullAddress());
            }
            return;
        }

        selectedBillingAddress = address;
        if (tvBillingAddress != null) {
            tvBillingAddress.setText(address.getFullAddress());
        }
    }

    private void showAddressForm(View form,
                                 View rv,
                                 View btnAdd,
                                 TextView title,
                                 @Nullable Address editingAddress,
                                 BottomSheetDialog dialog,
                                 AddressSelectionTarget target) {
        title.setText(editingAddress == null ? "New Address" : "Edit Address");
        rv.setVisibility(View.GONE);
        btnAdd.setVisibility(View.GONE);
        form.setVisibility(View.VISIBLE);

        TextInputLayout tilTitle = form.findViewById(R.id.tilAddressTitle);
        TextInputLayout tilLine1 = form.findViewById(R.id.tilAddressLine1);
        TextInputLayout tilLine2 = form.findViewById(R.id.tilAddressLine2);
        TextInputLayout tilCity = form.findViewById(R.id.tilCity);
        TextInputLayout tilPost = form.findViewById(R.id.tilPostalCode);

        EditText etAddrTitle = form.findViewById(R.id.etAddressTitle);
        EditText etLine1 = form.findViewById(R.id.etAddressLine1);
        EditText etLine2 = form.findViewById(R.id.etAddressLine2);
        EditText etCity = form.findViewById(R.id.etCity);
        EditText etPostal = form.findViewById(R.id.etPostalCode);
        RadioGroup rgAddressType = form.findViewById(R.id.rgAddressType);

        setupErrorClearer(etAddrTitle, tilTitle);
        setupErrorClearer(etLine1, tilLine1);
        setupErrorClearer(etLine2, tilLine2);
        setupErrorClearer(etCity, tilCity);
        setupErrorClearer(etPostal, tilPost);

        if (editingAddress != null) {
            etAddrTitle.setText(editingAddress.getTitle());
            etLine1.setText(editingAddress.getAddressLine1());
            etLine2.setText(editingAddress.getAddressLine2());
            etCity.setText(editingAddress.getCity());
            etPostal.setText(editingAddress.getPostalCode());

            AddressType existingType = AddressType.fromValue(editingAddress.getAddressType());
            if (existingType == AddressType.BILLING) {
                rgAddressType.check(R.id.rbTypeBilling);
            } else if (existingType == AddressType.SHIPPING) {
                rgAddressType.check(R.id.rbTypeShipping);
            } else {
                rgAddressType.check(R.id.rbTypeBoth);
            }
        } else {
            rgAddressType.check(target == AddressSelectionTarget.BILLING ? R.id.rbTypeBilling : R.id.rbTypeShipping);
            etAddrTitle.setText(target == AddressSelectionTarget.BILLING ? "Billing" : "Home");
        }

        form.findViewById(R.id.btnUseCurrentLocation).setOnClickListener(v -> fetchLocationForForm(etLine1, etLine2, etCity, etPostal));

        form.findViewById(R.id.btnSaveAddress).setOnClickListener(v -> {
            boolean isValid = true;
            if (TextUtils.isEmpty(etAddrTitle.getText().toString().trim())) {
                showShakeError(tilTitle, "Address title is required");
                isValid = false;
            }
            if (TextUtils.isEmpty(etLine1.getText().toString().trim())) {
                showShakeError(tilLine1, "Enter house number or name");
                isValid = false;
            }
            if (TextUtils.isEmpty(etLine2.getText().toString().trim())) {
                showShakeError(tilLine2, "Enter street or area");
                isValid = false;
            }
            if (TextUtils.isEmpty(etCity.getText().toString().trim())) {
                showShakeError(tilCity, "Enter city");
                isValid = false;
            }
            if (TextUtils.isEmpty(etPostal.getText().toString().trim())) {
                showShakeError(tilPost, "Enter postal code");
                isValid = false;
            }
            if (!isValid) {
                return;
            }

            Address address = editingAddress == null ? new Address() : editingAddress;
            address.setFirebaseUid(FirebaseAuth.getInstance().getUid());
            address.setTitle(etAddrTitle.getText().toString().trim());
            address.setAddressLine1(etLine1.getText().toString().trim());
            address.setAddressLine2(etLine2.getText().toString().trim());
            address.setCity(etCity.getText().toString().trim());
            address.setPostalCode(etPostal.getText().toString().trim());
            address.setAddressType(getSelectedAddressType(rgAddressType).name());
            if (address.getCreatedAt() == null) {
                address.setCreatedAt(LocalDateTime.now());
            }
            address.setUpdatedAt(LocalDateTime.now());

            askDefaultAndSaveAddress(address, dialog, target);
        });
    }

    private void hideAddressForm(View form, View rv, View btnAdd, TextView title) {
        form.setVisibility(View.GONE);
        rv.setVisibility(View.VISIBLE);
        btnAdd.setVisibility(View.VISIBLE);
        title.setText("My Addresses");
    }

    private AddressType getSelectedAddressType(RadioGroup rgAddressType) {
        int selectedId = rgAddressType.getCheckedRadioButtonId();
        if (selectedId == R.id.rbTypeBilling) {
            return AddressType.BILLING;
        }
        if (selectedId == R.id.rbTypeShipping) {
            return AddressType.SHIPPING;
        }
        return AddressType.BOTH;
    }

    private void askDefaultAndSaveAddress(Address address, BottomSheetDialog dialog, AddressSelectionTarget target) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Default Address")
                .setMessage("Set this as default address?")
                .setPositiveButton("Yes", (d, i) -> {
                    address.setDefault(true);
                    saveAddressWithDefaultHandling(address, dialog, target);
                })
                .setNegativeButton("No", (d, i) -> {
                    address.setDefault(false);
                    saveAddressWithDefaultHandling(address, dialog, target);
                })
                .show();
    }

    private void saveAddressWithDefaultHandling(Address address, BottomSheetDialog dialog, AddressSelectionTarget target) {
        if (address.isDefault()) {
            unsetOtherDefaultAddresses(address, () -> saveAddress(address, dialog, target));
            return;
        }
        saveAddress(address, dialog, target);
    }

    private void unsetOtherDefaultAddresses(Address selectedAddress, Runnable onComplete) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            onComplete.run();
            return;
        }

        db.collection("users").document(uid).collection("addresses")
                .whereEqualTo("default", true)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        onComplete.run();
                        return;
                    }
                    final int total = snapshot.size();
                    final int[] done = {0};
                    for (DocumentSnapshot doc : snapshot) {
                        Address address = doc.toObject(Address.class);
                        if (address == null) {
                            done[0]++;
                            if (done[0] == total) {
                                onComplete.run();
                            }
                            continue;
                        }
                        boolean same = selectedAddress.getFirestoreDocId() != null
                                && selectedAddress.getFirestoreDocId().equals(doc.getId());
                        if (same) {
                            done[0]++;
                            if (done[0] == total) {
                                onComplete.run();
                            }
                            continue;
                        }
                        address.setDefault(false);
                        address.setFirestoreDocId(doc.getId());
                        address.setUpdatedAt(LocalDateTime.now());
                        doc.getReference().set(address).addOnCompleteListener(t -> {
                            done[0]++;
                            if (done[0] == total) {
                                onComplete.run();
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> onComplete.run());
    }

    private void saveAddress(Address address, BottomSheetDialog dialog, AddressSelectionTarget target) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            return;
        }

        if (address.getFirestoreDocId() == null) {
            db.collection("users").document(uid).collection("addresses")
                    .add(address)
                    .addOnSuccessListener(ref -> {
                        address.setFirestoreDocId(ref.getId());
                        syncAddressToApi(address, dialog, target);
                    })
                    .addOnFailureListener(e -> CustomToast.showError(getContext(), "Failed to save address"));
        } else {
            db.collection("users").document(uid).collection("addresses")
                    .document(address.getFirestoreDocId())
                    .set(address)
                    .addOnSuccessListener(unused -> syncAddressToApi(address, dialog, target))
                    .addOnFailureListener(e -> CustomToast.showError(getContext(), "Failed to update address"));
        }
    }

    private void syncAddressToApi(Address address, BottomSheetDialog dialog, AddressSelectionTarget target) {
        apiService.saveAddress(address).enqueue(new Callback<Address>() {
            @Override
            public void onResponse(Call<Address> call, Response<Address> response) {
                if (response.isSuccessful() && response.body() != null && !TextUtils.isEmpty(response.body().getId())) {
                    address.setId(response.body().getId());
                    persistAddressServerId(address);
                    applySelectedAddress(address, target);
                    dialog.dismiss();
                    CustomToast.showSuccess(getContext(), "Address saved");
                    return;
                }

                applySelectedAddress(address, target);
                dialog.dismiss();
                CustomToast.showWarning(getContext(), "Saved locally. Server sync failed: " + readErrorBody(response));
            }

            @Override
            public void onFailure(Call<Address> call, Throwable t) {
                applySelectedAddress(address, target);
                dialog.dismiss();
                CustomToast.showWarning(getContext(), "Saved locally. API sync failed.");
            }
        });
    }

    private void persistAddressServerId(@NonNull Address address) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || TextUtils.isEmpty(address.getFirestoreDocId())) {
            return;
        }
        db.collection("users")
                .document(uid)
                .collection("addresses")
                .document(address.getFirestoreDocId())
                .set(address, SetOptions.merge());
    }

    private void fetchLocationForForm(EditText etLine1, EditText etLine2, EditText etCity, EditText etPostal) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) {
                return;
            }
            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
            try {
                List<android.location.Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (addresses == null || addresses.isEmpty()) {
                    return;
                }
                android.location.Address address = addresses.get(0);
                etLine1.setText(address.getFeatureName());
                etLine2.setText(address.getThoroughfare());
                etCity.setText(address.getLocality());
                etPostal.setText(address.getPostalCode());
            } catch (Exception ignored) {
                CustomToast.showWarning(getContext(), "Unable to auto-fill location");
            }
        });
    }

    private void setupErrorClearer(EditText editText, TextInputLayout inputLayout) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    inputLayout.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void showShakeError(TextInputLayout til, String message) {
        til.setError(message);
        Animation shake = AnimationUtils.loadAnimation(getContext(), R.anim.shake);
        til.startAnimation(shake);
        if (til.getEditText() != null) {
            til.getEditText().requestFocus();
        }
    }

    private void loadCheckoutOffers() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            checkoutOffer = null;
            isCheckoutOfferClaimed = false;
            if (cardOfferClaim != null) {
                cardOfferClaim.setVisibility(View.GONE);
            }
            updateTotal();
            return;
        }

        apiService.getAvailableOffersForUser(uid).enqueue(new Callback<List<Offer>>() {
            @Override
            public void onResponse(Call<List<Offer>> call, Response<List<Offer>> response) {
                if (!isAdded()) {
                    return;
                }
                checkoutOffer = null;
                isCheckoutOfferClaimed = false;
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    for (Offer offer : response.body()) {
                        if (isActiveSubscriptionOffer(offer)) {
                            checkoutOffer = offer;
                            break;
                        }
                    }
                }

                if (checkoutOffer != null) {
                    showOfferCardAsClaimable();
                } else {
                    if (cardOfferClaim != null) {
                        cardOfferClaim.setVisibility(View.GONE);
                    }
                }
                updateTotal();
            }

            @Override
            public void onFailure(Call<List<Offer>> call, Throwable t) {
                checkoutOffer = null;
                isCheckoutOfferClaimed = false;
                if (cardOfferClaim != null) {
                    cardOfferClaim.setVisibility(View.GONE);
                }
                updateTotal();
            }
        });
    }

    private void loadCheckoutOffersFromFirestore(@NonNull String uid) {
        db.collection("offers")
                .whereEqualTo("status", "active")
                .get()
                .addOnSuccessListener(snapshot -> {
                    Offer found = null;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Offer offer = mapOfferFromDocument(doc);
                        if (!isActiveSubscriptionOffer(offer)) {
                            continue;
                        }
                        found = offer;
                        break;
                    }

                    checkoutOffer = found;
                    if (checkoutOffer != null) {
                        if (hasExplicitSubscriptionItem()) {
                            isCheckoutOfferClaimed = true;
                            showOfferCardAsAutoApplied();
                        } else {
                            isCheckoutOfferClaimed = false;
                            showOfferCardAsClaimable();
                        }
                    } else if (cardOfferClaim != null) {
                        cardOfferClaim.setVisibility(View.GONE);
                    }
                    updateTotal();
                })
                .addOnFailureListener(e -> {
                    checkoutOffer = null;
                    isCheckoutOfferClaimed = false;
                    if (cardOfferClaim != null) {
                        cardOfferClaim.setVisibility(View.GONE);
                    }
                    updateTotal();
                });
    }

    private Offer mapOfferFromDocument(@NonNull DocumentSnapshot doc) {
        Offer offer = new Offer();
        offer.setId(readLong(doc.get("id")));
        offer.setTitle(readString(doc.get("title")));
        offer.setDescription(readString(doc.get("description")));
        offer.setDiscountPercentage(readDouble(doc.get("discountPercentage")));
        offer.setPromoCode(readString(doc.get("promoCode")));
        offer.setImageUrl(readString(doc.get("imageUrl")));
        offer.setStatus(readString(doc.get("status")));
        offer.setPromoType(readString(doc.get("promoType")));
        offer.setMinOrderValue(readDouble(doc.get("minOrderValue")));

        // Backend sometimes stores max cap as maxDiscount instead of maxDiscountAmount.
        Double maxDiscount = readDouble(doc.get("maxDiscountAmount"));
        if (maxDiscount == null) {
            maxDiscount = readDouble(doc.get("maxDiscount"));
        }
        offer.setMaxDiscountAmount(maxDiscount);
        return offer;
    }

    @Nullable
    private String readString(@Nullable Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    @Nullable
    private Double readDouble(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @Nullable
    private Long readLong(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void showOfferCardAsClaimable() {
        if (checkoutOffer == null) {
            return;
        }
        cardOfferClaim.setVisibility(View.VISIBLE);
        double discount = checkoutOffer.getDiscountPercentage() != null
                ? checkoutOffer.getDiscountPercentage()
                : 10.0;
        tvOfferTitle.setText(String.format(Locale.getDefault(), "%.0f%% subscription offer", discount));
        tvOfferHint.setText("Unclaimed offer found. Claim now to apply this discount.");
        btnClaimOfferInCheckout.setEnabled(true);
        btnClaimOfferInCheckout.setText("Claim Offer");
    }

    private void showOfferCardAsAutoApplied() {
        if (checkoutOffer == null) {
            return;
        }
        cardOfferClaim.setVisibility(View.VISIBLE);
        double discount = checkoutOffer.getDiscountPercentage() != null
                ? checkoutOffer.getDiscountPercentage()
                : 10.0;
        tvOfferTitle.setText(String.format(Locale.getDefault(), "%.0f%% subscription offer", discount));
        tvOfferHint.setText("Auto-applied for subscription items in your cart.");
        btnClaimOfferInCheckout.setEnabled(false);
        btnClaimOfferInCheckout.setText("Applied");
    }

    private void claimCheckoutOffer() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || checkoutOffer == null) {
            return;
        }

        apiService.claimOffer(uid, checkoutOffer.getId()).enqueue(new Callback<Object>() {
            @Override
            public void onResponse(Call<Object> call, Response<Object> response) {
                if (!isAdded()) {
                    return;
                }
                if (response.isSuccessful()) {
                    isCheckoutOfferClaimed = true;
                    tvOfferHint.setText("Claimed! Discount will apply to subscription-eligible items.");
                    btnClaimOfferInCheckout.setEnabled(false);
                    btnClaimOfferInCheckout.setText("Claimed");
                    if (!switchSubscription.isChecked()) {
                        switchSubscription.setChecked(true);
                    }
                    CustomToast.showSuccess(getContext(), "Offer claimed: " + checkoutOffer.getPromoCode());
                    updateTotal();
                } else {
                    CustomToast.showWarning(getContext(), "Unable to claim this offer");
                }
            }

            @Override
            public void onFailure(Call<Object> call, Throwable t) {
                CustomToast.showError(getContext(), "Network error while claiming offer");
            }
        });
    }

    private void updateTotal() {
        double shippingFee = calculateShippingFee();
        double subscriptionDiscount = calculateSubscriptionDiscount();
        double offerDiscount = promoCodeDiscount;
        double totalDiscount = subscriptionDiscount + offerDiscount;
        double pointsDiscount = pointsToRedeem * GREEN_POINT_VALUE_LKR;

        // Subscription discount row — show only subscription portion separately
        if (subscriptionDiscount > 0) {
            layoutDiscount.setVisibility(View.VISIBLE);
            tvDiscount.setText("-" + formatCurrency(subscriptionDiscount));
            if (tvDiscountMeta != null) {
                tvDiscountMeta.setText("5% flat + free delivery | Points +" + calculateSubscriptionBonusPoints());
                tvDiscountMeta.setVisibility(View.VISIBLE);
            }
        } else {
            layoutDiscount.setVisibility(View.GONE);
            if (tvDiscountMeta != null) tvDiscountMeta.setVisibility(View.GONE);
        }

        // Promo code discount row — shown separately
        if (layoutPromoDiscount != null && tvPromoDiscount != null) {
            if (offerDiscount > 0) {
                layoutPromoDiscount.setVisibility(View.VISIBLE);
                tvPromoDiscount.setText("-" + formatCurrency(offerDiscount));
            } else {
                layoutPromoDiscount.setVisibility(View.GONE);
            }
        }

        // Shipping
        if (shippingFee <= 0.0) {
            tvShipping.setText("Rs. 0.00");
        } else {
            tvShipping.setText(formatCurrency(shippingFee));
        }
        if (tvShippingLabel != null) {
            tvShippingLabel.setText(shippingFee <= 0.0 ? "Delivery Fee (Free)" : "Delivery Fee");
        }

        // Final total — clamp points first, then compute
        clampRedeemPoints(subtotalAmount + shippingFee - totalDiscount);
        pointsDiscount = pointsToRedeem * GREEN_POINT_VALUE_LKR;
        double total = Math.max(0.0, subtotalAmount + shippingFee - totalDiscount - pointsDiscount);

        tvTotal.setText(formatCurrency(total));
    }

    private void clampRedeemPoints(double preRedeemTotal) {
        int maxByAmount = (int) Math.floor(preRedeemTotal / GREEN_POINT_VALUE_LKR);
        int allowed = Math.max(0, Math.min(availableGreenPoints, maxByAmount));
        if (pointsToRedeem > allowed) {
            pointsToRedeem = allowed;
            refreshGreenPointsUi();
        }
    }

    private double calculateSubscriptionDiscount() {
        if (!isSubscribeAndSaveActive()) {
            return 0.0;
        }
        return Math.max(0.0, (subtotalAmount * SUBSCRIBE_DISCOUNT_PERCENT) / 100.0);
    }

    private double calculateShippingFee() {
        return isSubscribeAndSaveActive() ? 0.0 : SHIPPING_LKR;
    }

    private int calculateSubscriptionBonusPoints() {
        if (!isSubscribeAndSaveActive()) {
            return 0;
        }
        return 5; // flat +5 bonus points for subscribing
    }

    private double calculateSubscribeSavingsPreview() {
        // Always show the sum of subscription discount, promo code discount, and free delivery (if applicable)
        double subscriptionDiscount = calculateSubscriptionDiscount();
        double offerDiscount = promoCodeDiscount;
        double freeDeliverySaving = SHIPPING_LKR - calculateShippingFee();
        if (switchSubscription == null || !switchSubscription.isChecked()) {
            // Not subscribed: only preview what would be saved if subscribed (no offer discount)
            return SHIPPING_LKR + ((subtotalAmount * SUBSCRIBE_DISCOUNT_PERCENT) / 100.0);
        }
        // Subscribed: show actual savings (subscription + offer + free delivery)
        return subscriptionDiscount + offerDiscount + freeDeliverySaving;
    }

    private int calculateOrderEarnedPoints(double payableTotal) {
        // 10 points per Rs. 1000
        int base = (int) Math.floor(Math.max(0.0, payableTotal) / 1000.0) * 10;
        // +5 bonus if subscription is active
        if (isSubscribeAndSaveActive()) {
            base += 5;
        }
        return Math.max(0, base);
    }

    private boolean isSubscribeAndSaveActive() {
        return switchSubscription != null && switchSubscription.isChecked();
    }

    private boolean isActiveSubscriptionOffer(@Nullable Offer offer) {
        if (offer == null) {
            return false;
        }
        String status = offer.getStatus();
        if (status == null || !"active".equalsIgnoreCase(status)) {
            return false;
        }
        String promoType = offer.getPromoType();
        return promoType != null && "subscription".equalsIgnoreCase(promoType.trim());
    }

    private void fetchLiveLocationAndUpdateAddress() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        CustomToast.showWarning(getContext(), "Unable to fetch current location");
                        return;
                    }
                    applyLiveLocationAddress(location);
                })
                .addOnFailureListener(e -> CustomToast.showError(getContext(), "Failed to get location"));
    }

    private void applyLiveLocationAddress(@NonNull Location location) {
        try {
            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
            List<android.location.Address> addresses = geocoder.getFromLocation(
                    location.getLatitude(),
                    location.getLongitude(),
                    1
            );

            String line = String.format(Locale.getDefault(), "%.5f, %.5f",
                    location.getLatitude(), location.getLongitude());
            if (addresses != null && !addresses.isEmpty() && addresses.get(0).getAddressLine(0) != null) {
                line = addresses.get(0).getAddressLine(0);
            }

            Address liveAddress = Address.builder()
                    .id("live_location")
                    .title("Live Location")
                    .addressLine1(line)
                    .city("")
                    .postalCode("")
                    .build();

            selectedDeliveryAddress = liveAddress;
            tvLocationTitle.setText(liveAddress.getTitle());
            tvLocationAddress.setText(line);

            if (!cbDifferentBilling.isChecked()) {
                selectedBillingAddress = liveAddress;
                if (tvBillingAddress != null) {
                    tvBillingAddress.setText(line);
                }
            }

            CustomToast.showSuccess(getContext(), "Delivery location updated from GPS");
        } catch (Exception e) {
            CustomToast.showError(getContext(), "Unable to reverse geocode current location");
        }
    }

    private void placeOrder() {
        if (checkoutItems.isEmpty()) {
            CustomToast.showWarning(getContext(), "Your cart is empty");
            return;
        }

        if (selectedDeliveryAddress == null
                || selectedDeliveryAddress.getFullAddress() == null
                || selectedDeliveryAddress.getFullAddress().trim().isEmpty()) {
            CustomToast.showWarning(getContext(), "Please select a delivery address");
            return;
        }

        // FIX: addressId is string now, not numeric
        if (TextUtils.isEmpty(selectedDeliveryAddress.getId())) {
            if (TextUtils.isEmpty(selectedDeliveryAddress.getFirestoreDocId())) {
                CustomToast.showWarning(getContext(), "Please save your delivery address before placing the order");
                return;
            }
            syncSelectedDeliveryAddressThenValidateStock();
            return;
        }

        validateCheckoutStockThenContinue();
    }


    private void continuePlaceOrder() {
        if (selectedPaymentMethod == null) {
            CustomToast.showWarning(getContext(), "Please select a payment method");
            return;
        }
        if (switchSubscription.isChecked() && PAYMENT_CASH.equals(selectedPaymentMethod)) {
            CustomToast.showWarning(getContext(), "Subscriptions require Card payments");
            return;
        }

        validateGreenPointsBeforeCheckout(() -> {
            if (PAYMENT_CARD.equals(selectedPaymentMethod)) {
                askSaveCardAndStartPayment();
            } else {
                finalizeOrderFlow("PENDING", null);
            }
        });
    }

    private void syncSelectedDeliveryAddressThenValidateStock() {
        Address address = selectedDeliveryAddress;
        if (address == null) {
            CustomToast.showWarning(getContext(), "Please select a delivery address");
            return;
        }

        CustomToast.showInfo(getContext(), "Syncing selected address with server...");
        apiService.saveAddress(address).enqueue(new Callback<Address>() {
            @Override
            public void onResponse(Call<Address> call, Response<Address> response) {
                if (!isAdded()) {
                    return;
                }
                if (!response.isSuccessful() || response.body() == null || TextUtils.isEmpty(response.body().getId())) {
                    CustomToast.showWarning(getContext(), "Address sync failed. " + readErrorBody(response));
                    return;
                }

                address.setId(response.body().getId());
                persistAddressServerId(address);
                applySelectedAddress(address, AddressSelectionTarget.DELIVERY);
                validateCheckoutStockThenContinue();
            }

            @Override
            public void onFailure(Call<Address> call, Throwable t) {
                if (!isAdded()) {
                    return;
                }
                String message = t.getMessage() != null ? t.getMessage() : "Unable to reach address service";
                CustomToast.showWarning(getContext(), "Address sync failed. " + message);
            }
        });
    }

    private void validateCheckoutStockThenContinue() {
        if (checkoutItems.isEmpty()) {
            CustomToast.showWarning(getContext(), "Your cart is empty");
            return;
        }

        AtomicInteger pending = new AtomicInteger(checkoutItems.size());
        List<String> stockErrors = new ArrayList<>();

        for (CartItem item : checkoutItems) {
            apiService.getVariantsByProductId(item.getProductId()).enqueue(new Callback<List<com.hansanie.greencart.model.ProductVariant>>() {
                @Override
                public void onResponse(Call<List<com.hansanie.greencart.model.ProductVariant>> call,
                                       Response<List<com.hansanie.greencart.model.ProductVariant>> response) {
                    if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                        synchronized (stockErrors) {
                            stockErrors.add(item.getName() + " stock check failed");
                        }
                        finishCheckoutStockValidation(pending, stockErrors);
                        return;
                    }

                    com.hansanie.greencart.model.ProductVariant selected = findMatchingVariantForCheckout(item, response.body());
                    if (selected == null) {
                        synchronized (stockErrors) {
                            stockErrors.add(item.getName() + " is unavailable");
                        }
                        finishCheckoutStockValidation(pending, stockErrors);
                        return;
                    }

                    Integer stock = selected.getResolvedStockCount();
                    int available = stock != null ? Math.max(0, stock) : (selected.isInStock() ? Integer.MAX_VALUE : 0);
                    if (available <= 0) {
                        synchronized (stockErrors) {
                            stockErrors.add(item.getName() + " is out of stock");
                        }
                    } else if (available != Integer.MAX_VALUE && item.getQuantity() > available) {
                        synchronized (stockErrors) {
                            stockErrors.add("Only " + available + " left for " + item.getName());
                        }
                    }

                    finishCheckoutStockValidation(pending, stockErrors);
                }

                @Override
                public void onFailure(Call<List<com.hansanie.greencart.model.ProductVariant>> call, Throwable t) {
                    synchronized (stockErrors) {
                        stockErrors.add(item.getName() + " stock check failed");
                    }
                    finishCheckoutStockValidation(pending, stockErrors);
                }
            });
        }
    }

    private void finishCheckoutStockValidation(@NonNull AtomicInteger pending,
                                               @NonNull List<String> stockErrors) {
        if (pending.decrementAndGet() != 0) {
            return;
        }
        if (!isAdded()) {
            return;
        }
        if (!stockErrors.isEmpty()) {
            CustomToast.showWarning(getContext(), stockErrors.get(0));
            return;
        }
        continuePlaceOrder();
    }

    @Nullable
    private com.hansanie.greencart.model.ProductVariant findMatchingVariantForCheckout(
            @NonNull CartItem item,
            @NonNull List<com.hansanie.greencart.model.ProductVariant> variants
    ) {
        if (item.getVariantId() != null) {
            for (com.hansanie.greencart.model.ProductVariant variant : variants) {
                if (variant != null && item.getVariantId().equals(variant.getId())) {
                    return variant;
                }
            }
        }

        if (!TextUtils.isEmpty(item.getVariantName())) {
            for (com.hansanie.greencart.model.ProductVariant variant : variants) {
                if (variant == null || TextUtils.isEmpty(variant.getVariantName())) {
                    continue;
                }
                if (item.getVariantName().trim().equalsIgnoreCase(variant.getVariantName().trim())) {
                    return variant;
                }
            }
        }

        return variants.get(0);
    }

    private void finalizeOrderFlow(@NonNull String paymentStatus, @Nullable String paymentReference) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            CustomToast.showWarning(getContext(), "Please sign in to place an order");
            return;
        }

        generateNextOrderCode(uid, new OrderCodeCallback() {
            @Override
            public void onReady(@NonNull String orderCode) {
                Map<String, Object> orderData = buildOrderData(uid, orderCode, paymentStatus, paymentReference);
                persistOrder(orderData);
            }

            @Override
            public void onError(@NonNull String message) {
                CustomToast.showError(getContext(), message);
            }
        });
    }

    private Map<String, Object> buildOrderData(
            @NonNull String uid,
            @NonNull String orderCode,
            @NonNull String paymentStatus,
            @Nullable String paymentReference
    ) {

        double recomputedSubtotal = 0.0;
        double recomputedEligibleSubtotal = 0.0;
        for (CartItem item : checkoutItems) {
            double line = item.getPrice() * item.getQuantity();
            recomputedSubtotal += line;
            if (isItemSubscriptionEligible(item)) {
                recomputedEligibleSubtotal += line;
            }
        }

        // Keep UI and payload consistent with the actual cart at save time.
        subtotalAmount = recomputedSubtotal;
        eligibleSubtotalAmount = recomputedEligibleSubtotal;
        hasSubscriptionEligibleItem = recomputedEligibleSubtotal > 0;

        double subscriptionDiscount = calculateSubscriptionDiscount();
        double offerDiscount = promoCodeDiscount; // promoCodeDiscount already calculated elsewhere
        double totalDiscount = subscriptionDiscount + offerDiscount;
        double shipping = calculateShippingFee();
        double preRedeemTotal = Math.max(0.0, recomputedSubtotal + shipping - totalDiscount);
        clampRedeemPoints(preRedeemTotal);
        double redeemedValue = pointsToRedeem * GREEN_POINT_VALUE_LKR;
        double total = Math.max(0.0, preRedeemTotal - redeemedValue);
        int greenPointsEarned = calculateOrderEarnedPoints(total);

        String normalizedPaymentStatus = normalizePaymentStatus(paymentStatus);
        String deliveryAddress = selectedDeliveryAddress != null
                ? nullToEmpty(selectedDeliveryAddress.getFullAddress())
                : "";

        List<Map<String, Object>> orderItems = new ArrayList<>();
        for (CartItem item : checkoutItems) {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("productId", item.getProductId());
            itemMap.put("variantId", item.getVariantId());
            itemMap.put("name", item.getName());
            itemMap.put("productName", item.getName());
            itemMap.put("variantName", item.getVariantName());
            itemMap.put("price", item.getPrice());
            itemMap.put("unitPrice", item.getPrice());
            itemMap.put("priceAtPurchase", item.getPrice());
            itemMap.put("quantity", item.getQuantity());
            itemMap.put("lineTotal", item.getPrice() * item.getQuantity());
            itemMap.put("imageUrl", item.getImageUrl());
            itemMap.put("isSubscriptionItem", item.isSubscriptionItem());
            itemMap.put("subscriptionFrequency", item.getSubscriptionFrequency());
            itemMap.put("subscriptionEligible", isItemSubscriptionEligible(item));
            orderItems.add(itemMap);
        }
        // Ensure order items are included in orderData
        // This is critical for saving to Firestore and MySQL
        // Fixes: order_items not being saved
        // Add this before returning orderData

        Map<String, Object> orderData = new HashMap<>();
        orderData.put("firebaseUid", uid);
        orderData.put("orderCode", orderCode);
        orderData.put("addressId", selectedDeliveryAddress != null ? selectedDeliveryAddress.getId() : null);
        // AFTER — also store computed delivery string directly:
        String resolvedDelivery = selectedDeliveryAddress != null
                ? nullToEmpty(selectedDeliveryAddress.getFullAddress())
                : "";
        orderData.put("addressLine", resolvedDelivery);
        orderData.put("deliveryAddress", resolvedDelivery);
        orderData.put("address", resolvedDelivery); // some backends expect "address"
        orderData.put("billingAddressId", (selectedBillingAddress != null ? selectedBillingAddress : selectedDeliveryAddress) != null
                ? (selectedBillingAddress != null ? selectedBillingAddress : selectedDeliveryAddress).getId()
                : null);
        orderData.put("paymentMethod", selectedPaymentMethod);
        orderData.put("paymentStatus", normalizedPaymentStatus);
        orderData.put("paymentReference", paymentReference);
        orderData.put("orderStatus", ORDER_STATUS_PENDING);
        orderData.put("status", ORDER_ROW_STATUS_PENDING);
        orderData.put("currency", "LKR");
        orderData.put("hubName", DEFAULT_HUB_NAME);
        orderData.put("hubLatitude", DEFAULT_HUB_LAT);
        orderData.put("hubLongitude", DEFAULT_HUB_LON);
        orderData.put("subtotal", recomputedSubtotal);
        orderData.put("shipping", shipping);
        orderData.put("subscriptionDiscount", subscriptionDiscount);  // NEW: separate field
        orderData.put("promoCodeDiscount", offerDiscount);            // NEW: separate field
        orderData.put("discount", totalDiscount);
        orderData.put("greenPointsRedeemed", pointsToRedeem);
        orderData.put("greenPointsRedeemValue", redeemedValue);
        orderData.put("greenPointsBalanceBefore", availableGreenPoints);
        orderData.put("greenPointsBalanceAfter", Math.max(0, availableGreenPoints - pointsToRedeem) + greenPointsEarned);
        total = Math.max(0.0, recomputedSubtotal + shipping - totalDiscount - redeemedValue);
        orderData.put("totalAmount", total);
        orderData.put("greenPointsEarned", greenPointsEarned);
        orderData.put("isSubscription", switchSubscription.isChecked());
        orderData.put("offerId", hasAppliedSubscriptionOffer() ? checkoutOffer.getId() : null);
        orderData.put("offerPercentage", hasAppliedSubscriptionOffer() ? checkoutOffer.getDiscountPercentage() : null);
        // Promo code offer data
        orderData.put("promoCode", appliedPromoCode);
        orderData.put("promoOfferId", appliedPromoOfferId);
        orderData.put("promoDiscountPercent", appliedPromoDiscountPercent);
        orderData.put("promoDiscount", promoCodeDiscount);
        // Add items to orderData for saving
        orderData.put("items", orderItems);
        return orderData;
    }

    private boolean hasAppliedSubscriptionOffer() {
        return switchSubscription != null
                && switchSubscription.isChecked()
                && isCheckoutOfferClaimed
                && isActiveSubscriptionOffer(checkoutOffer);
    }


    private void persistOrder(@NonNull Map<String, Object> orderData) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            CustomToast.showWarning(getContext(), "Please sign in to place an order");
            return;
        }

        // Use orderCode as Firestore document ID
        String orderCode = (String) orderData.get("orderCode");
        if (orderCode == null || orderCode.trim().isEmpty()) {
            CustomToast.showError(getContext(), "Order code missing. Cannot save order.");
            return;
        }
        orderData.put("firestoreDocumentId", orderCode);

        // FIX 1: Add createdAt ISO for MySQL and use a copy for Firestore so we do not
        // mutate the original orderData (it is used later in the post-order flow).
        String orderCreatedAt = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        orderData.put("createdAtIso", orderCreatedAt);              // MySQL

        // Prepare a Firestore-specific payload (shallow copy) and attach server timestamp
        Map<String, Object> firebasePayload = new HashMap<>(orderData);
        firebasePayload.put("createdAt", FieldValue.serverTimestamp());  // Firestore server ts

        // Remove fields we don't want in Firestore document
        firebasePayload.remove("discount");
        firebasePayload.remove("offerId");
        firebasePayload.remove("offerPercentage");

        db.collection("orders")
                .document(orderCode)
                .set(firebasePayload)
                .addOnSuccessListener(aVoid -> { // Fixed: Added 'aVoid' parameter
                    Order mysqlOrder = buildMySqlOrder(orderData);
                    apiService.saveOrderToMySql(mysqlOrder).enqueue(new Callback<Order>() {
                        @Override
                        public void onResponse(Call<Order> call, Response<Order> response) {
                            if (!isAdded()) {
                                return;
                            }
                            if (!response.isSuccessful()) {
                                String backendError = readErrorBody(response);
                                db.collection("orders").document(orderCode).update(
                                        "mysqlSyncStatus", "FAILED",
                                        "mysqlSyncAt", FieldValue.serverTimestamp(),
                                        "mysqlSyncError", backendError
                                );
                                CustomToast.showError(getContext(), "MySQL sync failed: " + backendError);
                                return;
                            }

                            db.collection("orders").document(orderCode).update(
                                    "mysqlSyncStatus", "SYNCED",
                                    "mysqlSyncAt", FieldValue.serverTimestamp(),
                                                    "id", response.body() != null ? response.body().getId() : null,
                                    "mysqlConfirmedSyncStatus", "PENDING"
                            );
                                            Long mysqlOrderId = response.body() != null ? response.body().getId() : null;
                                            // Store MySQL order id in the in-memory orderData so downstream flows (e.g. subscription sync)
                                            // can reference it when linking subscription -> order.
                                            if (mysqlOrderId != null) {
                                                orderData.put("mysqlOrderId", mysqlOrderId);
                                            }
                            if (mysqlOrderId == null || mysqlOrderId <= 0) {
                                db.collection("orders").document(orderCode).update(
                                        "paymentSyncStatus", "MYSQL_ORDER_ID_MISSING",
                                        "paymentSyncAt", FieldValue.serverTimestamp(),
                                        "paymentSyncError", "Order synced but MySQL order id missing"
                                );
                                CustomToast.showError(getContext(), "Order synced, but payment sync was skipped (missing MySQL order id)");
                                return;
                            }
                            syncPaymentRecord(uid, orderData, mysqlOrderId, db.collection("orders").document(orderCode), () ->
                                    completePostOrderSuccess(uid, orderData));
                        }

                        @Override
                        public void onFailure(Call<Order> call, Throwable t) {
                            if (!isAdded()) {
                                return;
                            }
                            String message = t.getMessage() != null ? t.getMessage() : "network error";
                            db.collection("orders").document(orderCode).update(
                                    "mysqlSyncStatus", "FAILED",
                                    "mysqlSyncAt", FieldValue.serverTimestamp(),
                                    "mysqlSyncError", message
                            );
                            CustomToast.showError(getContext(), "Order sync to MySQL failed: " + message);
                        }
                    });
                })
                .addOnFailureListener(e -> CustomToast.showError(getContext(), "Unable to place order right now"));
    }
    private void syncPaymentRecord(@NonNull String uid,
                                   @NonNull Map<String, Object> orderData,
                                   @Nullable Long mysqlOrderId,
                                   @NonNull com.google.firebase.firestore.DocumentReference orderRef,
                                   @NonNull Runnable onDone) {

        double amount = orderData.get("totalAmount") instanceof Number
                ? ((Number) orderData.get("totalAmount")).doubleValue()
                : 0.0;

        String paymentStatus = normalizePaymentStatus(stringValueOrDefault(orderData.get("paymentStatus"), PAYMENT_STATUS_UNPAID));
        String paymentRef = readString(orderData.get("paymentReference"));
        String paymentMethodForRow = PAYMENT_CASH.equals(selectedPaymentMethod) ? "CASH_ON_DELIVERY" : "CARD";
        String rowStatus = PAYMENT_STATUS_PAID.equalsIgnoreCase(paymentStatus) ? "SUCCESS" : "PENDING";

        Map<String, Object> firestorePayment = new HashMap<>();
        firestorePayment.put("firebaseUid", uid);
        firestorePayment.put("orderCode", orderData.get("orderCode"));
        firestorePayment.put("mysqlOrderId", mysqlOrderId);
        firestorePayment.put("amount", amount);
        firestorePayment.put("currency", "LKR");
        firestorePayment.put("paymentMethod", paymentMethodForRow);
        firestorePayment.put("status", rowStatus);
        firestorePayment.put("payherePaymentId", paymentRef);
        firestorePayment.put("createdAt", FieldValue.serverTimestamp());

        Payment payment = Payment.builder()
                .id(UUID.randomUUID().toString())
                .orderId(mysqlOrderId)
                .firebaseUid(uid)
                .amount(amount)
                .currency("LKR")
                .paymentMethod(paymentMethodForRow)
                .status(rowStatus)
                .payherePaymentId(paymentRef)
                .payhereAmount(amount)
                .build();

        db.collection("payments")
                .add(firestorePayment)
                .addOnSuccessListener(doc -> syncPaymentToMySql(payment, orderRef, true, onDone))
                .addOnFailureListener(e -> syncPaymentToMySql(payment, orderRef, false, onDone));
    }

    private void syncPaymentToMySql(@NonNull Payment payment,
                                    @NonNull com.google.firebase.firestore.DocumentReference orderRef,
                                    boolean firestorePaymentSaved,
                                    @NonNull Runnable onDone) {
        orderRef.update(
                "paymentSyncStatus", "IN_PROGRESS",
                "paymentSyncAt", FieldValue.serverTimestamp(),
                "paymentSyncError", FieldValue.delete()
        );

        apiService.savePayment(payment).enqueue(new Callback<Payment>() {
            @Override
            public void onResponse(Call<Payment> call, Response<Payment> response) {
                if (response.isSuccessful()) {
                    orderRef.update(
                            "paymentSyncStatus", firestorePaymentSaved ? "SYNCED" : "FIREBASE_FAILED",
                            "paymentSyncAt", FieldValue.serverTimestamp(),
                            "paymentSyncError", FieldValue.delete()
                    );
                } else {
                    orderRef.update(
                            "paymentSyncStatus", firestorePaymentSaved ? "MYSQL_FAILED" : "FAILED",
                            "paymentSyncAt", FieldValue.serverTimestamp(),
                            "paymentSyncError", "HTTP " + response.code()
                    );
                }
                onDone.run();
            }

            @Override
            public void onFailure(Call<Payment> call, Throwable t) {
                orderRef.update(
                        "paymentSyncStatus", firestorePaymentSaved ? "MYSQL_FAILED" : "FAILED",
                        "paymentSyncAt", FieldValue.serverTimestamp(),
                        "paymentSyncError", t.getMessage() != null ? t.getMessage() : "network error"
                );
                onDone.run();
            }
        });
    }

    @NonNull
    private Order buildMySqlOrder(@NonNull Map<String, Object> orderData) {

        Order order = new Order();

        order.setFirebaseUid((String) orderData.get("firebaseUid"));
        order.setOrderCode((String) orderData.get("orderCode"));

        // FIX: do not parse addressId as long
        order.setAddressId(readString(orderData.get("addressId")));
        String addr = readString(orderData.get("deliveryAddress"));
        if (addr == null || addr.trim().isEmpty()) {
            addr = readString(orderData.get("addressLine"));
        }
        if (addr == null || addr.trim().isEmpty()) {
            addr = readString(orderData.get("address"));
        }

        order.setDeliveryAddress(nullToEmpty(addr));
        order.setSubtotal(orderData.get("subtotal") instanceof Number
                ? ((Number) orderData.get("subtotal")).doubleValue() : 0.0);
        order.setShipping(orderData.get("shipping") instanceof Number
                ? ((Number) orderData.get("shipping")).doubleValue() : 0.0);
        order.setTotalAmount(orderData.get("totalAmount") instanceof Number
                ? ((Number) orderData.get("totalAmount")).doubleValue()
                : 0.0);

        double subDiscount   = orderData.get("subscriptionDiscount") instanceof Number ? ((Number) orderData.get("subscriptionDiscount")).doubleValue() : 0.0;
        double promoDiscount = orderData.get("promoDiscount") instanceof Number ? ((Number) orderData.get("promoDiscount")).doubleValue() : 0.0;
        double pointsDiscount = orderData.get("greenPointsRedeemValue") instanceof Number ? ((Number) orderData.get("greenPointsRedeemValue")).doubleValue() : 0.0;
        order.setDiscountAmount(subDiscount + promoDiscount + pointsDiscount);


        order.setOrderStatus(ORDER_STATUS_PENDING);
        order.setStatus(ORDER_ROW_STATUS_PENDING);
        order.setSubscriptionOrder(asBoolean(orderData.get("isSubscription")));
        order.setPaymentStatus(normalizePaymentStatus(readString(orderData.get("paymentStatus"))));

        // Firestore serverTimestamp client-side returns null, so use ISO string if present
        String isoNow = orderData.containsKey("createdAtIso") && orderData.get("createdAtIso") instanceof String
                ? (String) orderData.get("createdAtIso")
                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        order.setCreatedAt(isoNow);
        order.setOrderDate(isoNow);

        // Add offerId and offerPercentage to MySQL order
        if (orderData.containsKey("offerId")) {
            Object offerIdObj = orderData.get("offerId");
            Long offerId = null;
            if (offerIdObj instanceof Number) {
                offerId = ((Number) offerIdObj).longValue();
            } else if (offerIdObj instanceof String) {
                try {
                    order.setOfferId(Long.parseLong((String) offerIdObj));
                } catch (NumberFormatException ignored) {}
            }
        }
        if (orderData.containsKey("offerPercentage")) {
            Object offerPctObj = orderData.get("offerPercentage");
            if (offerPctObj instanceof Number) {
                order.setOfferPercentage(((Number) offerPctObj).doubleValue());
            } else if (offerPctObj instanceof String) {
                try {
                    order.setOfferPercentage(Double.parseDouble((String) offerPctObj));
                } catch (NumberFormatException ignored) {}
            }
        }

        List<OrderItem> orderItems = new ArrayList<>();
        Object rawItems = orderData.get("items");

        if (rawItems instanceof List<?>) {
            for (Object obj : (List<?>) rawItems) {
                if (!(obj instanceof Map)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> itemMap = (Map<String, Object>) obj;

                OrderItem item = new OrderItem();

                Long productId = parseLongOrNull(itemMap.get("productId"));
                if (productId == null) {
                    continue;
                }
                item.setProductId(productId);

                // variantId remains numeric nullable
                item.setVariantId(parseLongOrNull(itemMap.get("variantId")));

                int quantity = itemMap.get("quantity") instanceof Number
                        ? ((Number) itemMap.get("quantity")).intValue()
                        : 0;
                if (quantity <= 0) {
                    continue;
                }
                item.setQuantity(quantity);

                double unitPrice = itemMap.get("unitPrice") instanceof Number
                        ? ((Number) itemMap.get("unitPrice")).doubleValue()
                        : 0.0;
                item.setUnitPrice(unitPrice);
                item.setPriceAtPurchase(unitPrice * quantity);

                orderItems.add(item);
            }
        }

        order.setItems(orderItems);
        // Promo code fields
        if (orderData.containsKey("promoOfferId")) {
            Object promoOfferIdObj = orderData.get("promoOfferId");
            if (promoOfferIdObj instanceof Number) {
                order.setPromoOfferId(((Number) promoOfferIdObj).longValue());
            }
        }
        if (orderData.containsKey("promoCode")) {
            order.setPromoCode(readString(orderData.get("promoCode")));
        }
        if (orderData.containsKey("promoDiscountPercent")) {
            Object pct = orderData.get("promoDiscountPercent");
            if (pct instanceof Number) {
                order.setPromoDiscountPercent(((Number) pct).doubleValue());
            }
        }
        if (orderData.containsKey("promoDiscount")) {
            Object pd = orderData.get("promoDiscount");
            if (pd instanceof Number) {
                order.setPromoDiscount(((Number) pd).doubleValue());
            }
        }
        return order;
    }

    @Nullable
    private Long parseLongOrNull(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (!text.matches("\\d+")) {
                return null;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @NonNull
    private String normalizePaymentStatus(@Nullable String paymentStatus) {
        if (paymentStatus == null) {
            return PAYMENT_STATUS_UNPAID;
        }
        String normalized = paymentStatus.trim().toLowerCase(Locale.getDefault());
        if (PAYMENT_STATUS_PAID.equals(normalized) || "success".equals(normalized)) {
            return PAYMENT_STATUS_PAID;
        }
        return PAYMENT_STATUS_UNPAID;
    }

    @NonNull
    private String stringValueOrDefault(@Nullable Object value, @NonNull String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return fallback;
        }
        return text;
    }

    @NonNull
    private String nullToEmpty(@Nullable String value) {
        return value != null ? value : "";
    }

    @NonNull
    private String readErrorBody(@NonNull Response<?> response) {
        if (response.errorBody() == null) {
            return "HTTP " + response.code();
        }
        try {
            String raw = response.errorBody().string();
            if (raw == null || raw.trim().isEmpty()) {
                return "HTTP " + response.code();
            }
            return raw.trim();
        } catch (IOException ignored) {
            return "HTTP " + response.code();
        }
    }

    @NonNull
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castItemList(@Nullable Object value) {
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        return new ArrayList<>();
    }

    private void completePostOrderSuccess(@NonNull String uid, @NonNull Map<String, Object> orderData) {
        double total = orderData.get("totalAmount") instanceof Number ? ((Number) orderData.get("totalAmount")).doubleValue() : 0.0;
        // Use subscriptionDiscount (only the subscription portion) for subscriptions
        double subscriptionDiscount = orderData.get("subscriptionDiscount") instanceof Number
                ? ((Number) orderData.get("subscriptionDiscount")).doubleValue()
                : 0.0;

        if (isSubscribeAndSaveActive()) {
            // Pass MySQL order id (if available) so we can create a subscription->order link
            Long mysqlOrderId = null;
            Object mid = orderData.get("mysqlOrderId");
            if (mid instanceof Number) mysqlOrderId = ((Number) mid).longValue();
            upsertGrocerySubscription(uid, total, subscriptionDiscount, mysqlOrderId);
        }

        reduceStockForOrderItems();

        Runnable next = () -> openOrderSuccess(
                String.valueOf(orderData.get("orderCode")),
                total
        );

        settleGreenPointsForOrder(uid, orderData, () -> {
            if (!isBuyNowMode) {
                CartManager.clearCart(requireContext(), uid, next);
            } else {
                next.run();
            }
        });
    }

    private void settleGreenPointsForOrder(@NonNull String uid,
                                           @NonNull Map<String, Object> orderData,
                                           @NonNull Runnable onDone) {
        int earned = orderData.get("greenPointsEarned") instanceof Number
                ? ((Number) orderData.get("greenPointsEarned")).intValue()
                : 0;
        int redeemed = orderData.get("greenPointsRedeemed") instanceof Number
                ? ((Number) orderData.get("greenPointsRedeemed")).intValue()
                : 0;
        String orderCode = String.valueOf(orderData.get("orderCode"));

        com.google.firebase.firestore.DocumentReference walletRef = db.collection("users")
                .document(uid)
                .collection("wallet")
                .document("green_points");
        com.google.firebase.firestore.DocumentReference ledgerRef = walletRef.collection("transactions")
                .document(orderCode);

        db.runTransaction(transaction -> {
            DocumentSnapshot ledgerSnap = transaction.get(ledgerRef);
            if (ledgerSnap.exists()) {
                return readLong(ledgerSnap.get("balanceAfter")) != null
                        ? readLong(ledgerSnap.get("balanceAfter"))
                        : 0L;
            }

            DocumentSnapshot walletSnap = transaction.get(walletRef);
            long balance = readLong(walletSnap.get("pointsBalance")) != null
                    ? readLong(walletSnap.get("pointsBalance"))
                    : 0L;
            long totalEarned = readLong(walletSnap.get("totalEarned")) != null
                    ? readLong(walletSnap.get("totalEarned"))
                    : 0L;
            long totalRedeemed = readLong(walletSnap.get("totalRedeemed")) != null
                    ? readLong(walletSnap.get("totalRedeemed"))
                    : 0L;

            if (redeemed > balance) {
                throw new IllegalStateException("Insufficient points balance");
            }

            long updatedBalance = balance - redeemed + earned;
            Map<String, Object> walletPatch = new HashMap<>();
            walletPatch.put("pointsBalance", updatedBalance);
            walletPatch.put("totalEarned", totalEarned + earned);
            walletPatch.put("totalRedeemed", totalRedeemed + redeemed);
            walletPatch.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(walletRef, walletPatch, SetOptions.merge());

            Map<String, Object> ledger = new HashMap<>();
            ledger.put("orderCode", orderCode);
            ledger.put("earned", earned);
            ledger.put("redeemed", redeemed);
            ledger.put("balanceBefore", balance);
            ledger.put("balanceAfter", updatedBalance);
            ledger.put("createdAt", FieldValue.serverTimestamp());
            transaction.set(ledgerRef, ledger, SetOptions.merge());
            return updatedBalance;
        }).addOnSuccessListener(updatedBalance -> {
            availableGreenPoints = Math.max(0, updatedBalance.intValue());
            pointsToRedeem = 0;
            refreshGreenPointsUi();

            Object firestoreDocumentId = orderData.get("firestoreDocumentId");
            if (firestoreDocumentId != null) {
                Map<String, Object> orderPatch = new HashMap<>();
                orderPatch.put("pointsSettlementStatus", "SETTLED");
                orderPatch.put("pointsBalanceAfter", availableGreenPoints);
                db.collection("orders")
                        .document(String.valueOf(firestoreDocumentId))
                        .set(orderPatch, SetOptions.merge());
            }
            onDone.run();
        }).addOnFailureListener(e -> {
            CustomToast.showWarning(getContext(), "Order placed, but points settlement is pending.");
            onDone.run();
        });
    }

    private void reduceStockForOrderItems() {
        for (CartItem item : checkoutItems) {
            if (item.getVariantId() == null || item.getQuantity() <= 0) {
                continue;
            }
            ProductStockRequest req = new ProductStockRequest();
            req.productId = item.getProductId();
            req.variantId = item.getVariantId();
            req.quantity = item.getQuantity();
            req.batchNumber = "SALE-" + System.currentTimeMillis();

            apiService.reduceVariantStock(req).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    // Best-effort sync.
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    // Keep order success UX unaffected.
                }
            });
        }
    }

    private void openOrderSuccess(@NonNull String orderCode, double total) {
        if (!isAdded()) {
            return;
        }
        OrderSuccessFragment fragment = OrderSuccessFragment.newInstance(orderCode, total);
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void askSaveCardAndStartPayment() {
        if (TextUtils.isEmpty(BuildConfig.PAYHERE_MERCHANT_ID) || TextUtils.isEmpty(BuildConfig.PAYHERE_MERCHANT_SECRET)) {
            CustomToast.showError(getContext(), "Configure PAYHERE_MERCHANT_ID and PAYHERE_MERCHANT_SECRET");
            return;
        }

        // If user already picked a saved card, skip the save prompt.
        if (selectedSavedCard != null) {
            launchPayHereCheckout();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Save Card")
                .setMessage("Would you like to save this card for faster checkout?")
                .setNegativeButton("No", (dialog, which) -> {
                    launchPayHereCheckout();
                })
                .setPositiveButton("Yes", (dialog, which) -> {
                    showPaymentMethodsBottomSheet();
                    CustomToast.showInfo(getContext(), "Add and select a card, then pay");
                })
                .show();
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

            invokeSetter(initRequest, "setSandBox", BuildConfig.PAYHERE_SANDBOX);
            invokeSetter(initRequest, "setMerchantId", BuildConfig.PAYHERE_MERCHANT_ID);
            invokeSetter(initRequest, "setMerchantSecret", BuildConfig.PAYHERE_MERCHANT_SECRET);
            invokeSetter(initRequest, "setCurrency", "LKR");
            // Subtract promoCodeDiscount as well for correct payment amount
            invokeSetter(initRequest, "setAmount", Math.max(0.0,
                    subtotalAmount
                    + calculateShippingFee()
                    - calculateSubscriptionDiscount()
                    - promoCodeDiscount
                    - (pointsToRedeem * GREEN_POINT_VALUE_LKR)));
            invokeSetter(initRequest, "setOrderId", "PAY-" + System.currentTimeMillis());
            String cardMeta = selectedSavedCard != null && !TextUtils.isEmpty(selectedSavedCard.getCardMasked())
                    ? (" - " + selectedSavedCard.getCardMasked())
                    : "";
            invokeSetter(initRequest, "setItemsDescription", "GreenCart Order" + cardMeta);
            invokeSetter(initRequest, "setNotifyUrl", "https://e_shop.requestcatcher.com/");

            Object customer = invokeMethod(initRequest, "getCustomer");
            if (customer != null) {
                String fullName = selectedSavedCard != null && !TextUtils.isEmpty(selectedSavedCard.getCardHolderName())
                        ? selectedSavedCard.getCardHolderName()
                        : (selectedDeliveryAddress != null && !TextUtils.isEmpty(selectedDeliveryAddress.getTitle())
                        ? selectedDeliveryAddress.getTitle()
                        : "GreenCart User");
                invokeSetter(customer, "setFirstName", fullName);
                invokeSetter(customer, "setLastName", "");
                invokeSetter(customer, "setEmail", "customer@greencart.lk");
                invokeSetter(customer, "setPhone", "0710000000");

                Object address = invokeMethod(customer, "getAddress");
                if (address != null) {
                    invokeSetter(address, "setAddress", selectedDeliveryAddress != null ? selectedDeliveryAddress.getFullAddress() : "Colombo");
                    invokeSetter(address, "setCity", selectedDeliveryAddress != null && selectedDeliveryAddress.getCity() != null
                            ? selectedDeliveryAddress.getCity()
                            : "Colombo");
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

    private void handlePayHereResult(@NonNull androidx.activity.result.ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Intent data = result.getData();
            String extraResultKey = resolvePayHereConstant("INTENT_EXTRA_RESULT", "PH_EXTRA_RESULT");
            Object response = data.getSerializableExtra(extraResultKey);
            boolean success = readBoolean(response, "isSuccess");
            if (success) {
                String paymentRef = readNestedString(response, "getData", "getPaymentNo");
                finalizeOrderFlow("PAID", paymentRef);
                CustomToast.showSuccess(getContext(), "Payment successful");
            } else {
                CustomToast.showWarning(getContext(), "Payment failed or cancelled");
            }
            return;
        }

        if (result.getResultCode() == Activity.RESULT_CANCELED) {
            CustomToast.showInfo(getContext(), "Payment cancelled");
        }
    }

    private void generateNextOrderCode(@NonNull String uid, @NonNull OrderCodeCallback callback) {
        String dateKey = LocalDate.now().format(ORDER_CODE_DATE);
        db.runTransaction((Transaction.Function<String>) transaction -> {
            com.google.firebase.firestore.DocumentReference counterRef = db.collection("order_sequence").document(dateKey);
            DocumentSnapshot snapshot = transaction.get(counterRef);
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

    private void invokeSetter(@NonNull Object target, @NonNull String methodName, @Nullable Object value) {
        if (value == null) {
            return;
        }
        try {
            Method[] methods = target.getClass().getMethods();
            for (Method method : methods) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> param = method.getParameterTypes()[0];
                if (param.isAssignableFrom(value.getClass())
                        || (param == boolean.class && value instanceof Boolean)
                        || (param == double.class && value instanceof Number)) {
                    method.invoke(target, value);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
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
                if (!"putExtra".equals(method.getName()) || method.getParameterCount() != 2) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params[0] != String.class) {
                    continue;
                }
                if (params[1].isAssignableFrom(payload.getClass())) {
                    method.invoke(intent, key, payload);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    @NonNull
    private String resolvePayHereConstant(@NonNull String name, @NonNull String fallback) {
        try {
            Class<?> constants = Class.forName("lk.payhere.androidsdk.PHConstants");
            Field field = constants.getField(name);
            Object value = field.get(null);
            if (value != null) {
                return String.valueOf(value);
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private boolean readBoolean(@Nullable Object target, @NonNull String methodName) {
        if (target == null) {
            return false;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            Object out = method.invoke(target);
            return out instanceof Boolean && (Boolean) out;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Nullable
    private String readNestedString(@Nullable Object root, @NonNull String firstMethod, @NonNull String secondMethod) {
        if (root == null) {
            return null;
        }
        try {
            Object first = root.getClass().getMethod(firstMethod).invoke(root);
            if (first == null) {
                return null;
            }
            Object second = first.getClass().getMethod(secondMethod).invoke(first);
            return second != null ? String.valueOf(second) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void upsertGrocerySubscription(@NonNull String uid, double total, double discount, @Nullable Long mysqlOrderId) {
        String frequency = resolveSubscriptionFrequency();
        LocalDate today = LocalDate.now();
        int intervalDays = SUBSCRIPTION_FREQ_BI_WEEKLY.equals(frequency) ? 14 : 7;
        LocalDate nextDate = findNextDeliveryDate(today, selectedSubscriptionDeliveryDay, intervalDays);

        // Only include subscription-eligible items in the subscription
        List<Long> subscribedProductIds = new ArrayList<>();
        List<GrocerySubscriptionItem> subscriptionItems = new ArrayList<>();
        // Build Firestore-friendly maps for each item at the same time to avoid nulls
        List<Map<String, Object>> itemMaps = new ArrayList<>();
        for (CartItem item : checkoutItems) {
            subscribedProductIds.add(item.getProductId());
            GrocerySubscriptionItem si = GrocerySubscriptionItem.builder()
                    .productId(item.getProductId())
                    .variantId(item.getVariantId())
                    .name(item.getName())
                    .variantName(item.getVariantName())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getPrice())
                    .imageUrl(item.getImageUrl())
                    .build();
            subscriptionItems.add(si);

            Map<String, Object> im = new HashMap<>();
            im.put("productId", item.getProductId());
            im.put("variantId", item.getVariantId());
            im.put("name", item.getName());
             im.put("productName", item.getName());
            im.put("variantName", item.getVariantName());
            im.put("quantity", item.getQuantity());
            double unitPrice = item.getPrice();
            im.put("price", unitPrice);
            im.put("unitPrice", unitPrice);
            im.put("priceAtPurchase", unitPrice);
            im.put("lineTotal", unitPrice * (item.getQuantity() > 0 ? item.getQuantity() : 1));
            im.put("imageUrl", item.getImageUrl());
            im.put("isSubscriptionItem", true);
            im.put("subscriptionFrequency", frequency);
            itemMaps.add(im);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("firebaseUid", uid);
        data.put("name", "Subscribe & Save");
        data.put("frequency", frequency);
        data.put("deliveryDay", selectedSubscriptionDeliveryDay);
        data.put("intervalDays", intervalDays);
        data.put("status", "ACTIVE");
        data.put("start_date", today.format(DATE_FORMATTER));
        data.put("next_delivery_date", nextDate.format(DATE_FORMATTER));
        data.put("deliveryTimeSlot", "07:00 AM");
        String delivAddrId = selectedDeliveryAddress != null
                ? (selectedDeliveryAddress.getId() != null ? selectedDeliveryAddress.getId()
                : selectedDeliveryAddress.getFirestoreDocId())
                : null;
        String billAddrId = selectedBillingAddress != null
                ? (selectedBillingAddress.getId() != null ? selectedBillingAddress.getId()
                : selectedBillingAddress.getFirestoreDocId())
                : (delivAddrId);
        data.put("deliveryAddressId", delivAddrId);
        data.put("billingAddressId", billAddrId);
        data.put("totalAmount", total);
        data.put("discountAmount", discount);
        data.put("bonusPoints", calculateSubscriptionBonusPoints());
        data.put("productIds", subscribedProductIds);
        data.put("items", itemMaps);
        data.put("updatedAt", LocalDateTime.now().format(DATE_TIME_FORMATTER));

        data.put("createdAt", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        data.put("itemCount", subscriptionItems.size());
        db.collection("grocery_subscriptions")
                .add(data)
                .addOnSuccessListener(ref -> syncSubscriptionToMySql(uid, ref.getId(), data, subscriptionItems, mysqlOrderId));
    }

    private String resolveSubscriptionFrequency() {
        return selectedSubscriptionFrequency;
    }

    @NonNull
    private LocalDate findNextDeliveryDate(@NonNull LocalDate base,
                                           @NonNull String targetDay,
                                           int intervalDays) {
        DayOfWeek target;
        try {
            target = DayOfWeek.valueOf(targetDay.trim().toUpperCase(Locale.getDefault()));
        } catch (IllegalArgumentException ignored) {
            target = DayOfWeek.MONDAY;
        }

        LocalDate next = base;
        while (next.getDayOfWeek() != target) {
            next = next.plusDays(1);
        }
        if (!next.isAfter(base)) {
            next = next.plusDays(Math.max(7, intervalDays));
        }
        return next;
    }

    @NonNull
    private List<GrocerySubscriptionItem> toSubscriptionItems() {
        List<GrocerySubscriptionItem> items = new ArrayList<>();
        for (CartItem cartItem : checkoutItems) {
            if (!isItemSubscriptionEligible(cartItem)) continue;
            GrocerySubscriptionItem item = GrocerySubscriptionItem.builder()
                    .productId(cartItem.getProductId())
                    .variantId(cartItem.getVariantId())
                    .name(cartItem.getName())
                    .variantName(cartItem.getVariantName())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(cartItem.getPrice())
                    .imageUrl(cartItem.getImageUrl())
                    .build();
            items.add(item);
        }
        return items;
    }

    private void syncSubscriptionToMySql(@NonNull String uid,
                                         @NonNull String firestoreId,
                                         @NonNull Map<String, Object> data,
                                         @NonNull List<GrocerySubscriptionItem> items,
                                         @Nullable Long mysqlOrderId) {

        // GrocerySubscriptionItem → SubscriptionItemUpsertRequest convert
        List<SubscriptionItemUpsertRequest> requestItems = new ArrayList<>();
        for (GrocerySubscriptionItem item : items) {
            requestItems.add(SubscriptionItemUpsertRequest.builder()
                    .productId(item.getProductId())
                    .variantId(item.getVariantId())
                    .name(item.getName())
                    .variantName(item.getVariantName())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .imageUrl(item.getImageUrl())
                    .build());
        }

        SubscriptionSaveRequest payload = SubscriptionSaveRequest.builder()
                .firebaseUid(uid)
                .name(readString(data.get("name")))
                .frequency(readString(data.get("frequency")))
                .status(readString(data.get("status")))
                .startDate(readString(data.get("start_date")))
                .nextDeliveryDate(readString(data.get("next_delivery_date")))
                .deliveryDay(readString(data.get("deliveryDay")))
                .intervalDays(data.get("intervalDays") instanceof Number
                        ? ((Number) data.get("intervalDays")).intValue() : 7)
                .deliveryTimeSlot(readString(data.get("deliveryTimeSlot")))
                .deliveryAddressId(readString(data.get("deliveryAddressId")))
                .billingAddressId(readString(data.get("billingAddressId")))
                .totalAmount(data.get("totalAmount") instanceof Number
                        ? ((Number) data.get("totalAmount")).doubleValue() : 0.0)
                .discountAmount(data.get("discountAmount") instanceof Number
                        ? ((Number) data.get("discountAmount")).doubleValue() : 0.0)
                .itemCount(requestItems.size())
                .bonusPoints(data.get("bonusPoints") instanceof Number
                        ? ((Number) data.get("bonusPoints")).intValue() : 0)
                .firestoreId(firestoreId)
                .skipNextDelivery(false)
                .items(new ArrayList<>()) // ← CHANGE: empty, items separately යනවා
                .build();

        apiService.saveGrocerySubscription(payload).enqueue(new Callback<GrocerySubscription>() {
            @Override
            public void onResponse(Call<GrocerySubscription> call, Response<GrocerySubscription> response) {
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getId() == null) {
                    String err = readErrorBody(response);
                    android.util.Log.e("SUB_SYNC", "MySQL save failed: " + err);
                    db.collection("grocery_subscriptions").document(firestoreId)
                            .update("mysqlSyncStatus", "FAILED", "mysqlSyncError", err);
                    return;
                }

                Long subId = response.body().getId();
                Map<String, Object> patch = new HashMap<>();
                patch.put("id", subId);
                patch.put("mysqlSyncStatus", "SYNCED");
                db.collection("grocery_subscriptions").document(firestoreId)
                        .set(patch, SetOptions.merge());

                apiService.updateSubscriptionItems(subId, requestItems)
                        .enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> c, Response<Void> r) {
                                android.util.Log.d("SUB_SYNC", "Items synced ok");
                            }

                            @Override
                            public void onFailure(Call<Void> c, Throwable t) {
                                android.util.Log.e("SUB_SYNC", "Items update failed: " + t.getMessage());
                            }
                        });

                // Save subscription -> order link locally and to Firestore, and try backend if we have mysqlOrderId
                try {
                    // Local Room
                    new Thread(() -> {
                        try {
                            com.hansanie.greencart.model.SubscriptionOrder localLink = com.hansanie.greencart.model.SubscriptionOrder.builder()
                                    .subscriptionId(subId)
                                    .orderId(mysqlOrderId)
                                    .deliveryDate(readString(data.get("next_delivery_date")))
                                    .build();
                            AppDatabase.getInstance(requireContext()).subscriptionOrderDao().insert(localLink);
                        } catch (Exception e) {
                            android.util.Log.e("SUB_LOCAL", "Failed to save local subscription order: " + e.getMessage());
                        }
                    }).start();
                } catch (Exception e) {
                    android.util.Log.w("SUB_LOCAL", "Failed to enqueue local subscription order save: " + e.getMessage());
                }

                try {
                    // Firestore mirror — store the grocery subscription Firestore document id
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("subscriptionFirestoreId", firestoreId);
                    doc.put("orderId", mysqlOrderId);
                    doc.put("deliveryDate", readString(data.get("next_delivery_date")));
                    doc.put("createdAt", FieldValue.serverTimestamp());
                    db.collection("subscription_orders").add(doc);
                } catch (Exception e) {
                    android.util.Log.e("SUB_REMOTE", "Failed to save subscription_orders firestore doc: " + e.getMessage());
                }

                // Best-effort backend save if we have a MySQL order id
                try {
                    if (mysqlOrderId != null && subId != null && apiService != null) {
                        com.hansanie.greencart.model.SubscriptionOrder payload = com.hansanie.greencart.model.SubscriptionOrder.builder()
                                .subscriptionId(subId)
                                .orderId(mysqlOrderId)
                                .deliveryDate(readString(data.get("next_delivery_date")))
                                .build();
                        apiService.saveSubscriptionOrder(payload).enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> call, Response<Void> response) {
                                if (!response.isSuccessful()) {
                                    android.util.Log.w("SUB_REMOTE", "Failed to save subscription order to backend: HTTP " + response.code());
                                }
                            }

                            @Override
                            public void onFailure(Call<Void> call, Throwable t) {
                                android.util.Log.w("SUB_REMOTE", "Failed to save subscription order to backend: " + (t != null ? t.getMessage() : "unknown"));
                            }
                        });
                    }
                } catch (Exception e) {
                    android.util.Log.w("SUB_REMOTE", "Exception while calling backend to save subscription order: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<GrocerySubscription> call, Throwable t) {
                android.util.Log.e("SUB_SYNC", "Network error: " + t.getMessage());
                db.collection("grocery_subscriptions").document(firestoreId)
                        .update("mysqlSyncStatus", "NETWORK_FAILED",
                                "mysqlSyncError", t.getMessage());
            }
        });
    }

    private String formatCurrency(double value) {
        return String.format(Locale.getDefault(), "Rs. %.2f", value);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLiveLocationAndUpdateAddress();
            } else {
                CustomToast.showWarning(getContext(), "Location permission is required for live address");
            }
        }
    }

    // Place this at the end of the class, after other private methods
    private void applyPromoCode() {
        if (getContext() == null || etPromoCode == null || tilPromoCode == null) return;
        // Check login
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            CustomToast.showWarning(getContext(), getString(R.string.promo_code_sign_in));
            Intent intent = new Intent(getContext(), com.hansanie.greencart.activity.AuthActivity.class);
            startActivity(intent);
            return;
        }
        String code = etPromoCode.getText() != null ? etPromoCode.getText().toString().trim().toUpperCase(java.util.Locale.getDefault()) : "";
        if (code.isEmpty()) {
            tilPromoCode.setError(getString(R.string.promo_code_enter));
            return;
        } else {
            tilPromoCode.setError(null);
        }
        btnClaimPromoCode.setEnabled(false);
        btnClaimPromoCode.setText(R.string.promo_code_applying);
        String uid = FirebaseAuth.getInstance().getUid();
        // Query user_offers for this user and code
        FirebaseFirestore.getInstance()
                .collection("user_offers")
                .whereEqualTo("firebaseUid", uid)
                .whereEqualTo("promoCode", code)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    android.util.Log.d("PROMO", "Docs found: " + snapshot.size());
                    btnClaimPromoCode.setEnabled(true);
                    btnClaimPromoCode.setText(R.string.promo_code_apply);
                    if (snapshot.isEmpty()) {
                        tilPromoCode.setError(getString(R.string.promo_code_invalid));
                        return;
                    }
                    Map<String, Object> userOffer = snapshot.getDocuments().get(0).getData();
                    Boolean used = userOffer != null && userOffer.get("used") instanceof Boolean ? (Boolean) userOffer.get("used") : null;
                    String expiry = userOffer != null && userOffer.get("expiryDate") instanceof String ? (String) userOffer.get("expiryDate") : null;
                    // Check if expired
                    boolean isExpired = false;
                    if (expiry != null && !expiry.isEmpty()) {
                        try {
                            java.text.SimpleDateFormat isoFmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault());
                            isoFmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                            java.util.Date expiryDate = isoFmt.parse(expiry);
                            isExpired = expiryDate != null && expiryDate.before(new java.util.Date());
                        } catch (Exception e) {
                            // fallback: ignore expiry check if parse fails
                        }
                    }
                    if (isExpired) {
                        tilPromoCode.setError(getString(R.string.promo_code_expired));
                        return;
                    }
                    if (used != null && used) {
                        tilPromoCode.setError(getString(R.string.promo_code_already_used));
                        return;
                    }
                    // Success: apply promo code
                    tilPromoCode.setError(null);
                    appliedPromoCode = code;
                    promoCodeDiscount = 0.0;
                    appliedPromoOfferId = null;
                    appliedPromoDiscountPercent = 0.0;

                    if (userOffer != null) {
                        // offers collection ගෙන් offerId resolve කරන්න
                        Object offerIdField = userOffer.get("offerId");
                        Long offerId = null;
                        if (offerIdField instanceof Number) {
                            offerId = ((Number) offerIdField).longValue();
                        }
                        final Long resolvedOfferId = offerId;

                        Object discountField = userOffer.get("discount");
                        double promoPercent = 0.0;
                        if (discountField instanceof Number) {
                            promoPercent = ((Number) discountField).doubleValue();
                        }
                        final double resolvedPercent = promoPercent;

                        if (resolvedOfferId != null) {
                            // offers table ගෙන් discount percentage confirm කරන්න
                            FirebaseFirestore.getInstance()
                                .collection("offers")
                                .whereEqualTo("id", resolvedOfferId)
                                .limit(1)
                                .get()
                                .addOnSuccessListener(offerSnap -> {
                                    double finalPercent = resolvedPercent; // fallback to user_offers discount
                                    if (!offerSnap.isEmpty()) {
                                        Object pct = offerSnap.getDocuments().get(0).get("discountPercentage");
                                        if (pct instanceof Number) {
                                            finalPercent = ((Number) pct).doubleValue();
                                        }
                                    }
                                    appliedPromoOfferId = resolvedOfferId;
                                    appliedPromoDiscountPercent = finalPercent;
                                    promoCodeDiscount = (subtotalAmount * finalPercent) / 100.0;
                                    updateTotal();
                                    CustomToast.showSuccess(getContext(), getString(R.string.promo_code_applied));
                                    tvOfferHint.setText(getString(R.string.promo_code_claimed_hint));
                                })
                                .addOnFailureListener(e -> {
                                    // offers lookup fail වුනත් user_offers discount use කරන්න
                                    appliedPromoOfferId = resolvedOfferId;
                                    appliedPromoDiscountPercent = resolvedPercent;
                                    promoCodeDiscount = (subtotalAmount * resolvedPercent) / 100.0;
                                    updateTotal();
                                    CustomToast.showSuccess(getContext(), getString(R.string.promo_code_applied));
                                    tvOfferHint.setText(getString(R.string.promo_code_claimed_hint));
                                });
                        } else {
                            // offerId නැතිනම් discount direct use කරන්න
                            appliedPromoDiscountPercent = resolvedPercent;
                            promoCodeDiscount = (subtotalAmount * resolvedPercent) / 100.0;
                            updateTotal();
                            CustomToast.showSuccess(getContext(), getString(R.string.promo_code_applied));
                            tvOfferHint.setText(getString(R.string.promo_code_claimed_hint));
                        }
                    }

                    // Firestore 'used' true කරන්න
                    String docId = snapshot.getDocuments().get(0).getId();
                    FirebaseFirestore.getInstance()
                        .collection("user_offers")
                        .document(docId)
                        .update("used", true);
                })
                .addOnFailureListener(e -> {
                    btnClaimPromoCode.setEnabled(true);
                    btnClaimPromoCode.setText(R.string.promo_code_apply);
                    tilPromoCode.setError(getString(R.string.promo_code_network_error));
                });
    }

    // After successful checkout, mark offer as used
    private void markPromoCodeAsUsed(String code) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            return;
        }
        FirebaseFirestore.getInstance()
                .collection("user_offers")
                .whereEqualTo("firebaseUid", uid)
                .whereEqualTo("promoCode", code)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        String docId = snapshot.getDocuments().get(0).getId();
                        FirebaseFirestore.getInstance()
                                .collection("user_offers")
                                .document(docId)
                                .update("used", true);
                    }
                });
    }
}


package com.hansanie.greencart.fragment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hansanie.greencart.R;
import com.hansanie.greencart.adapter.OrderItemSummaryAdapter;
import com.hansanie.greencart.BuildConfig;
import com.hansanie.greencart.model.Order;
import com.hansanie.greencart.model.OrderItem;
import com.hansanie.greencart.network.ApiService;
import com.hansanie.greencart.network.RetrofitClient;
import com.hansanie.greencart.util.CustomToast;
import com.hansanie.greencart.util.NotificationHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderDetailsFragment extends Fragment implements OnMapReadyCallback {

    private static final String DEFAULT_HUB_NAME = "Green Cart Hub";
    private static final String DEFAULT_HUB_ADDRESS = "Kenko1st Organic foods shop, 12 Rosmead Pl, Colombo 00700";
    private static final double DEFAULT_HUB_LAT = 6.9156395;
    private static final double DEFAULT_HUB_LON = 79.8667641;
    private static final String DIRECTIONS_ENDPOINT = "https://maps.googleapis.com/maps/api/directions/json";

    private TextView tvOrderId;
    private TextView tvDate;
    private TextView tvStatus;
    private TextView tvTotal;
    private TextView tvDetailSubtotal;
    private TextView tvDetailDeliveryFee;
    private TextView tvDetailDiscount;
    private TextView tvDetailAddress;
    private TextView tvTrackingSubtitle;
    private TextView tvFreshnessDistance;
    private TextView tvFreshnessEta;
    private TextView tvPendingTime;
    private TextView tvConfirmedTime;
    private TextView tvOutTime;
    private TextView tvDeliveredTime;
    private TextView tvRiderName;
    private TextView tvRiderPhone;
    private TextView tvArrivalHint;
    private TextView tvGreenPoints;
    private TextView tvGreenPointsDiscount;
    private TextView tvPromoDiscount;
    private View layoutDetailDiscount;
    private View layoutGreenPointsDiscount;
    private View layoutPromoDiscount;
    private View riderCard;
    private View arrivalChip;
    private View stepPending;
    private View stepConfirmed;
    private View stepOut;
    private View stepDelivered;
    private ImageView iconPending;
    private ImageView iconConfirmed;
    private ImageView iconOut;
    private ImageView iconDelivered;
    private MapView mapView;
    private ImageView mapExpandButton;
    private RecyclerView rvOrderItems;
    private MaterialButton btnCallRider;
    private MaterialButton btnSupportHelp;
    private MaterialButton btnMarkArrived;
    private MaterialButton btnReviewFarm;

    private OrderItemSummaryAdapter orderItemAdapter;
    private GoogleMap googleMap;
    private com.google.android.material.button.MaterialButton btnOrderMyLocation;
    private com.google.android.gms.location.FusedLocationProviderClient fusedClient;
    private Order currentOrder;
    private java.util.List<LatLng> currentRoutePoints = null;
    private FirebaseFirestore db;
    private ApiService apiService;
    private String currentOrderStatus = "Pending";
    @Nullable
    private LatLng resolvedDeliveryPoint;
    private boolean geocodeInProgress;

    interface DirectionsRouteListener {
        void onSuccess(@NonNull List<LatLng> points, @NonNull String distanceText, @NonNull String durationText);
        void onFailure(@NonNull String reason);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_order_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = FirebaseFirestore.getInstance();
        apiService = RetrofitClient.getApiService();
        fusedClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(requireActivity());
        bindViews(view);
        setupLists();
        setupActions();

        if (getArguments() != null) {
            currentOrder = (Order) getArguments().getSerializable("selected_order");
        }

        if (currentOrder != null) {
            bindOrder(currentOrder);
        }

        if (mapView != null) {
            mapView.onCreate(savedInstanceState);
            mapView.onResume();
            MapsInitializer.initialize(requireContext());
            mapView.getMapAsync(this);
            // Make the card tappable to open full-screen map
            mapView.setOnClickListener(v -> openFullscreenMapForOrder());
        }
    }

    private void bindViews(View view) {
        tvOrderId = view.findViewById(R.id.tvDetailOrderID);
        tvDate = view.findViewById(R.id.tvDetailOrderDate);
        tvStatus = view.findViewById(R.id.tvDetailStatus);
        tvTotal = view.findViewById(R.id.tvDetailTotal);
        tvDetailSubtotal = view.findViewById(R.id.tvDetailSubtotal);
        tvDetailDeliveryFee = view.findViewById(R.id.tvDetailDeliveryFee);
        // subscription discount (new) - view may be absent in older layouts so guard usage
        tvDetailDiscount = view.findViewById(R.id.tvDetailDiscount);
        tvGreenPointsDiscount = view.findViewById(R.id.tvGreenPointsDiscount);
        tvPromoDiscount = view.findViewById(R.id.tvPromoDiscount);
        layoutGreenPointsDiscount = view.findViewById(R.id.layoutGreenPointsDiscount);
        layoutPromoDiscount = view.findViewById(R.id.layoutPromoDiscount);
        layoutDetailDiscount = view.findViewById(R.id.layoutDetailDiscount);
        tvDetailAddress = view.findViewById(R.id.tvDetailAddress);
        tvTrackingSubtitle = view.findViewById(R.id.tvTrackingSubtitle);
        tvFreshnessDistance = view.findViewById(R.id.tvFreshnessDistance);
        tvFreshnessEta = view.findViewById(R.id.tvFreshnessEta);
        tvPendingTime = view.findViewById(R.id.tvPendingTime);
        tvConfirmedTime = view.findViewById(R.id.tvConfirmedTime);
        tvOutTime = view.findViewById(R.id.tvOutTime);
        tvDeliveredTime = view.findViewById(R.id.tvDeliveredTime);
        tvRiderName = view.findViewById(R.id.tvRiderName);
        tvRiderPhone = view.findViewById(R.id.tvRiderPhone);
        tvArrivalHint = view.findViewById(R.id.tvArrivalHint);
        tvGreenPoints = view.findViewById(R.id.tvGreenPoints);
        riderCard = view.findViewById(R.id.cardRider);
        arrivalChip = view.findViewById(R.id.cardArrivalChip);
        stepPending = view.findViewById(R.id.stepPending);
        stepConfirmed = view.findViewById(R.id.stepConfirmed);
        stepOut = view.findViewById(R.id.stepOutForDelivery);
        stepDelivered = view.findViewById(R.id.stepDelivered);
        iconPending = view.findViewById(R.id.iconPending);
        iconConfirmed = view.findViewById(R.id.iconConfirmed);
        iconOut = view.findViewById(R.id.iconOutForDelivery);
        iconDelivered = view.findViewById(R.id.iconDelivered);
        mapView = view.findViewById(R.id.orderMapView);
        mapExpandButton = view.findViewById(R.id.orderMapExpandButton);
        rvOrderItems = view.findViewById(R.id.rvOrderItems);
        btnCallRider = view.findViewById(R.id.btnCallRider);
        btnSupportHelp = view.findViewById(R.id.btnSupportHelp);
        btnMarkArrived = view.findViewById(R.id.btnMarkArrived);
        btnReviewFarm = view.findViewById(R.id.btnReviewFarm);
    }

    private void setupLists() {
        orderItemAdapter = new OrderItemSummaryAdapter();
        rvOrderItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvOrderItems.setAdapter(orderItemAdapter);
        orderItemAdapter.setOnOrderItemClickListener(this::onOrderItemSelectedForReview);
    }

    private void setupActions() {
        btnCallRider.setOnClickListener(v -> dialNumber(currentOrder != null ? currentOrder.getRiderPhone() : null));
        btnSupportHelp.setOnClickListener(v -> openSupportAndHelp());
        btnMarkArrived.setOnClickListener(v -> markRiderArrived());
        btnReviewFarm.setOnClickListener(v -> openFarmReviewDialog());
        // allow tapping the map card area to open fullscreen map
        View mapCard = getView() != null ? getView().findViewById(R.id.orderMapCard) : null;
        if (mapCard != null) mapCard.setOnClickListener(v -> openFullscreenMapForOrder());
        if (mapExpandButton != null) mapExpandButton.setOnClickListener(v -> openFullscreenMapForOrder());
    }

    private void openFullscreenMapForOrder() {
        if (currentOrder == null) return;
        LatLng hub = resolveHubPoint(currentOrder);
        LatLng delivery = resolveDeliveryPoint(currentOrder);
        double[] lats;
        double[] lons;
        if (currentRoutePoints != null && !currentRoutePoints.isEmpty()) {
            int n = currentRoutePoints.size();
            lats = new double[n];
            lons = new double[n];
            for (int i = 0; i < n; i++) {
                LatLng p = currentRoutePoints.get(i);
                lats[i] = p.latitude;
                lons[i] = p.longitude;
            }
        } else if (delivery != null) {
            lats = new double[]{hub.latitude, delivery.latitude};
            lons = new double[]{hub.longitude, delivery.longitude};
        } else {
            lats = new double[]{hub.latitude};
            lons = new double[]{hub.longitude};
        }

        FullscreenMapFragment frag = FullscreenMapFragment.newInstance(lats, lons);
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, frag)
                .addToBackStack(null)
                .commit();
    }

    private void bindOrder(@NonNull Order order) {
        String status = getStatus(order);
        currentOrderStatus = status;

        tvOrderId.setText(order.getOrderId());
        tvDate.setText(formatDateTime(order.getDate(), order.getCreatedAt()));
        tvStatus.setText(status);


        double total = order.getTotalAmount() != null ? order.getTotalAmount() : 0.0;
        double shipping = order.getShipping() != null ? Math.max(0.0, order.getShipping()) : 0.0;

        // Subscription discount — stored separately in Firestore as "subscriptionDiscount"
        double subscriptionDiscount = order.getSubscriptionDiscount() != null
                ? Math.max(0.0, order.getSubscriptionDiscount()) : 0.0;
        double redeemPointsDiscount = order.getGreenPointsRedeemValue() != null
                ? Math.max(0.0, order.getGreenPointsRedeemValue()) : 0.0;
        double promoDiscount = order.getPromoDiscount() != null
                ? Math.max(0.0, order.getPromoDiscount()) : 0.0;
        int redeemPointsUsed = order.getGreenPointsRedeemed() != null
                ? order.getGreenPointsRedeemed() : 0;

        double subtotal = order.getSubtotal() != null
                ? Math.max(0.0, order.getSubtotal())
                : Math.max(0.0, total);

        tvDetailSubtotal.setText(formatCurrency(subtotal));
        tvDetailDeliveryFee.setText(shipping <= 0.0 ? "Rs. 0.00" : formatCurrency(shipping));

// Subscription discount row
        if (layoutDetailDiscount != null && tvDetailDiscount != null) {
            if (subscriptionDiscount > 0) {
                layoutDetailDiscount.setVisibility(View.VISIBLE);
                tvDetailDiscount.setText("-" + formatCurrency(subscriptionDiscount));
            } else {
                layoutDetailDiscount.setVisibility(View.GONE);
            }
        }

// Green points redemption row
        if (layoutGreenPointsDiscount != null && tvGreenPointsDiscount != null) {
            if (redeemPointsDiscount > 0) {
                layoutGreenPointsDiscount.setVisibility(View.VISIBLE);
                tvGreenPointsDiscount.setText("-" + formatCurrency(redeemPointsDiscount)
                        + String.format(Locale.getDefault(), " (%d pts)", redeemPointsUsed));
            } else {
                layoutGreenPointsDiscount.setVisibility(View.GONE);
            }
        }

// Promo code discount row
        if (layoutPromoDiscount != null && tvPromoDiscount != null) {
            if (promoDiscount > 0) {
                layoutPromoDiscount.setVisibility(View.VISIBLE);
                tvPromoDiscount.setText("-" + formatCurrency(promoDiscount));
            } else {
                layoutPromoDiscount.setVisibility(View.GONE);
            }
        }

// Total — use stored value directly
        tvTotal.setText(formatCurrency(Math.max(0.0, total)));

        tvDetailAddress.setText(
                !TextUtils.isEmpty(order.getDeliveryAddress())
                        ? order.getDeliveryAddress()
                        : "Delivery address will appear here"
        );

        tvTrackingSubtitle.setText(buildTrackingSubtitle(order));
        tvFreshnessDistance.setText(buildDistanceLabel(order));
        tvFreshnessEta.setText(valueOrFallback(order.getEstimatedArrival(), "Calculating ETA"));
        tvPendingTime.setText(formatJourneyTime(order.getPendingAt(), "Received"));
        tvConfirmedTime.setText(formatJourneyTime(order.getConfirmedAt(), "Awaiting"));
        tvOutTime.setText(formatJourneyTime(order.getOutForDeliveryAt(), "Not yet"));
        tvDeliveredTime.setText(formatJourneyTime(order.getDeliveredAt(), "Pending"));

        int points = calculateGreenPoints(order);
        tvGreenPoints.setText(String.format(Locale.getDefault(), "+%d Green Points", points));

        boolean revealRider =
                "Out for Delivery".equalsIgnoreCase(status) ||
                        "Delivered".equalsIgnoreCase(status);

        riderCard.setVisibility(revealRider ? View.VISIBLE : View.GONE);
        tvRiderName.setText(valueOrFallback(order.getRiderName(), "Assigned soon"));
        tvRiderPhone.setText(valueOrFallback(order.getRiderPhone(), "Visible once dispatched"));
        btnCallRider.setVisibility(revealRider ? View.VISIBLE : View.GONE);
        arrivalChip.setVisibility(order.isArrivalNotified() ? View.VISIBLE : View.GONE);
        btnMarkArrived.setVisibility(
                "Out for Delivery".equalsIgnoreCase(status)
                        ? View.VISIBLE
                        : View.GONE
        );
        btnReviewFarm.setVisibility(canSubmitReview(status) ? View.VISIBLE : View.GONE);
        tvArrivalHint.setText(
                order.isArrivalNotified()
                        ? "Rider is within 200m or marked arrived. Please be ready to receive your order."
                        : "You'll get an arrival alert when the rider is within 200m of your location."
        );

        // SAFE ITEMS LIST
        List<OrderItem> items = order.getItems() != null
                ? order.getItems()
                : new ArrayList<>();
        orderItemAdapter.submitList(toSummaryItems(items));

        updateStepper(status);
    }

    @NonNull
    private List<Map<String, Object>> toSummaryItems(@NonNull List<OrderItem> items) {
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (OrderItem item : items) {
            if (item == null) {
                continue;
            }
            Map<String, Object> summary = new HashMap<>();
            if (item.getProductId() != null) {
                summary.put("productId", item.getProductId());
            }
            summary.put("quantity", item.getQuantity() != null ? item.getQuantity() : 1);
            double unitPrice = item.getUnitPrice() != null
                    ? item.getUnitPrice()
                    : (item.getPriceAtPurchase() != null ? item.getPriceAtPurchase() : 0.0);
            summary.put("unitPrice", unitPrice);
            summary.put("priceAtPurchase", item.getPriceAtPurchase() != null ? item.getPriceAtPurchase() : unitPrice);
            summary.put("productName", item.getProductName() != null ? item.getProductName() : "Organic item");
            summary.put("variantName", item.getVariantName() != null ? item.getVariantName() : "Fresh pack");
            summary.put("imageUrl", item.getImageUrl());
            mapped.add(summary);
        }
        return mapped;
    }

    private void markRiderArrived() {
        if (currentOrder == null) {
            return;
        }
        currentOrder.setArrivalNotified(true);
        arrivalChip.setVisibility(View.VISIBLE);
        tvArrivalHint.setText("Rider marked as arrived. Please keep your phone nearby.");
        NotificationHelper.showAndPersist(
                requireContext(),
                NotificationHelper.CHANNEL_ORDERS,
                "Orders",
                "Rider has arrived",
                currentOrder.getOrderId() + " is now at your delivery point."
        );
        String documentId = currentOrder.getFirestoreDocumentId();
        if (!TextUtils.isEmpty(documentId)) {
            // Set statusUpdatedAt to null so Firestore will overwrite it
            currentOrder.setStatusUpdatedAt(null);
            db.collection("orders").document(documentId)
                    .set(currentOrder)
                    .addOnSuccessListener(aVoid -> db.collection("orders").document(documentId)
                            .update("statusUpdatedAt", FieldValue.serverTimestamp()));
            return;
        }

        String orderCode = currentOrder.getOrderCode();
        String uid = FirebaseAuth.getInstance().getUid();
        if (!TextUtils.isEmpty(orderCode) && !TextUtils.isEmpty(uid)) {
            db.collection("orders")
                    .whereEqualTo("firebaseUid", uid)
                    .whereEqualTo("orderCode", orderCode)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (!snapshot.isEmpty()) {
                            DocumentSnapshot doc = snapshot.getDocuments().get(0);
                            currentOrder.setFirestoreDocumentId(doc.getId());
                            // Set statusUpdatedAt to null so Firestore will overwrite it
                            currentOrder.setStatusUpdatedAt(null);
                            doc.getReference().set(currentOrder)
                                    .addOnSuccessListener(aVoid -> doc.getReference()
                                            .update("statusUpdatedAt", FieldValue.serverTimestamp()));
                        }
                    });
        }
    }

    private void updateStepper(String status) {
        updateStepVisual(stepPending, iconPending, true);
        updateStepVisual(stepConfirmed, iconConfirmed,
                "Confirmed".equalsIgnoreCase(status) || "Out for Delivery".equalsIgnoreCase(status) || "Delivered".equalsIgnoreCase(status));
        updateStepVisual(stepOut, iconOut,
                "Out for Delivery".equalsIgnoreCase(status) || "Delivered".equalsIgnoreCase(status));
        updateStepVisual(stepDelivered, iconDelivered,
                "Delivered".equalsIgnoreCase(status));
    }

    private void updateStepVisual(View container, ImageView icon, boolean active) {
        if (container == null || icon == null) {
            return;
        }
        container.setAlpha(active ? 1f : 0.45f);
        icon.setColorFilter(Color.parseColor(active ? "#2E7D32" : "#9E9E9E"));
    }

    private String getStatus(Order order) {
        if (order == null) {
            return "Pending";
        }
        if (!TextUtils.isEmpty(order.getOrderStatus())) {
            return normalizeCustomerStatus(order.getOrderStatus());
        }
        if (!TextUtils.isEmpty(order.getStatus())) {
            return normalizeCustomerStatus(order.getStatus());
        }
        return "Pending";
    }

    @NonNull
    private String normalizeCustomerStatus(@Nullable String rawStatus) {
        if (rawStatus == null) {
            return "Pending";
        }
        String normalized = rawStatus.trim().replace('_', ' ').toUpperCase(Locale.getDefault());
        switch (normalized) {
            case "ACTIVE":
            case "PLACED":
            case "PENDING":
                return "Pending";
            case "CONFIRMED":
            case "PROCESSING":
                return "Confirmed";
            case "OUT FOR DELIVERY":
            case "DISPATCHED":
                return "Out for Delivery";
            case "DELIVERED":
                return "Delivered";
            case "CANCELED":
            case "CANCELLED":
                return "Cancelled";
            default:
                return rawStatus;
        }
    }

    private String buildTrackingSubtitle(Order order) {
        return String.format(Locale.getDefault(), "%s → Your home", resolveHubName(order));
    }

    private String buildDistanceLabel(Order order) {
        LatLng hub = resolveHubPoint(order);
        LatLng delivery = resolveDeliveryPoint(order);
        if (delivery == null) {
            return "Hub to home route (locating address...)";
        }
        double km = distanceKm(hub.latitude, hub.longitude, delivery.latitude, delivery.longitude);
        return String.format(Locale.getDefault(), "Hub to home %.1f km", km);
    }

    private String resolveHubName(@NonNull Order order) {
        return valueOrFallback(order.getHubName(), DEFAULT_HUB_NAME);
    }

    @NonNull
    private LatLng resolveHubPoint(@NonNull Order order) {
        double lat = order.getHubLatitude() != null ? order.getHubLatitude() : DEFAULT_HUB_LAT;
        double lon = order.getHubLongitude() != null ? order.getHubLongitude() : DEFAULT_HUB_LON;
        return new LatLng(lat, lon);
    }

    @Nullable
    private LatLng resolveDeliveryPoint(@NonNull Order order) {
        if (order.getDeliveryLatitude() != null && order.getDeliveryLongitude() != null) {
            return new LatLng(order.getDeliveryLatitude(), order.getDeliveryLongitude());
        }
        return resolvedDeliveryPoint;
    }

    private double distanceKm(double startLat, double startLon, double endLat, double endLon) {
        double earthRadius = 6371.0;
        double latDistance = Math.toRadians(endLat - startLat);
        double lonDistance = Math.toRadians(endLon - startLon);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(startLat)) * Math.cos(Math.toRadians(endLat))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private String valueOrFallback(String value, String fallback) {
        return !TextUtils.isEmpty(value) ? value : fallback;
    }

    private String formatCurrency(Double value) {
        return String.format(Locale.getDefault(), "Rs. %.2f", value != null ? value : 0.0);
    }

    private int calculateGreenPoints(@NonNull Order order) {
        // If the order already contains an explicit earned points value, prefer it.
        if (order.getGreenPointsEarned() != null) {
            return Math.max(0, order.getGreenPointsEarned());
        }

        // Base points: 5 points for each full 1000 LKR of the order total
        double total = order.getTotalAmount() != null ? order.getTotalAmount() : 0.0;
        int base = (int) Math.floor(total / 1000.0) * 5;

        // If this was a subscription order, add the subscription bonus points.
        int subscriptionBonus = 0;
        try {
            boolean isSubscription = order.isSubscriptionOrder() || order.getSubscriptionId() != null;
            if (isSubscription) {
                double subtotal = order.getSubtotal() != null ? order.getSubtotal() : total;
                double shipping = order.getShipping() != null ? order.getShipping() : 0.0;
                double subDiscount = order.getSubscriptionDiscount() != null ? order.getSubscriptionDiscount() : 0.0;
                double discountedTotal = Math.max(0.0, subtotal + shipping - subDiscount);
                // Checkout logic gives min 5 points, then 2 points for each full 500 LKR
                subscriptionBonus = Math.max(5, (int) Math.floor(discountedTotal / 500.0) * 2);
            }
        } catch (Exception ignored) {
            // Defensive: if any getter is missing or fails, fall back to base points only
        }

        return Math.max(0, base + subscriptionBonus);
    }

    private String formatDateTime(@Nullable String orderDate, @Nullable String createdAt) {
        Date date = parseToDate(firstNonBlank(orderDate, createdAt));
        if (date == null) {
            return valueOrFallback(firstNonBlank(orderDate, createdAt), "Date unavailable");
        }
        DateFormat formatter = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault());
        return formatter.format(date);
    }

    private String formatJourneyTime(@Nullable String rawValue, @NonNull String fallback) {
        if (TextUtils.isEmpty(rawValue)) {
            return fallback;
        }
        Date parsed = parseToDate(rawValue);
        if (parsed == null) {
            return rawValue;
        }
        SimpleDateFormat sameDayFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
        return isToday(parsed) ? sameDayFormat.format(parsed) : dateTimeFormat.format(parsed);
    }

    private boolean isToday(@NonNull Date date) {
        Calendar lhs = Calendar.getInstance();
        lhs.setTime(date);
        Calendar rhs = Calendar.getInstance();
        return lhs.get(Calendar.YEAR) == rhs.get(Calendar.YEAR)
                && lhs.get(Calendar.DAY_OF_YEAR) == rhs.get(Calendar.DAY_OF_YEAR);
    }

    @Nullable
    private String firstNonBlank(@Nullable String primary, @Nullable String fallback) {
        return !TextUtils.isEmpty(primary) ? primary : fallback;
    }

    @Nullable
    private Date parseToDate(@Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        String raw = value.trim();
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "MMM d, yyyy • h:mm a",
                "MMM d, yyyy h:mm a"
        };
        for (String pattern : patterns) {
            try {
                return new SimpleDateFormat(pattern, Locale.getDefault()).parse(raw);
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    private void openSupportAndHelp() {
        SupportHelpFragment supportHelpFragment = new SupportHelpFragment();
        Bundle args = new Bundle();
        if (currentOrder != null) {
            args.putString("orderId", currentOrder.getOrderId());
            args.putString("supportPhone", currentOrder.getSupportPhone());
            args.putString("riderName", currentOrder.getRiderName());
        }
        supportHelpFragment.setArguments(args);
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, supportHelpFragment)
                .addToBackStack(null)
                .commit();
    }

    private boolean canSubmitReview(@Nullable String status) {
        return status != null && status.equalsIgnoreCase("Delivered");
    }

    private void onOrderItemSelectedForReview(@NonNull Map<String, Object> item) {
        if (!canSubmitReview(currentOrderStatus)) {
            CustomToast.showInfo(getContext(), "Reviews can be added after delivery");
            return;
        }

        String itemName = valueOrFallback(readString(item, "productName", "name"), "Item");
        Long productId = readLong(item, "productId");
        showReviewDialog(
                "Review Item",
                itemName,
                productId,
                false
        );
    }

    private void openFarmReviewDialog() {
        if (!canSubmitReview(currentOrderStatus)) {
            CustomToast.showInfo(getContext(), "Farm reviews are available after delivery");
            return;
        }
        String farmName = currentOrder != null ? valueOrFallback(currentOrder.getFarmName(), "Farm") : "Farm";
        showReviewDialog("Review Farm", farmName, null, true);
    }

    private void showReviewDialog(
            @NonNull String title,
            @NonNull String targetName,
            @Nullable Long productId,
            boolean farmReview
    ) {
        if (!isAdded()) {
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_submit_review, null, false);
        TextView tvTarget = dialogView.findViewById(R.id.tvReviewTarget);
        RatingBar ratingBar = dialogView.findViewById(R.id.ratingReview);
        TextInputEditText inputComment = dialogView.findViewById(R.id.inputReviewComment);

        tvTarget.setText(targetName);
        ratingBar.setRating(0f);
        inputComment.setText("");

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Submit", (dialog, which) -> {
                    double rating = ratingBar.getRating();
                    String comment = inputComment.getText() != null ? inputComment.getText().toString().trim() : "";
                    if (rating <= 0f) {
                        CustomToast.showWarning(getContext(), "Please select a rating");
                        return;
                    }
                    submitReviewToFirebase(rating, comment, targetName, productId, farmReview);
                })
                .show();
    }

    private void submitReviewToFirebase(
            double rating,
            @NonNull String comment,
            @NonNull String targetName,
            @Nullable Long productId,
            boolean farmReview
    ) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            CustomToast.showWarning(getContext(), "Please login to submit a review");
            return;
        }

        String fallbackReviewerName = valueOrFallback(user.getDisplayName(), "GreenCart User");
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String reviewerName = buildReviewerName(
                            doc.getString("first_name"),
                            doc.getString("last_name"),
                            fallbackReviewerName
                    );
                    saveReview(rating, comment, targetName, productId, farmReview, user, reviewerName);
                })
                .addOnFailureListener(e ->
                        saveReview(rating, comment, targetName, productId, farmReview, user, fallbackReviewerName));
    }

    private void saveReview(
            double rating,
            @NonNull String comment,
            @NonNull String targetName,
            @Nullable Long productId,
            boolean farmReview,
            @NonNull FirebaseUser user,
            @NonNull String reviewerName
    ) {
        Map<String, Object> review = new HashMap<>();
        review.put("firebaseUid", user.getUid());
        review.put("orderId", currentOrder != null ? currentOrder.getId() : null);
        review.put("orderCode", currentOrder != null ? currentOrder.getOrderId() : null);
        review.put("targetType", farmReview ? "farm" : "product");
        review.put("targetName", targetName);
        review.put("productId", farmReview ? null : productId);
        review.put("farmId", null);
        review.put("farmName", currentOrder != null ? currentOrder.getFarmName() : null);
        review.put("hubName", currentOrder != null ? currentOrder.getHubName() : null);
        review.put("reviewerName", reviewerName);
        review.put("reviewerInitial", reviewerInitial(reviewerName));
        review.put("reviewDateLabel", buildReviewDateLabel());
        review.put("rating", rating);
        review.put("comment", comment);
        review.put("verifiedPurchase", true);
        review.put("helpfulCount", 0);
        review.put("createdAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(new Date()));
        review.put("mysqlSyncStatus", "PENDING");

        db.collection("reviews")
                .add(review)
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) {
                        return;
                    }
                    syncReviewToMySql(doc.getId(), review);
                    CustomToast.showSuccess(getContext(), "Thank you! Your review was submitted.");
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) {
                        return;
                    }
                    CustomToast.showError(getContext(), "Unable to submit review right now");
                });
    }

    private void syncReviewToMySql(@NonNull String reviewDocId, @NonNull Map<String, Object> review) {
        Map<String, Object> payload = new HashMap<>(review);
        payload.put("id", reviewDocId);

        apiService.saveReviewToMySql(payload).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    db.collection("reviews").document(reviewDocId).update(
                            "mysqlSyncStatus", "SYNCED",
                            "mysqlSyncAt", FieldValue.serverTimestamp(),
                            "mysqlSyncError", FieldValue.delete()
                    );
                    return;
                }
                db.collection("reviews").document(reviewDocId).update(
                        "mysqlSyncStatus", "FAILED",
                        "mysqlSyncAt", FieldValue.serverTimestamp(),
                        "mysqlSyncError", "HTTP " + response.code()
                );
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                db.collection("reviews").document(reviewDocId).update(
                        "mysqlSyncStatus", "FAILED",
                        "mysqlSyncAt", FieldValue.serverTimestamp(),
                        "mysqlSyncError", t.getMessage() != null ? t.getMessage() : "network error"
                );
            }
        });
    }

    @NonNull
    private String buildReviewerName(@Nullable String firstName, @Nullable String lastName, @NonNull String fallback) {
        String first = firstName != null ? firstName.trim() : "";
        String last = lastName != null ? lastName.trim() : "";
        String fullName = (first + " " + last).trim();
        return !TextUtils.isEmpty(fullName) ? fullName : fallback;
    }

    @NonNull
    private String reviewerInitial(@NonNull String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "G";
        }
        return String.valueOf(Character.toUpperCase(trimmed.charAt(0)));
    }

    @NonNull
    private String buildReviewDateLabel() {
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date());
    }

    private void dialNumber(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            CustomToast.showInfo(getContext(), "Phone number not available yet");
            return;
        }
        // Try direct call if permission granted, otherwise request permission
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phoneNumber)));
            } catch (SecurityException se) {
                // fallback to dialer
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phoneNumber)));
            }
            return;
        }

        // Save pending number and request permission
        pendingPhoneToCall = phoneNumber;
        requestPermissions(new String[]{android.Manifest.permission.CALL_PHONE}, 2001);
    }

    private String pendingPhoneToCall = null;

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setMapToolbarEnabled(true);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setZoomGesturesEnabled(true);
        googleMap.getUiSettings().setScrollGesturesEnabled(true);
        googleMap.getUiSettings().setRotateGesturesEnabled(true);
        googleMap.getUiSettings().setTiltGesturesEnabled(true);
        renderRouteMap();
        if (btnOrderMyLocation != null) btnOrderMyLocation.setOnClickListener(v -> centerToMyLocationForOrder());
    }

    private void centerToMyLocationForOrder() {
        if (googleMap == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1002);
            return;
        }
        try { googleMap.setMyLocationEnabled(true); } catch (SecurityException ignored) {}
        fusedClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc == null) {
                CustomToast.showInfo(getContext(), "Unable to get current location");
                return;
            }
            LatLng p = new LatLng(loc.getLatitude(), loc.getLongitude());
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(p, 16f));
        });
    }

    private void renderRouteMap() {
        if (googleMap == null || currentOrder == null) {
            return;
        }
        googleMap.clear();
        List<LatLng> points = new ArrayList<>();

        LatLng hub = resolveHubPoint(currentOrder);
        points.add(hub);
        googleMap.addMarker(new MarkerOptions()
                .position(hub)
                .title(resolveHubName(currentOrder))
                .snippet(DEFAULT_HUB_ADDRESS)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));

        LatLng customer = resolveDeliveryPoint(currentOrder);
        if (customer != null) {
            points.add(customer);
            googleMap.addMarker(new MarkerOptions()
                    .position(customer)
                    .title("Shipping address")
                    .snippet(valueOrFallback(currentOrder.getDeliveryAddress(), "Your home"))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        } else if (!geocodeInProgress && !TextUtils.isEmpty(currentOrder.getDeliveryAddress())) {
            geocodeInProgress = true;
            geocodeDeliveryAddress(currentOrder.getDeliveryAddress());
        }

        if (points.size() >= 2) {
            fetchNavigationRoute(hub, points.get(1), "motorcycling", new DirectionsRouteListener() {
                @Override
                public void onSuccess(@NonNull List<LatLng> routePoints, @NonNull String distanceText, @NonNull String durationText) {
                    if (!isAdded() || googleMap == null) {
                        return;
                    }
                    googleMap.addPolyline(new PolylineOptions()
                            .addAll(routePoints)
                            .width(10f)
                            .geodesic(true)
                            .color(Color.parseColor("#2E7D32")));

                    // cache the last successful route for fullscreen view
                    currentRoutePoints = new ArrayList<>(routePoints);

                    tvFreshnessDistance.setText(String.format(Locale.getDefault(), "Hub to home %s", distanceText));
                    tvFreshnessEta.setText(durationText);

                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                    for (LatLng point : routePoints) {
                        builder.include(point);
                    }
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 140));
                }

                @Override
                public void onFailure(@NonNull String reason) {
                    if (!isAdded() || googleMap == null) {
                        return;
                    }
                    // Fallback to straight line when API route is unavailable.
                    googleMap.addPolyline(new PolylineOptions()
                            .addAll(points)
                            .width(10f)
                            .geodesic(true)
                            .color(Color.parseColor("#2E7D32")));
                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                    for (LatLng point : points) {
                        builder.include(point);
                    }
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 140));
                }
            });
        } else if (points.size() == 1) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(points.get(0), 13f));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (!TextUtils.isEmpty(pendingPhoneToCall)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + pendingPhoneToCall)));
                    } catch (SecurityException se) {
                        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + pendingPhoneToCall)));
                    }
                }
            } else {
                // Permission denied - open dialer as fallback if we have the number
                if (!TextUtils.isEmpty(pendingPhoneToCall)) {
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + pendingPhoneToCall)));
                }
            }
            pendingPhoneToCall = null;
        }
    }

    private void fetchNavigationRoute(
            @NonNull LatLng origin,
            @NonNull LatLng destination,
            @NonNull String mode,
            @NonNull DirectionsRouteListener listener
    ) {
        String apiKey = BuildConfig.MAPS_API_KEY;
        if (TextUtils.isEmpty(apiKey)) {
            listener.onFailure("Missing maps API key");
            return;
        }

        String requestedMode = mode.toLowerCase(Locale.getDefault());
        requestDirections(origin, destination, requestedMode, apiKey, new DirectionsRouteListener() {
            @Override
            public void onSuccess(@NonNull List<LatLng> points, @NonNull String distanceText, @NonNull String durationText) {
                listener.onSuccess(points, distanceText, durationText);
            }

            @Override
            public void onFailure(@NonNull String reason) {
                if (!"motorcycling".equals(requestedMode)) {
                    listener.onFailure(reason);
                    return;
                }
                // Directions API can reject unsupported mode values in some regions/projects.
                requestDirections(origin, destination, "driving", apiKey, listener);
            }
        });
    }

    private void requestDirections(
            @NonNull LatLng origin,
            @NonNull LatLng destination,
            @NonNull String mode,
            @NonNull String apiKey,
            @NonNull DirectionsRouteListener listener
    ) {
        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection connection = null;
            try {
                String url = DIRECTIONS_ENDPOINT
                        + "?origin=" + origin.latitude + "," + origin.longitude
                        + "&destination=" + destination.latitude + "," + destination.longitude
                        + "&mode=" + mode
                        + "&key=" + apiKey;

                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String responseBody = readAll(stream);
                parseDirectionsResponse(responseBody, listener);
            } catch (Exception e) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> listener.onFailure("Route fetch failed"));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void parseDirectionsResponse(@Nullable String responseBody, @NonNull DirectionsRouteListener listener) {
        if (TextUtils.isEmpty(responseBody)) {
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> listener.onFailure("Empty route response"));
            return;
        }
        try {
            JSONObject json = new JSONObject(responseBody);
            String status = json.optString("status");
            if (!"OK".equalsIgnoreCase(status)) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> listener.onFailure(status));
                return;
            }

            JSONArray routes = json.optJSONArray("routes");
            if (routes == null || routes.length() == 0) {
                if (!isAdded()) {
                    return;
                }
                requireActivity().runOnUiThread(() -> listener.onFailure("No routes found"));
                return;
            }

            JSONObject firstRoute = routes.getJSONObject(0);
            JSONObject polylineObj = firstRoute.getJSONObject("overview_polyline");
            String encoded = polylineObj.optString("points");

            JSONArray legs = firstRoute.optJSONArray("legs");
            JSONObject leg = legs != null && legs.length() > 0 ? legs.getJSONObject(0) : null;
            String distanceText = leg != null && leg.optJSONObject("distance") != null
                    ? leg.getJSONObject("distance").optString("text", "-- km")
                    : "-- km";
            String durationText = leg != null && leg.optJSONObject("duration") != null
                    ? leg.getJSONObject("duration").optString("text", "-- mins")
                    : "-- mins";

            List<LatLng> decoded = decodePolyline(encoded);
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> listener.onSuccess(decoded, distanceText, durationText));
        } catch (Exception e) {
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> listener.onFailure("Unable to parse route"));
        }
    }

    @NonNull
    private String readAll(@Nullable InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    @NonNull
    private List<LatLng> decodePolyline(@Nullable String encoded) {
        List<LatLng> polyline = new ArrayList<>();
        if (TextUtils.isEmpty(encoded)) {
            return polyline;
        }

        int index = 0;
        int lat = 0;
        int lng = 0;
        while (index < encoded.length()) {
            int b;
            int shift = 0;
            int result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int deltaLat = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lat += deltaLat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int deltaLng = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lng += deltaLng;

            polyline.add(new LatLng(lat / 1E5, lng / 1E5));
        }
        return polyline;
    }

    private void geocodeDeliveryAddress(@NonNull String address) {
        Executors.newSingleThreadExecutor().execute(() -> {
            LatLng geocoded = null;
            try {
                Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                List<android.location.Address> matches = geocoder.getFromLocationName(address, 1);
                if (matches != null && !matches.isEmpty()) {
                    android.location.Address first = matches.get(0);
                    geocoded = new LatLng(first.getLatitude(), first.getLongitude());
                }
            } catch (Exception ignored) {
            }

            LatLng finalGeocoded = geocoded;
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                geocodeInProgress = false;
                if (finalGeocoded != null) {
                    resolvedDeliveryPoint = finalGeocoded;
                    tvFreshnessDistance.setText(buildDistanceLabel(currentOrder));
                    renderRouteMap();
                } else {
                    tvFreshnessDistance.setText("Hub to home route (address unresolved)");
                }
            });
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (mapView != null) {
            mapView.onDestroy();
        }
        super.onDestroyView();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    @Nullable
    private String readString(@NonNull Map<String, Object> source, @NonNull String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }

    @Nullable
    private Long readLong(@NonNull Map<String, Object> source, @NonNull String key) {
        Object value = source.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}


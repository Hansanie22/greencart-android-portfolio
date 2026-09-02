package com.hansanie.greencart.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.Timestamp;
import com.google.android.material.tabs.TabLayout;
import com.hansanie.greencart.R;
import com.hansanie.greencart.adapter.OrderAdapter;
import com.hansanie.greencart.database.AppDatabase;
import com.hansanie.greencart.model.Order;
import com.hansanie.greencart.model.OrderItem;
import com.hansanie.greencart.network.ApiService;
import com.hansanie.greencart.network.RetrofitClient;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrdersFragment extends Fragment implements OrderAdapter.OnOrderClickListener {

    private static final String DEFAULT_HUB_NAME = "Green Cart Hub";
    private static final double DEFAULT_HUB_LAT = 6.9156395;
    private static final double DEFAULT_HUB_LON = 79.8667641;
    private static final String TAB_ONGOING = "ONGOING";
    private static final String TAB_HISTORY = "HISTORY";
    private static final String TAB_SUBSCRIPTIONS = "SUBSCRIPTIONS";

    private RecyclerView rvOrders;
    private TabLayout tabLayout;
    private List<Order> orderList;
    private OrderAdapter adapter;
    private FirebaseFirestore db;
    private ApiService apiService;
    private String selectedTab = TAB_ONGOING;
    private final List<Order> latestOrders = new ArrayList<>();
    // Track deleted doc IDs to prevent infinite snapshot loop
    private final java.util.Set<String> deletedDocIds = new java.util.HashSet<>();
    @Nullable
    private ListenerRegistration ordersListener;
    private androidx.constraintlayout.widget.ConstraintLayout layoutEmptyOrders;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_orders, container, false);
        layoutEmptyOrders = view.findViewById(R.id.layoutEmptyOrders);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rvOrders = view.findViewById(R.id.rvOrders);
        tabLayout = view.findViewById(R.id.tabLayoutOrders);
        db = FirebaseFirestore.getInstance();
        apiService = RetrofitClient.getApiService();
        layoutEmptyOrders = view.findViewById(R.id.layoutEmptyOrders);
        setupTabs();
        setupRecyclerView();
        loadOrders();
    }

    @Override
    public void onStop() {
        super.onStop();
        // detachOrdersListener(); // Removed as per best practice
    }

    private void setupTabs() {
        tabLayout.removeAllTabs();
        tabLayout.addTab(tabLayout.newTab().setText("Ongoing"));
        tabLayout.addTab(tabLayout.newTab().setText("History"));
        tabLayout.addTab(tabLayout.newTab().setText("Subscriptions"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                if (position == 0) {
                    selectedTab = TAB_ONGOING;
                } else if (position == 1) {
                    selectedTab = TAB_HISTORY;
                } else {
                    selectedTab = TAB_SUBSCRIPTIONS;
                }
                renderOrders(latestOrders);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                onTabSelected(tab);
            }
        });
    }

    private void setupRecyclerView() {
        orderList = new ArrayList<>();
        rvOrders.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new OrderAdapter(getContext(), orderList, this);
        rvOrders.setAdapter(adapter);

        // Enable swipe-to-cancel
        androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback swipeCallback = new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                if (pos >= 0 && pos < orderList.size()) {
                    Order order = orderList.get(pos);
                    // Only allow cancel if Pending
                    String status = order.getStatus();
                    if (status == null || status.trim().isEmpty()) status = order.getOrderStatus();
                    if ("Pending".equalsIgnoreCase(status)) {
                        onCancelClick(order);
                    } else {
                        adapter.notifyItemChanged(pos); // reset swipe if not allowed
                    }
                }
            }
        };
        new androidx.recyclerview.widget.ItemTouchHelper(swipeCallback).attachToRecyclerView(rvOrders);
    }

    private void loadOrders() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            detachOrdersListener();
            loadOrdersFromLocal();
            return;
        }

        if (ordersListener != null) {
            return;
        }

        ordersListener = db.collection("orders")
                .whereEqualTo("firebaseUid", uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) {
                        loadOrdersFromLocal();
                        return;
                    }

                    // Group docs by orderCode — skip already-deleted docs
                    LinkedHashMap<String, List<DocumentSnapshot>> grouped = new LinkedHashMap<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        // Skip docs we already deleted — avoids infinite loop
                        if (deletedDocIds.contains(doc.getId())) {
                            continue;
                        }
                        String orderCode = asString(doc.get("orderCode"));
                        String groupKey = (orderCode != null && !orderCode.trim().isEmpty())
                                ? orderCode.trim().toUpperCase(Locale.ROOT)
                                : doc.getId();
                        if (!grouped.containsKey(groupKey)) {
                            grouped.put(groupKey, new ArrayList<>());
                        }
                        grouped.get(groupKey).add(doc);
                    }

                    // Pick best doc per group, schedule deletes AFTER rendering
                    LinkedHashMap<String, Order> resolved = new LinkedHashMap<>();
                    List<DocumentSnapshot> toDelete = new ArrayList<>();

                    for (Map.Entry<String, List<DocumentSnapshot>> entry : grouped.entrySet()) {
                        List<DocumentSnapshot> docs = entry.getValue();

                        DocumentSnapshot bestDoc = docs.get(0);
                        for (DocumentSnapshot doc : docs) {
                            if (statusPriorityFromDoc(doc) > statusPriorityFromDoc(bestDoc)) {
                                bestDoc = doc;
                            }
                        }

                        // Collect duplicates to delete — don't delete yet
                        for (DocumentSnapshot doc : docs) {
                            if (!doc.getId().equals(bestDoc.getId())) {
                                toDelete.add(doc);
                            }
                        }

                        Order order = mapOrder(bestDoc);
                        syncConfirmedOrderToMySqlIfNeeded(bestDoc, order);
                        resolved.put(entry.getKey(), order);
                    }

                    // Render first
                    latestOrders.clear();
                    latestOrders.addAll(resolved.values());
                    sortOrdersNewestFirst(latestOrders);
                    cacheOrders(latestOrders);
                    renderOrders(latestOrders);

                    // Delete duplicates AFTER render — track deleted IDs to skip on next snapshot
                    for (DocumentSnapshot doc : toDelete) {
                        deletedDocIds.add(doc.getId()); // mark before delete
                        android.util.Log.d("OrdersFragment", "Deleting duplicate: " + doc.getId());
                        doc.getReference().delete();
                    }
                });
    }
            @Override
            public void onDestroyView() {
                super.onDestroyView();
                detachOrdersListener();
                deletedDocIds.clear();
            }
    // Helper to determine status priority for deduplication
    private int statusPriorityFromDoc(@NonNull DocumentSnapshot doc) {
        String status = asString(doc.get("status"));
        if (status == null) status = asString(doc.get("orderStatus"));
        if (status == null) return 0;
        switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "delivered":        return 6;
            case "out_for_delivery":
            case "out for delivery": return 5;
            case "confirmed":
            case "processing":       return 4;
            case "active":           return 3;
            case "pending":
            case "placed":           return 2;
            case "cancelled":
            case "canceled":         return 1;
            default:                  return 0;
        }
    }

    private void detachOrdersListener() {
        if (ordersListener != null) {
            ordersListener.remove();
            ordersListener = null;
        }
    }

    @NonNull
        private String buildOrderKey(@NonNull Order order) {
            String docId = order.getFirestoreDocumentId();
            if (docId != null && !docId.trim().isEmpty()) {
                return docId;
            }
            String orderCode = order.getOrderCode();
            if (orderCode != null && !orderCode.trim().isEmpty()) {
                return orderCode.trim().toUpperCase(Locale.ROOT);
            }
            return String.valueOf(resolveSortTime(order));
        }

    @NonNull
    private Order mapOrder(@NonNull DocumentSnapshot doc) {
        Order order = new Order();
        order.setFirestoreDocumentId(doc.getId());
        order.setId(asLong(doc.get("id")));
        if (order.getId() == null) {
            order.setId(deriveNumericId(doc.getId()));
        }
        order.setUserId(asLong(doc.get("userId")));
        order.setAddressId(firstNonBlank(
                asString(doc.get("addressId")),
                asString(doc.get("address_id"))
        ));
        order.setSubscriptionId(asLong(doc.get("subscriptionId")));
        order.setSubscriptionOrder(asBoolean(doc.get("isSubscription")) || order.getSubscriptionId() != null);
        order.setTotalAmount(asDouble(doc.get("totalAmount")));
        order.setSubtotal(asDouble(doc.get("subtotal")));
        order.setShipping(asDouble(doc.get("shipping")));
        order.setDiscountAmount(asDouble(doc.get("discountAmount"), asDouble(doc.get("discount"))));
        order.setGreenPointsRedeemed(asInt(doc.get("greenPointsRedeemed"), 0));
        order.setGreenPointsRedeemValue(asDouble(doc.get("greenPointsRedeemValue"), 0.0));
        order.setOrderCode(asString(doc.get("orderCode")));

        // Always prefer the 'status' field from Firestore, fallback to 'orderStatus'
        String rawStatus = asString(doc.get("status"));
        String rawOrderStatus = asString(doc.get("orderStatus"));
        String normalizedStatus = null;
        if (rawStatus != null && !rawStatus.trim().isEmpty()) {
            normalizedStatus = normalizeStatus(rawStatus);
        } else if (rawOrderStatus != null && !rawOrderStatus.trim().isEmpty()) {
            normalizedStatus = normalizeStatus(rawOrderStatus);
        }
        // Log raw and normalized status for debugging
        android.util.Log.d("OrdersFragment", "mapOrder: code=" + order.getOrderCode() + ", rawStatus=" + rawStatus + ", rawOrderStatus=" + rawOrderStatus + ", normalizedStatus=" + normalizedStatus);

        order.setOrderStatus(normalizedStatus); // for compatibility
        order.setStatus(normalizedStatus);
        order.setPaymentStatus(asString(doc.get("paymentStatus")));
        Date createdAt = firstNonNullDate(
                asDate(doc.get("orderDate")),
                asDate(doc.get("order_date")),
                asDate(doc.get("createdAt")),
                asDate(doc.get("created_at")),
                asDate(doc.get("pendingAt")),
                asDate(doc.get("pending_at"))
        );
        Date pendingAt = firstNonNullDate(
                asDate(doc.get("pendingAt")),
                asDate(doc.get("pending_at")),
                createdAt
        );
        Date confirmedAt = firstNonNullDate(
                asDate(doc.get("confirmedAt")),
                asDate(doc.get("confirmed_at")),
                asDate(doc.get("confirmedAtIso")) // ISO fallback
        );
        Date outForDeliveryAt = firstNonNullDate(
                asDate(doc.get("outForDeliveryAt")),
                asDate(doc.get("out_for_delivery_at")),
                asDate(doc.get("outForDeliveryAtIso")) // ISO fallback
        );
        Date deliveredAt = firstNonNullDate(
                asDate(doc.get("deliveredAt")),
                asDate(doc.get("delivered_at")),
                asDate(doc.get("deliveredAtIso")) // ISO fallback
        );
        Date displayDate = resolveDisplayDate(normalizedStatus, pendingAt, confirmedAt, outForDeliveryAt, deliveredAt, createdAt);

        order.setOrderDate(formatDateTime(displayDate));
        order.setCreatedAt(toIsoDateTime(createdAt));
        order.setNotes(asString(doc.get("notes")));
        order.setPromoCode(asString(doc.get("promoCode")));
        // Map additional discount / offer fields so Order carries all discount info
        order.setOfferId(asLong(doc.get("offerId")));
        order.setOfferPercentage(asDouble(doc.get("offerPercentage")));
        order.setPromoOfferId(asLong(doc.get("promoOfferId")));
        order.setPromoDiscountPercent(asDouble(doc.get("promoDiscountPercent"), asDouble(doc.get("promo_discount_percent"))));
        order.setPromoDiscount(asDouble(doc.get("promoDiscount"), asDouble(doc.get("promo_discount"))));
        // Subscription discount might be stored as subscriptionDiscount or subscription_discount
        order.setSubscriptionDiscount(asDouble(doc.get("subscriptionDiscount"), asDouble(doc.get("subscription_discount"))));
        order.setFirebaseUid(asString(doc.get("firebaseUid")));
        order.setDeliveryAddress(firstNonBlank(
                asString(doc.get("deliveryAddress")),
                asString(doc.get("addressLine"))
        ));
        order.setDeliveryLatitude(asDouble(doc.get("deliveryLatitude")));
        order.setDeliveryLongitude(asDouble(doc.get("deliveryLongitude")));
        order.setHubName(asString(doc.get("hubName")));
        order.setHubLatitude(asDouble(doc.get("hubLatitude")));
        order.setHubLongitude(asDouble(doc.get("hubLongitude")));
        if (order.getHubLatitude() == null || order.getHubLongitude() == null) {
            order.setHubName(DEFAULT_HUB_NAME);
            order.setHubLatitude(DEFAULT_HUB_LAT);
            order.setHubLongitude(DEFAULT_HUB_LON);
        }
        order.setFarmName(asString(doc.get("farmName")));
        order.setFarmAddress(asString(doc.get("farmAddress")));
        order.setFarmLatitude(asDouble(doc.get("farmLatitude")));
        order.setFarmLongitude(asDouble(doc.get("farmLongitude")));
        order.setRiderName(asString(doc.get("riderName")));
        order.setRiderPhone(asString(doc.get("riderPhone")));
        order.setSupportPhone(firstNonBlank(asString(doc.get("supportPhone")), "+94 75 8497065"));
        order.setEstimatedArrival(asString(doc.get("estimatedArrival")));
        order.setPendingAt(toIsoDateTime(pendingAt));
        order.setConfirmedAt(toIsoDateTime(confirmedAt));
        order.setOutForDeliveryAt(toIsoDateTime(outForDeliveryAt));
        order.setDeliveredAt(toIsoDateTime(deliveredAt));
        order.setArrivalNotified(asBoolean(doc.get("arrivalNotified")));
        int points = asInt(doc.get("greenPointsEarned"), -1);
        if (points < 0) {
            double total = order.getTotalAmount() != null ? order.getTotalAmount() : 0.0;
            points = (int) Math.floor(total / 1000.0) * 5;
        }
        order.setGreenPointsEarned(points);
        order.setItems(asItemList(doc.get("items")));
        return order;
    }

    @Nullable
    private Date resolveDisplayDate(
            @Nullable String normalizedStatus,
            @Nullable Date pendingAt,
            @Nullable Date confirmedAt,
            @Nullable Date outForDeliveryAt,
            @Nullable Date deliveredAt,
            @Nullable Date createdAt
    ) {
        if (normalizedStatus == null) {
            return firstNonNullDate(createdAt, pendingAt, confirmedAt, outForDeliveryAt, deliveredAt);
        }
        if ("Delivered".equalsIgnoreCase(normalizedStatus)
                || "Cancelled".equalsIgnoreCase(normalizedStatus)
                || "Canceled".equalsIgnoreCase(normalizedStatus)) {
            return firstNonNullDate(deliveredAt, outForDeliveryAt, confirmedAt, pendingAt, createdAt);
        }
        if ("Out for Delivery".equalsIgnoreCase(normalizedStatus)) {
            return firstNonNullDate(outForDeliveryAt, confirmedAt, pendingAt, createdAt);
        }
        if ("Confirmed".equalsIgnoreCase(normalizedStatus)) {
            return firstNonNullDate(confirmedAt, pendingAt, createdAt);
        }
        return firstNonNullDate(pendingAt, createdAt, confirmedAt, outForDeliveryAt, deliveredAt);
    }

    private String formatDateTime(@Nullable Date parsed) {
        if (parsed == null) {
            return "Date unavailable";
        }
        DateFormat formatter = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault());
        return formatter.format(parsed);
    }

    @Nullable
    private Date parseDate(@NonNull String raw) {
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd HH:mm:ss",
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

    @NonNull
    private List<OrderItem> asItemList(@Nullable Object value) {
        List<OrderItem> result = new ArrayList<>();
        if (!(value instanceof List)) {
            return result;
        }
        for (Object item : (List<?>) value) {
            if (item instanceof Map) {
                result.add(mapToOrderItem((Map<?, ?>) item));
            }
        }
        return result;
    }

    @NonNull
    private OrderItem mapToOrderItem(@NonNull Map<?, ?> item) {
        Object orderIdValue = item.containsKey("orderId") ? item.get("orderId") : item.get("order_id");
        Object variantIdValue = item.containsKey("variantId") ? item.get("variantId") : item.get("variant_id");
        Object productIdValue = item.containsKey("productId") ? item.get("productId") : item.get("product_id");
        Object priceAtPurchaseValue = item.containsKey("priceAtPurchase") ? item.get("priceAtPurchase") : item.get("price_at_purchase");
        Object unitPriceValue = item.containsKey("unitPrice") ? item.get("unitPrice") : item.get("unit_price");

        OrderItem orderItem = OrderItem.builder()
                .id(asLong(item.get("id")))
                .orderId(asLong(orderIdValue))
                .variantId(asLong(variantIdValue))
                .productId(asLong(productIdValue))
                .quantity(asInt(item.get("quantity"), 1))
                .priceAtPurchase(asDouble(priceAtPurchaseValue, asDouble(unitPriceValue, asDouble(item.get("price"), 0.0))))
                .unitPrice(asDouble(unitPriceValue, asDouble(priceAtPurchaseValue, asDouble(item.get("price"), 0.0))))
                .build();

        // Populate transient name fields from the map (accept multiple key variants)
        Object nameVal = item.containsKey("productName") ? item.get("productName") : (item.containsKey("name") ? item.get("name") : item.get("product_name"));
        if (nameVal != null) {
            orderItem.setProductName(String.valueOf(nameVal));
        }
        Object variantVal = item.containsKey("variantName") ? item.get("variantName") : (item.containsKey("variant") ? item.get("variant") : item.get("variant_name"));
        if (variantVal != null) {
            orderItem.setVariantName(String.valueOf(variantVal));
        }
        // image URL (common keys)
        Object imageVal = item.containsKey("imageUrl") ? item.get("imageUrl") : (item.containsKey("image") ? item.get("image") : item.get("image_url"));
        if (imageVal != null) {
            orderItem.setImageUrl(String.valueOf(imageVal));
        }

        return orderItem;
    }

    @Nullable
    private Long asLong(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
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

    @Nullable
    private Double asDouble(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @Nullable
    private Double asDouble(@Nullable Object value, @Nullable Double fallback) {
        Double parsed = asDouble(value);
        return parsed != null ? parsed : fallback;
    }

    private int asInt(@Nullable Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private boolean asBoolean(@Nullable Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean(((String) value).trim());
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return false;
    }

    @Nullable
    private String asString(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    @Nullable
    private String firstNonBlank(@Nullable String primary, @Nullable String fallback) {
        return primary != null && !primary.trim().isEmpty() ? primary : fallback;
    }

    @Nullable
    private String normalizeStatus(@Nullable String rawStatus) {
        if (rawStatus == null) {
            return null;
        }
        String normalized = rawStatus.trim().replace('_', ' ').toUpperCase();
        switch (normalized) {
            case "PLACED":
            case "PENDING":
            case "ACTIVE":
                return "Pending";
            case "CONFIRMED":
            case "PROCESSING":
                return "Confirmed";
            case "OUT FOR DELIVERY":
            case "DISPATCHED":
                return "Out for Delivery";
            case "DELIVERED":
                return "Delivered";
            case "CANCELLED":
            case "CANCELED":
                return "Cancelled";
            default:
                return toTitleCase(rawStatus);
        }
    }

    @NonNull
    private String toTitleCase(@NonNull String value) {
        String[] parts = value.trim().replace('_', ' ').toLowerCase().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.length() > 0 ? builder.toString() : value;
    }

    @Nullable
    private Date asDate(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate();
        }
        if (value instanceof Number) {
            long epoch = ((Number) value).longValue();
            if (epoch > 0 && epoch < 100000000000L) {
                epoch *= 1000L;
            }
            return epoch > 0 ? new Date(epoch) : null;
        }
        if (value instanceof String) {
            return parseDate(((String) value).trim());
        }
        return parseDate(String.valueOf(value).trim());
    }

    @Nullable
    private Date firstNonNullDate(@Nullable Date... candidates) {
        if (candidates == null) {
            return null;
        }
        for (Date candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    private String toIsoDateTime(@Nullable Date date) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(date);
    }

    @NonNull
    private Long deriveNumericId(@NonNull String documentId) {
        String digits = documentId.replaceAll("\\D+", "");
        if (!digits.isEmpty()) {
            try {
                return Long.parseLong(digits);
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.abs((long) documentId.hashCode());
    }

    private void renderOrders(List<Order> allOrders) {
        orderList.clear();
        java.util.HashSet<String> seenOrderCodes = new java.util.HashSet<>();
        for (Order order : allOrders) {
            boolean isSubscriptionOrder = isSubscriptionOrder(order);
            String orderKey = buildOrderKey(order);
            if (!seenOrderCodes.contains(orderKey)) {
                if (TAB_SUBSCRIPTIONS.equals(selectedTab) && isSubscriptionOrder) {
                    orderList.add(order);
                    seenOrderCodes.add(orderKey);
                } else if (TAB_ONGOING.equals(selectedTab) && !isSubscriptionOrder) {
                    String status = normalizeStatus(order.getStatus());
                    if (status == null) status = normalizeStatus(order.getOrderStatus());
                    if ("Pending".equalsIgnoreCase(status)
                        || "Confirmed".equalsIgnoreCase(status)
                        || "Out for Delivery".equalsIgnoreCase(status)) {
                        orderList.add(order);
                        seenOrderCodes.add(orderKey);
                    }
                } else if (TAB_HISTORY.equals(selectedTab) && !isSubscriptionOrder) {
                    String status = normalizeStatus(order.getStatus());
                    if (status == null) status = normalizeStatus(order.getOrderStatus());
                    if ("Delivered".equalsIgnoreCase(status)
                        || "Cancelled".equalsIgnoreCase(status)
                        || "Canceled".equalsIgnoreCase(status)) {
                        orderList.add(order);
                        seenOrderCodes.add(orderKey);
                    }
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (layoutEmptyOrders != null && rvOrders != null) {
            if (orderList.isEmpty()) {
                layoutEmptyOrders.setVisibility(View.VISIBLE);
                rvOrders.setVisibility(View.GONE);
            } else {
                layoutEmptyOrders.setVisibility(View.GONE);
                rvOrders.setVisibility(View.VISIBLE);
            }
        }
    }

    private void cacheOrders(List<Order> orders) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(requireContext()).orderDao().clearAll();
            AppDatabase.getInstance(requireContext()).orderDao().insertAll(orders);
        });
    }

    private void loadOrdersFromLocal() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Order> localOrders = AppDatabase.getInstance(requireContext()).orderDao().getAll();
            sortOrdersNewestFirst(localOrders);
            latestOrders.clear();
            latestOrders.addAll(localOrders);
            if (!isAdded()) {
                return;
            }
            requireActivity().runOnUiThread(() -> {
                renderOrders(localOrders);
            });
        });
    }

    private boolean isSubscriptionOrder(@Nullable Order order) {
        return order != null && (order.isSubscriptionOrder() || order.getSubscriptionId() != null);
    }

    private boolean isHistoryOrder(@Nullable Order order) {
        if (order == null) {
            return false;
        }
        return isTerminalStatus(order.getStatus()) || isTerminalStatus(order.getOrderStatus());
    }

    private boolean isTerminalStatus(@Nullable String status) {
        if (status == null) {
            return false;
        }
        return status.equalsIgnoreCase("Delivered")
                || status.equalsIgnoreCase("Cancelled")
                || status.equalsIgnoreCase("Canceled");
    }

    private void sortOrdersNewestFirst(@NonNull List<Order> orders) {
        orders.sort((left, right) -> Long.compare(resolveSortTime(right), resolveSortTime(left)));
    }

    private void syncConfirmedOrderToMySqlIfNeeded(@NonNull DocumentSnapshot doc, @NonNull Order order) {
        String normalizedStatus = normalizeStatus(firstNonBlank(order.getStatus(), order.getOrderStatus()));
        if (!"Confirmed".equalsIgnoreCase(normalizedStatus)) {
            return;
        }

        if (order.getId() == null || order.getId() <= 0) {
            return;
        }

        String syncState = asString(doc.get("mysqlConfirmedSyncStatus"));
        if ("IN_PROGRESS".equalsIgnoreCase(syncState) || "SYNCED".equalsIgnoreCase(syncState)) {
            return;
        }

        order.setOrderStatus("confirmed");
        order.setStatus("confirmed");

        doc.getReference().update(
                "mysqlConfirmedSyncStatus", "IN_PROGRESS",
                "mysqlConfirmedSyncAt", FieldValue.serverTimestamp()
        );

        apiService.saveOrderToMySql(order).enqueue(new Callback<Order>() {
            @Override
            public void onResponse(Call<Order> call, Response<Order> response) {
                if (response.isSuccessful()) {
                    doc.getReference().update(
                            "mysqlConfirmedSyncStatus", "SYNCED",
                            "mysqlConfirmedSyncAt", FieldValue.serverTimestamp(),
                            "mysqlConfirmedSyncError", FieldValue.delete()
                    );
                    return;
                }
                doc.getReference().update(
                        "mysqlConfirmedSyncStatus", "FAILED",
                        "mysqlConfirmedSyncAt", FieldValue.serverTimestamp(),
                        "mysqlConfirmedSyncError", "HTTP " + response.code()
                );
            }

            @Override
            public void onFailure(Call<Order> call, Throwable t) {
                doc.getReference().update(
                        "mysqlConfirmedSyncStatus", "FAILED",
                        "mysqlConfirmedSyncAt", FieldValue.serverTimestamp(),
                        "mysqlConfirmedSyncError", t.getMessage() != null ? t.getMessage() : "network error"
                );
            }
        });
    }

    private long resolveSortTime(@Nullable Order order) {
        if (order == null) {
            return Long.MIN_VALUE;
        }
        Date effectiveDate = firstNonNullDate(
                asDate(order.getDeliveredAt()),
                asDate(order.getOutForDeliveryAt()),
                asDate(order.getConfirmedAt()),
                asDate(order.getPendingAt()),
                asDate(order.getCreatedAt()),
                asDate(order.getOrderDate())
        );
        if (effectiveDate != null) {
            return effectiveDate.getTime();
        }
        if (order.getId() != null) {
            return order.getId();
        }
        return Long.MIN_VALUE;
    }


    @Override
    public void onDetailsClick(Order order) {
        // OrderDetailsFragment
        OrderDetailsFragment detailsFragment = new OrderDetailsFragment();

        Bundle bundle = new Bundle();
        bundle.putSerializable("selected_order", order);
        detailsFragment.setArguments(bundle);

        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, detailsFragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onCancelClick(Order order) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Cancel Order")
            .setMessage("Are you sure you want to cancel this order?")
            .setPositiveButton("Yes", (dialog, which) -> {
                // 1. Update status locally
                order.setStatus("Cancelled");
                order.setOrderStatus("Cancelled");
                // 2. Update Firestore
                if (order.getFirestoreDocumentId() != null) {
                    db.collection("orders")
                        .document(order.getFirestoreDocumentId())
                        .update("status", "Cancelled", "orderStatus", "Cancelled")
                        .addOnSuccessListener(unused -> {
                            android.util.Log.d("OrdersFragment", "Order status updated in Firestore");
                        });
                }
                // 3. Update MySQL via API
                if (order.getId() != null) {
                    apiService.updateOrderStatus(order.getId(), "Cancelled").enqueue(new retrofit2.Callback<Void>() {
                        @Override
                        public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                            android.util.Log.d("OrdersFragment", "Order status updated in MySQL");
                        }
                        @Override
                        public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                            android.util.Log.e("OrdersFragment", "MySQL update failed: " + t.getMessage());
                        }
                    });
                }
                // 4. Move to history (refresh list)
                renderOrders(latestOrders);
                android.widget.Toast.makeText(requireContext(), "Order cancelled", android.widget.Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("No", (dialog, which) -> {
                // Reset swipe if cancelled
                adapter.notifyDataSetChanged();
            })
            .show();
    }
}

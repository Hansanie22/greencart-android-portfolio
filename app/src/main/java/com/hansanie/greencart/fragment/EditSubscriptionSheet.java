package com.hansanie.greencart.fragment;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonObject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hansanie.greencart.R;
import com.hansanie.greencart.dto.SubscriptionItemUpsertRequest;
import com.hansanie.greencart.dto.SubscriptionSaveRequest;
import com.hansanie.greencart.model.GrocerySubscription;
import com.hansanie.greencart.model.GrocerySubscriptionItem;
import com.hansanie.greencart.network.ApiService;
import com.hansanie.greencart.network.RetrofitClient;
import com.hansanie.greencart.util.CustomToast;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditSubscriptionSheet extends BottomSheetDialogFragment {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String[] DELIVERY_DAYS = new String[]{
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    };

    private GrocerySubscription subscription;
    @Nullable
    private final Runnable onUpdated;
    private final List<View> itemRows = new ArrayList<>();
    private String selectedDeliveryDay = "Monday";
    private ApiService apiService;

    // ── Product search fields ─────────────────────────────────────────────────
    private TextInputEditText etSearchProduct;
    private ImageButton btnVoiceSearchProduct;
    private MaterialButton btnSearchProduct;
    private LinearLayout searchResultsContainer;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private long activeSearchRequestId = 0L;
    @Nullable
    private Runnable pendingSearchRunnable;
    private static final long SEARCH_DEBOUNCE_MS = 300L;
    private static final int MAX_SUGGESTIONS = 6;

    // ── Constructors ──────────────────────────────────────────────────────────

    public EditSubscriptionSheet(GrocerySubscription subscription) {
        this(subscription, null);
    }

    public EditSubscriptionSheet(GrocerySubscription subscription, @Nullable Runnable onUpdated) {
        this.subscription = subscription;
        this.onUpdated = onUpdated;
    }

    // ── onCreateView ──────────────────────────────────────────────────────────

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_edit, container, false);

        apiService = RetrofitClient.getApiService();

        TextInputEditText etName       = view.findViewById(R.id.etScheduleName);
        TextView tvTime                = view.findViewById(R.id.tvSelectedTime);
        TextView tvDate                = view.findViewById(R.id.tvSelectedDate);
        RadioGroup rgFrequency         = view.findViewById(R.id.rgFrequency);
        ImageButton btnClose           = view.findViewById(R.id.btnClose);
        MaterialButton btnSave         = view.findViewById(R.id.btnSaveSchedule);
        LinearLayout dayContainer      = view.findViewById(R.id.layoutEditDeliveryDays);
        LinearLayout itemsEditor       = view.findViewById(R.id.layoutSubscriptionItemsEditor);
        etSearchProduct                = view.findViewById(R.id.etSearchProduct);
        btnVoiceSearchProduct          = view.findViewById(R.id.btnVoiceSearchProduct);
        btnSearchProduct               = view.findViewById(R.id.btnSearchProduct);
        searchResultsContainer         = view.findViewById(R.id.layoutSearchResults);

        // ── Pre-fill existing data ────────────────────────────────────────────
        etName.setText(subscription.getName() != null ? subscription.getName() : "");
        tvTime.setText(!TextUtils.isEmpty(subscription.getDeliveryTimeSlot())
                ? subscription.getDeliveryTimeSlot() : "7:00 AM");
        tvDate.setText(!TextUtils.isEmpty(subscription.getNextDeliveryDate())
                ? subscription.getNextDeliveryDate()
                : LocalDate.now().plusDays(1).format(DATE_FORMATTER));

        String frequency = subscription.getFrequency() != null
                ? subscription.getFrequency().toUpperCase(Locale.getDefault()) : "WEEKLY";
        rgFrequency.check("BI_WEEKLY".equals(frequency) ? R.id.rbCustomDays : R.id.rbDaily);

        selectedDeliveryDay = resolveDeliveryDay(subscription.getDeliveryDay());
        bindDayButtons(dayContainer);
        bindInitialItems(itemsEditor);

        // ── Product search ────────────────────────────────────────────────────
        btnSearchProduct.setOnClickListener(v -> triggerProductSearch(false, itemsEditor));
        etSearchProduct.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                triggerProductSearch(true, itemsEditor);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        btnClose.setOnClickListener(v -> dismiss());

        // ── Time picker ───────────────────────────────────────────────────────
        view.findViewById(R.id.btnChangeTime).setOnClickListener(v -> {
            int currentHour = 7, currentMinute = 0;
            new TimePickerDialog(requireContext(), (tp, hour, minute) -> {
                String ampm = hour >= 12 ? "PM" : "AM";
                int displayHour = hour % 12;
                if (displayHour == 0) displayHour = 12;
                tvTime.setText(String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, ampm));
            }, currentHour, currentMinute, false).show();
        });

        // ── Date picker ───────────────────────────────────────────────────────
        view.findViewById(R.id.btnChangeDate).setOnClickListener(v -> {
            LocalDate base;
            try {
                base = LocalDate.parse(tvDate.getText().toString(), DATE_FORMATTER);
            } catch (Exception ignored) {
                base = LocalDate.now().plusDays(1);
            }
            new DatePickerDialog(requireContext(),
                    (picker, year, month, dayOfMonth) -> {
                        LocalDate selected = LocalDate.of(year, month + 1, dayOfMonth);
                        tvDate.setText(selected.format(DATE_FORMATTER));
                    },
                    base.getYear(), base.getMonthValue() - 1, base.getDayOfMonth()
            ).show();
        });

        // ── Save button ───────────────────────────────────────────────────────
        btnSave.setOnClickListener(v -> {
            if (subscription == null || TextUtils.isEmpty(subscription.getFirestoreId())) {
                dismiss();
                return;
            }

            String updatedName = etName.getText() != null
                    ? etName.getText().toString().trim() : "";
            String updatedFrequency = rgFrequency.getCheckedRadioButtonId() == R.id.rbCustomDays
                    ? "BI_WEEKLY" : "WEEKLY";
            List<GrocerySubscriptionItem> updatedItems = collectItemsFromEditor();
            if (updatedItems.isEmpty()) {
                CustomToast.showWarning(getContext(), "Add at least one item");
                return;
            }

            // Validate stock → then update
            validateItemsStock(updatedItems, () ->
                    performSubscriptionUpdate(updatedName, updatedFrequency, updatedItems, tvTime, tvDate));
        });

        return view;
    }

    // ── Product search ────────────────────────────────────────────────────────

    private void triggerProductSearch(boolean debounce, @NonNull LinearLayout itemsEditor) {
        if (etSearchProduct == null) return;
        String query = etSearchProduct.getText() != null
                ? etSearchProduct.getText().toString().trim() : "";
        if (query.isEmpty()) {
            activeSearchRequestId++;
            if (searchResultsContainer != null)
                showSearchMessage(searchResultsContainer, "Start typing to see live product suggestions");
            if (pendingSearchRunnable != null) {
                searchHandler.removeCallbacks(pendingSearchRunnable);
                pendingSearchRunnable = null;
            }
            return;
        }
        if (debounce) {
            if (pendingSearchRunnable != null) searchHandler.removeCallbacks(pendingSearchRunnable);
            pendingSearchRunnable = () -> performProductSearch(query, itemsEditor);
            searchHandler.postDelayed(pendingSearchRunnable, SEARCH_DEBOUNCE_MS);
            return;
        }
        if (pendingSearchRunnable != null) {
            searchHandler.removeCallbacks(pendingSearchRunnable);
            pendingSearchRunnable = null;
        }
        performProductSearch(query, itemsEditor);
    }

    private void performProductSearch(@NonNull String query, @NonNull LinearLayout itemsEditor) {
        if (searchResultsContainer == null) return;
        final long requestId = ++activeSearchRequestId;
        showSearchMessage(searchResultsContainer, "Searching products...");
        apiService.getCatalogProducts(query, null, null, null, null, null, null)
                .enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                        if (!isAdded() || searchResultsContainer == null || requestId != activeSearchRequestId) return;
                        List<JsonObject> rawResults = response.body();
                        if (!response.isSuccessful() || rawResults == null || rawResults.isEmpty()) {
                            showSearchMessage(searchResultsContainer, "No matching products found");
                            return;
                        }
                        List<SearchCatalogProduct> mappedResults = new ArrayList<>();
                        for (JsonObject raw : rawResults) {
                            SearchCatalogProduct mapped = mapSearchResult(raw);
                            if (mapped.productId == null) continue;
                            mappedResults.add(mapped);
                            if (mappedResults.size() >= MAX_SUGGESTIONS) break;
                        }
                        if (mappedResults.isEmpty()) {
                            showSearchMessage(searchResultsContainer, "No matching products found");
                            return;
                        }
                        bindSearchResults(mappedResults, requestId, itemsEditor);
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                        if (isAdded() && searchResultsContainer != null && requestId == activeSearchRequestId)
                            showSearchMessage(searchResultsContainer, "Unable to search products right now");
                    }
                });
    }

    private void bindSearchResults(@NonNull List<SearchCatalogProduct> results,
                                   long requestId,
                                   @NonNull LinearLayout itemsEditor) {
        if (searchResultsContainer == null) return;
        searchResultsContainer.removeAllViews();
        for (SearchCatalogProduct result : results) {
            SearchResultViewHolder holder = inflateSearchResultCard(result);
            searchResultsContainer.addView(holder.root);
            loadVariantsForResult(result, holder, requestId, itemsEditor);
        }
    }

    @NonNull
    private SearchResultViewHolder inflateSearchResultCard(@NonNull SearchCatalogProduct result) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_subscription_search_result, searchResultsContainer, false);
        SearchResultViewHolder holder = new SearchResultViewHolder(view);
        holder.name.setText(result.name);
        holder.meta.setText(result.subtitle);
        holder.price.setText(result.price > 0
                ? String.format(Locale.getDefault(), "Rs. %.2f", result.price)
                : "Price updates with selected variant");
        holder.stockNotice.setText("Loading variants...");
        Glide.with(this).load(result.imageUrl)
                .placeholder(R.drawable.logo).error(R.drawable.logo).into(holder.image);
        return holder;
    }

    private void loadVariantsForResult(@NonNull SearchCatalogProduct result,
                                       @NonNull SearchResultViewHolder holder,
                                       long requestId,
                                       @NonNull LinearLayout itemsEditor) {
        if (result.productId == null) {
            holder.stockNotice.setText("Product details unavailable");
            return;
        }
        apiService.getVariantsByProductId(result.productId)
                .enqueue(new Callback<List<com.hansanie.greencart.model.ProductVariant>>() {
                    @Override
                    public void onResponse(Call<List<com.hansanie.greencart.model.ProductVariant>> call,
                                           Response<List<com.hansanie.greencart.model.ProductVariant>> response) {
                        if (!isAdded() || requestId != activeSearchRequestId) return;
                        List<com.hansanie.greencart.model.ProductVariant> variants =
                                response.body() != null ? response.body() : new ArrayList<>();
                        result.variants.clear();
                        result.variants.addAll(variants);
                        bindVariantChips(result, holder, itemsEditor);
                    }

                    @Override
                    public void onFailure(Call<List<com.hansanie.greencart.model.ProductVariant>> call, Throwable t) {
                        if (!isAdded() || requestId != activeSearchRequestId) return;
                        holder.variantGroup.removeAllViews();
                        holder.stockNotice.setText("Unable to load variants right now");
                    }
                });
    }

    private void bindVariantChips(@NonNull SearchCatalogProduct result,
                                  @NonNull SearchResultViewHolder holder,
                                  @NonNull LinearLayout itemsEditor) {
        holder.variantGroup.removeAllViews();
        if (result.variants.isEmpty()) {
            holder.stockNotice.setText("No stock-safe variants available for quick add");
            return;
        }
        int inStockCount = 0, outOfStockCount = 0;
        for (com.hansanie.greencart.model.ProductVariant variant : result.variants) {
            if (variant != null && variant.isInStock()) inStockCount++; else outOfStockCount++;
        }
        if (inStockCount == 0)
            holder.stockNotice.setText("All variants are out of stock right now");
        else if (outOfStockCount > 0)
            holder.stockNotice.setText(String.format(Locale.getDefault(),
                    "%d variants available • %d out of stock", inStockCount, outOfStockCount));
        else
            holder.stockNotice.setText("Tap an in-stock variant to add it to the schedule");

        for (com.hansanie.greencart.model.ProductVariant variant : result.variants) {
            if (variant == null) continue;
            Chip chip = new Chip(requireContext());
            chip.setCheckable(false);
            chip.setChipBackgroundColorResource(R.color.chip_background_selector);
            chip.setEnsureMinTouchTargetSize(false);
            if (!variant.isInStock()) {
                chip.setText(buildVariantLabel(variant) + " • Out of stock");
                chip.setEnabled(false);
                chip.setAlpha(0.55f);
                chip.setOnClickListener(v ->
                        CustomToast.showWarning(getContext(), "This variant is currently out of stock"));
            } else {
                chip.setText(buildVariantLabel(variant));
                chip.setOnClickListener(v -> addSelectedProductToSchedule(result, variant, itemsEditor));
            }
            holder.variantGroup.addView(chip);
        }
    }

    private void addSelectedProductToSchedule(@NonNull SearchCatalogProduct result,
                                              @NonNull com.hansanie.greencart.model.ProductVariant variant,
                                              @NonNull LinearLayout itemsEditor) {
        if (!variant.isInStock()) {
            CustomToast.showWarning(getContext(), "Cannot add an out-of-stock variant");
            return;
        }
        // If already in list, increase quantity
        View existingRow = findExistingRow(result.productId, variant.getId());
        if (existingRow != null) {
            EditText etQty = existingRow.findViewById(R.id.etQty);
            int qty = 1;
            try {
                qty = Integer.parseInt(etQty.getText() != null
                        ? etQty.getText().toString().trim() : "1");
            } catch (NumberFormatException ignored) { qty = 1; }
            etQty.setText(String.valueOf(qty + 1));
            CustomToast.showInfo(getContext(), "Variant already selected — quantity increased");
            return;
        }
        SelectedItemMeta meta = new SelectedItemMeta();
        meta.productId  = result.productId;
        meta.variantId  = variant.getId();
        meta.unitPrice  = variant.getEffectivePrice();
        meta.imageUrl   = result.imageUrl;
        meta.productName = result.name;
        meta.variantName = variant.getVariantName();
        meta.inStock    = true;

        GrocerySubscriptionItem newItem = GrocerySubscriptionItem.builder()
                .productId(result.productId)
                .variantId(variant.getId())
                .name(result.name)
                .variantName(variant.getVariantName())
                .quantity(1)
                .unitPrice(variant.getEffectivePrice())
                .imageUrl(result.imageUrl)
                .build();
        addItemRow(itemsEditor, newItem, meta);
        CustomToast.showSuccess(getContext(), "Item added to schedule");
    }

    @Nullable
    private View findExistingRow(@Nullable Long productId, @Nullable Long variantId) {
        for (View row : itemRows) {
            Object tag = row.getTag();
            if (!(tag instanceof SelectedItemMeta)) continue;
            SelectedItemMeta meta = (SelectedItemMeta) tag;
            if (equalsLong(meta.productId, productId) && equalsLong(meta.variantId, variantId))
                return row;
        }
        return null;
    }

    private boolean equalsLong(@Nullable Long a, @Nullable Long b) {
        return a != null && b != null && a.equals(b);
    }

    // ── Items editor ──────────────────────────────────────────────────────────

    private void bindInitialItems(@NonNull LinearLayout itemsEditor) {
        itemRows.clear();
        itemsEditor.removeAllViews();
        if (subscription.getItems() != null && !subscription.getItems().isEmpty()) {
            for (GrocerySubscriptionItem item : subscription.getItems()) {
                SelectedItemMeta meta = new SelectedItemMeta();
                meta.productId   = item.getProductId();
                meta.variantId   = item.getVariantId();
                meta.unitPrice   = item.getUnitPrice();
                meta.imageUrl    = item.getImageUrl();
                meta.productName = item.getName();
                meta.variantName = item.getVariantName();
                meta.inStock     = true;
                addItemRow(itemsEditor, item, meta);
            }
            return;
        }
        addItemRow(itemsEditor, null, null);
    }

    private void addItemRow(@NonNull LinearLayout container,
                            @Nullable GrocerySubscriptionItem existing,
                            @Nullable SelectedItemMeta meta) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_subscription_edit_row, container, false);
        EditText etItemName    = row.findViewById(R.id.etItemName);
        EditText etVariantName = row.findViewById(R.id.etVariantName);
        EditText etQty         = row.findViewById(R.id.etQty);
        ImageButton btnRemove  = row.findViewById(R.id.btnRemoveItem);

        if (existing != null) {
            etItemName.setText(existing.getName() != null ? existing.getName() : "");
            etVariantName.setText(existing.getVariantName() != null ? existing.getVariantName() : "");
            etQty.setText(String.valueOf(
                    existing.getQuantity() != null && existing.getQuantity() > 0
                            ? existing.getQuantity() : 1));
        }

        btnRemove.setOnClickListener(v -> {
            if (itemRows.size() == 1) {
                CustomToast.showInfo(getContext(), "At least one item is required");
                return;
            }
            itemRows.remove(row);
            container.removeView(row);
        });

        if (meta != null) {
            row.setTag(meta);
        } else if (existing != null) {
            SelectedItemMeta m = new SelectedItemMeta();
            m.productId   = existing.getProductId();
            m.variantId   = existing.getVariantId();
            m.unitPrice   = existing.getUnitPrice();
            m.imageUrl    = existing.getImageUrl();
            m.productName = existing.getName();
            m.variantName = existing.getVariantName();
            m.inStock     = true;
            row.setTag(m);
        }
        itemRows.add(row);
        container.addView(row);
    }

    @NonNull
    private List<GrocerySubscriptionItem> collectItemsFromEditor() {
        List<GrocerySubscriptionItem> out = new ArrayList<>();
        for (View row : itemRows) {
            EditText etItemName    = row.findViewById(R.id.etItemName);
            EditText etVariantName = row.findViewById(R.id.etVariantName);
            EditText etQty         = row.findViewById(R.id.etQty);

            String name = etItemName.getText() != null
                    ? etItemName.getText().toString().trim() : "";
            if (name.isEmpty()) continue;

            int qty;
            try {
                qty = Integer.parseInt(etQty.getText() != null
                        ? etQty.getText().toString().trim() : "1");
            } catch (NumberFormatException ignored) { qty = 1; }

            SelectedItemMeta meta = row.getTag() instanceof SelectedItemMeta
                    ? (SelectedItemMeta) row.getTag() : null;

            out.add(GrocerySubscriptionItem.builder()
                    .productId(meta != null ? meta.productId : null)
                    .variantId(meta != null ? meta.variantId : null)
                    .name(name)
                    .variantName(etVariantName.getText() != null
                            ? etVariantName.getText().toString().trim() : "")
                    .quantity(Math.max(1, qty))
                    .unitPrice(meta != null ? meta.unitPrice : null)
                    .imageUrl(meta != null ? meta.imageUrl : null)
                    .build());
        }
        return out;
    }

    // ── Firestore + MySQL update ───────────────────────────────────────────────

    private void performSubscriptionUpdate(@NonNull String updatedName,
                                           @NonNull String updatedFrequency,
                                           @NonNull List<GrocerySubscriptionItem> updatedItems,
                                           @NonNull TextView tvTime,
                                           @NonNull TextView tvDate) {
        // ── Calculate totals ──────────────────────────────────────────────────
        double totalAmount = 0.0;
        for (GrocerySubscriptionItem it : updatedItems) {
            double price = it.getUnitPrice() != null ? it.getUnitPrice() : 0.0;
            int qty      = it.getQuantity()  != null ? it.getQuantity()  : 1;
            totalAmount += price * Math.max(1, qty);
        }
        final double finalTotalAmount = totalAmount;

        // ── Firestore update ──────────────────────────────────────────────────
        Map<String, Object> updates = new HashMap<>();
        updates.put("name",           TextUtils.isEmpty(updatedName) ? "Subscription" : updatedName);
        updates.put("frequency",      updatedFrequency);
        updates.put("deliveryDay",    selectedDeliveryDay);
        updates.put("deliveryTimeSlot", tvTime.getText().toString().trim());
        updates.put("next_delivery_date", tvDate.getText().toString().trim());
        updates.put("intervalDays",   "BI_WEEKLY".equals(updatedFrequency) ? 14 : 7);
        updates.put("itemCount",      updatedItems.size());
        updates.put("totalAmount",    finalTotalAmount);
        updates.put("discountAmount", finalTotalAmount * 0.05);
        updates.put("bonusPoints",    calculateSubscriptionBonusPoints(finalTotalAmount));
        updates.put("updatedAt",      LocalDateTime.now().format(DATE_TIME_FORMATTER));

        // Promo / points — preserve existing values
        updates.put("appliedPromoCode",  subscription.getAppliedPromoCode());
        updates.put("promoCodeDiscount", subscription.getPromoCodeDiscount()  != null ? subscription.getPromoCodeDiscount()  : 0.0);
        updates.put("pointsRedeemed",    subscription.getPointsRedeemed()     != null ? subscription.getPointsRedeemed()     : 0);
        updates.put("redeemValue",       subscription.getRedeemValue()        != null ? subscription.getRedeemValue()        : 0.0);

        // Items → Firestore-friendly maps
        List<Map<String, Object>> itemMaps = new ArrayList<>();
        for (GrocerySubscriptionItem it : updatedItems) {
            Map<String, Object> im = new HashMap<>();
            im.put("productId",  it.getProductId());
            im.put("variantId",  it.getVariantId());
            im.put("name",       it.getName());
            im.put("variantName", it.getVariantName());
            im.put("quantity",   it.getQuantity()  != null ? it.getQuantity()  : 1);
            im.put("unitPrice",  it.getUnitPrice() != null ? it.getUnitPrice() : 0.0);
            im.put("imageUrl",   it.getImageUrl());
            itemMaps.add(im);
        }
        updates.put("items", itemMaps);

        FirebaseFirestore.getInstance()
                .collection("grocery_subscriptions")
                .document(subscription.getFirestoreId())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    // Firestore OK → sync to MySQL
                    syncUpdatesToMySql(
                            TextUtils.isEmpty(updatedName) ? "Subscription" : updatedName,
                            updatedFrequency,
                            selectedDeliveryDay,
                            tvTime.getText().toString().trim(),
                            tvDate.getText().toString().trim(),
                            updatedItems,
                            finalTotalAmount
                    );
                    if (onUpdated != null) onUpdated.run();
                    CustomToast.showSuccess(getContext(), "Schedule updated");
                    dismiss();
                })
                .addOnFailureListener(e ->
                        CustomToast.showError(getContext(), "Unable to update schedule"));
    }

    // ── FIX: MySQL sync with actual items + totalAmount ───────────────────────

    private void syncUpdatesToMySql(@NonNull String updatedName,
                                    @NonNull String updatedFrequency,
                                    @NonNull String updatedDeliveryDay,
                                    @NonNull String updatedDeliveryTime,
                                    @NonNull String updatedNextDate,
                                    @NonNull List<GrocerySubscriptionItem> updatedItems,
                                    double totalAmount) {
        if (subscription.getId() == null) return;

        // GrocerySubscriptionItem → SubscriptionItemUpsertRequest
        List<SubscriptionItemUpsertRequest> requestItems = new ArrayList<>();
        for (GrocerySubscriptionItem item : updatedItems) {
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

        // ── FIX 1: items(requestItems) — actual items (was new ArrayList<>()) ─
        // ── FIX 2: totalAmount, discountAmount, bonusPoints correctly set ──────
        SubscriptionSaveRequest payload = SubscriptionSaveRequest.builder()
                .id(subscription.getId())
                .firebaseUid(subscription.getFirebaseUid())
                .name(updatedName)
                .frequency(updatedFrequency)
                .deliveryDay(updatedDeliveryDay)
                .deliveryTimeSlot(updatedDeliveryTime)
                .nextDeliveryDate(updatedNextDate)
                .status(subscription.getStatus())
                .firestoreId(subscription.getFirestoreId())
                .intervalDays("BI_WEEKLY".equals(updatedFrequency) ? 14 : 7)
                .itemCount(requestItems.size())
                .totalAmount(totalAmount)
                .discountAmount(totalAmount * 0.05)
                .bonusPoints(calculateSubscriptionBonusPoints(totalAmount))
                .items(requestItems)   // ← FIX: was new ArrayList<>()
                .build();

        apiService.saveGrocerySubscription(payload).enqueue(new Callback<GrocerySubscription>() {
            @Override
            public void onResponse(Call<GrocerySubscription> call, Response<GrocerySubscription> response) {
                if (!response.isSuccessful()) {
                    android.util.Log.e("SUB_SYNC", "Edit save failed: HTTP " + response.code());
                    return;
                }
                // ── FIX 3: updateSubscriptionItems only after save succeeds ───
                apiService.updateSubscriptionItems(subscription.getId(), requestItems)
                        .enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> call2, Response<Void> res2) {
                                if (!res2.isSuccessful())
                                    android.util.Log.e("SUB_SYNC",
                                            "Items update failed: HTTP " + res2.code());
                            }
                            @Override
                            public void onFailure(Call<Void> call2, Throwable t) {
                                android.util.Log.e("SUB_SYNC",
                                        "Items network error: " + t.getMessage());
                            }
                        });
            }

            @Override
            public void onFailure(Call<GrocerySubscription> call, Throwable t) {
                android.util.Log.e("SUB_SYNC", "Edit network error: " + t.getMessage());
            }
        });
    }

    // ── Stock validation ──────────────────────────────────────────────────────

    private void validateItemsStock(@NonNull List<GrocerySubscriptionItem> items,
                                    @NonNull Runnable onValid) {
        if (items.isEmpty()) { onValid.run(); return; }

        final java.util.concurrent.atomic.AtomicInteger pending =
                new java.util.concurrent.atomic.AtomicInteger(items.size());
        final List<String> errors = new ArrayList<>();

        for (GrocerySubscriptionItem it : items) {
            if (it == null || it.getProductId() == null) {
                if (pending.decrementAndGet() == 0)
                    finishValidation(errors, onValid);
                continue;
            }
            apiService.getVariantsByProductId(it.getProductId())
                    .enqueue(new Callback<List<com.hansanie.greencart.model.ProductVariant>>() {
                        @Override
                        public void onResponse(Call<List<com.hansanie.greencart.model.ProductVariant>> call,
                                               Response<List<com.hansanie.greencart.model.ProductVariant>> response) {
                            if (!response.isSuccessful()
                                    || response.body() == null
                                    || response.body().isEmpty()) {
                                synchronized (errors) {
                                    errors.add(itemLabel(it) + " stock check failed");
                                }
                                if (pending.decrementAndGet() == 0)
                                    finishValidation(errors, onValid);
                                return;
                            }
                            com.hansanie.greencart.model.ProductVariant match =
                                    findMatchingVariantForStock(it, response.body());
                            if (match == null) {
                                synchronized (errors) { errors.add(itemLabel(it) + " is unavailable"); }
                                if (pending.decrementAndGet() == 0)
                                    finishValidation(errors, onValid);
                                return;
                            }
                            Integer stock = match.getResolvedStockCount();
                            int available = stock != null ? Math.max(0, stock)
                                    : (match.isInStock() ? Integer.MAX_VALUE : 0);
                            int qty = it.getQuantity() != null ? it.getQuantity() : 1;
                            if (available <= 0) {
                                synchronized (errors) { errors.add(itemLabel(it) + " is out of stock"); }
                            } else if (available != Integer.MAX_VALUE && qty > available) {
                                synchronized (errors) { errors.add("Only " + available + " left for " + itemLabel(it)); }
                            }
                            if (pending.decrementAndGet() == 0)
                                finishValidation(errors, onValid);
                        }

                        @Override
                        public void onFailure(Call<List<com.hansanie.greencart.model.ProductVariant>> call,
                                              Throwable t) {
                            synchronized (errors) { errors.add(itemLabel(it) + " stock check failed"); }
                            if (pending.decrementAndGet() == 0)
                                finishValidation(errors, onValid);
                        }
                    });
        }
    }

    private void finishValidation(@NonNull List<String> errors, @NonNull Runnable onValid) {
        if (errors.isEmpty()) onValid.run();
        else CustomToast.showWarning(getContext(), errors.get(0));
    }

    @NonNull
    private String itemLabel(@Nullable GrocerySubscriptionItem it) {
        return (it != null && it.getName() != null) ? it.getName() : "Item";
    }

    @Nullable
    private com.hansanie.greencart.model.ProductVariant findMatchingVariantForStock(
            @NonNull GrocerySubscriptionItem item,
            @NonNull List<com.hansanie.greencart.model.ProductVariant> variants) {
        if (item.getVariantId() != null) {
            for (com.hansanie.greencart.model.ProductVariant v : variants)
                if (v != null && item.getVariantId().equals(v.getId())) return v;
        }
        if (item.getVariantName() != null && !item.getVariantName().trim().isEmpty()) {
            for (com.hansanie.greencart.model.ProductVariant v : variants) {
                if (v == null || v.getVariantName() == null) continue;
                if (item.getVariantName().trim().equalsIgnoreCase(v.getVariantName().trim())) return v;
            }
        }
        return variants.get(0);
    }

    // ── Day buttons ───────────────────────────────────────────────────────────

    private void bindDayButtons(@NonNull LinearLayout container) {
        container.removeAllViews();
        for (String day : DELIVERY_DAYS) {
            MaterialButton button = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(day.substring(0, 3));
            button.setCheckable(true);
            button.setChecked(day.equalsIgnoreCase(selectedDeliveryDay));
            button.setAllCaps(false);
            button.setCornerRadius(50);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(12);
            button.setLayoutParams(lp);
            button.setOnClickListener(v -> {
                selectedDeliveryDay = day;
                bindDayButtons(container);
            });
            container.addView(button);
        }
    }

    @NonNull
    private String resolveDeliveryDay(@Nullable String day) {
        if (day == null || day.trim().isEmpty())
            return toDayLabel(LocalDate.now().getDayOfWeek());
        for (String candidate : DELIVERY_DAYS)
            if (candidate.equalsIgnoreCase(day.trim())) return candidate;
        return "Monday";
    }

    @NonNull
    private String toDayLabel(@NonNull DayOfWeek day) {
        switch (day) {
            case MONDAY:    return "Monday";
            case TUESDAY:   return "Tuesday";
            case WEDNESDAY: return "Wednesday";
            case THURSDAY:  return "Thursday";
            case FRIDAY:    return "Friday";
            case SATURDAY:  return "Saturday";
            default:        return "Sunday";
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int calculateSubscriptionBonusPoints(double totalAmount) {
        return totalAmount > 1000.0 ? 5 : 0;
    }

    @NonNull
    private String buildVariantLabel(@NonNull com.hansanie.greencart.model.ProductVariant variant) {
        String name = variant.getVariantName() != null && !variant.getVariantName().trim().isEmpty()
                ? variant.getVariantName().trim() : "Variant";
        return String.format(Locale.getDefault(), "%s • Rs. %.2f", name, variant.getEffectivePrice());
    }

    private void showSearchMessage(@Nullable LinearLayout container, @NonNull String message) {
        if (container == null) return;
        container.removeAllViews();
        TextView tv = new TextView(requireContext());
        tv.setText(message);
        tv.setTextSize(14f);
        tv.setTextColor(requireContext().getColor(R.color.md_theme_onPrimaryContainer));
        tv.setPadding(0, dp(8), 0, dp(4));
        container.addView(tv);
    }

    private int dp(int value) {
        return Math.round(value * requireContext().getResources().getDisplayMetrics().density);
    }

    @NonNull
    private SearchCatalogProduct mapSearchResult(@NonNull JsonObject result) {
        SearchCatalogProduct mapped = new SearchCatalogProduct();
        mapped.productId = readLong(result, "id", "productId");
        mapped.name      = readString(result, "name", "title", "productName");
        mapped.imageUrl  = readStringOrNull(result, "primaryImage", "imageUrl", "image", "thumbnailUrl");
        mapped.price     = readDouble(result, "effectivePrice", "price", "unitPrice", "finalPrice");
        String category  = readStringOrNull(result, "category", "categoryName");
        String unit      = readStringOrNull(result, "unit");
        if (hasText(category) && hasText(unit))   mapped.subtitle = category + " • " + unit;
        else if (hasText(category))               mapped.subtitle = category;
        else if (hasText(unit))                   mapped.subtitle = unit;
        else                                      mapped.subtitle = "Choose an in-stock variant";
        return mapped;
    }

    // JSON helpers
    @NonNull
    private String readString(@NonNull JsonObject object, @NonNull String... keys) {
        String v = readStringOrNull(object, keys);
        return v != null ? v : "Product";
    }

    @Nullable
    private String readStringOrNull(@NonNull JsonObject object, @NonNull String... keys) {
        for (String key : keys) {
            com.google.gson.JsonElement el = object.get(key);
            if (el == null || el.isJsonNull()) continue;
            try { String s = el.getAsString().trim(); if (!s.isEmpty()) return s; }
            catch (Exception ignored) {}
        }
        return null;
    }

    @Nullable
    private Long readLong(@NonNull JsonObject object, @NonNull String... keys) {
        for (String key : keys) {
            com.google.gson.JsonElement el = object.get(key);
            if (el == null || el.isJsonNull()) continue;
            try { return el.getAsLong(); }
            catch (Exception ignored) {
                try { return Long.parseLong(el.getAsString().trim()); }
                catch (Exception ignored2) {
                    try { return Math.round(el.getAsDouble()); }
                    catch (Exception ignored3) {}
                }
            }
        }
        return null;
    }

    private double readDouble(@NonNull JsonObject object, @NonNull String... keys) {
        for (String key : keys) {
            com.google.gson.JsonElement el = object.get(key);
            if (el == null || el.isJsonNull()) continue;
            try { return el.getAsDouble(); }
            catch (Exception ignored) {
                try { return Double.parseDouble(el.getAsString().trim()); }
                catch (Exception ignored2) {}
            }
        }
        return 0.0;
    }

    private boolean hasText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    @Override
    public int getTheme() {
        return R.style.CustomBottomSheetDialogTheme;
    }

    // ── Inner classes ─────────────────────────────────────────────────────────

    private static class SelectedItemMeta {
        @Nullable Long productId;
        @Nullable Long variantId;
        @Nullable Double unitPrice;
        @Nullable String imageUrl;
        @Nullable String productName;
        @Nullable String variantName;
        boolean inStock;
    }

    private static class SearchCatalogProduct {
        @Nullable Long productId;
        @Nullable String imageUrl;
        @NonNull String name = "Product";
        @NonNull String subtitle = "Choose an in-stock variant";
        double price;
        @NonNull List<com.hansanie.greencart.model.ProductVariant> variants = new ArrayList<>();
    }

    private static class SearchResultViewHolder {
        final View root;
        final ImageView image;
        final TextView name;
        final TextView meta;
        final TextView price;
        final TextView stockNotice;
        final ChipGroup variantGroup;

        SearchResultViewHolder(@NonNull View root) {
            this.root      = root;
            image          = root.findViewById(R.id.imgProduct);
            name           = root.findViewById(R.id.tvProductName);
            meta           = root.findViewById(R.id.tvProductMeta);
            price          = root.findViewById(R.id.tvProductPrice);
            stockNotice    = root.findViewById(R.id.tvStockNotice);
            variantGroup   = root.findViewById(R.id.chipGroupVariants);
        }
    }
}
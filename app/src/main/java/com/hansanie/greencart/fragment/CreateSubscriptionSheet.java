    package com.hansanie.greencart.fragment;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hansanie.greencart.R;
import com.hansanie.greencart.dto.SubscriptionItemUpsertRequest;
import com.hansanie.greencart.dto.SubscriptionSaveRequest;
import com.hansanie.greencart.model.GrocerySubscription;
import com.hansanie.greencart.model.GrocerySubscriptionItem;
import com.hansanie.greencart.model.ProductVariant;
import com.hansanie.greencart.network.ApiService;
import com.hansanie.greencart.network.RetrofitClient;
import com.hansanie.greencart.util.CustomToast;
import com.hansanie.greencart.adapter.AddressAdapter;
import com.hansanie.greencart.model.Address;
import com.hansanie.greencart.fragment.ProfileFragment;

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

public class CreateSubscriptionSheet extends BottomSheetDialogFragment {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String DEFAULT_DELIVERY_TIME = "7:00 AM";
    private static final String[] DELIVERY_DAYS = new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private static final int VOICE_REQUEST_CODE = 4102;
    private static final long SEARCH_DEBOUNCE_MS = 300L;
    private static final int MAX_SUGGESTIONS = 6;

    @Nullable
    private final Runnable onCreated;
    private final List<View> itemRows = new ArrayList<>();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());

    private ApiService apiService;
    private FirebaseFirestore db;
    private String selectedDeliveryDay = "Monday";
    private long activeSearchRequestId = 0L;
    @Nullable
    private Runnable pendingSearchRunnable;

    private TextInputEditText etSearchProduct;
    private TextView tvTime;
    private TextView tvDate;
    private LinearLayout itemsEditor;
    private LinearLayout searchResultsContainer;

    // Address UI
    private TextView tvLocationTitle, tvLocationAddress, tvBillingAddress;
    private ImageButton btnChangeLocation, btnChangeBilling;
    private MaterialButton btnAddAnotherAddress, btnAddBillingAddress;
    private MaterialCheckBox cbDifferentBilling;
    private MaterialCardView cardBillingAddressView;
    private Address selectedDeliveryAddress, selectedBillingAddress;
    private List<Address> userAddressList = new ArrayList<>();

    public CreateSubscriptionSheet() {
        this(null);
    }

    public CreateSubscriptionSheet(@Nullable Runnable onCreated) {
        this.onCreated = onCreated;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_create_subscription, container, false);

        apiService = RetrofitClient.getApiService();
        db = FirebaseFirestore.getInstance();

        TextInputEditText etName = view.findViewById(R.id.etScheduleName);
        etSearchProduct = view.findViewById(R.id.etSearchProduct);
        tvTime = view.findViewById(R.id.tvSelectedTime);
        tvDate = view.findViewById(R.id.tvSelectedDate);
        RadioGroup rgFrequency = view.findViewById(R.id.rgFrequency);
        LinearLayout dayContainer = view.findViewById(R.id.layoutDeliveryDays);
        searchResultsContainer = view.findViewById(R.id.layoutSearchResults);
        itemsEditor = view.findViewById(R.id.layoutSubscriptionItemsEditor);
        MaterialButton btnSearchProduct = view.findViewById(R.id.btnSearchProduct);
        MaterialButton btnAddItem = view.findViewById(R.id.btnAddSubscriptionItem);
        MaterialButton btnSave = view.findViewById(R.id.btnSaveSchedule);
        ImageButton btnClose = view.findViewById(R.id.btnClose);
        ImageButton btnVoiceSearch = view.findViewById(R.id.btnVoiceSearchProduct);

        tvTime.setText(DEFAULT_DELIVERY_TIME);
        tvDate.setText(LocalDate.now().plusDays(1).format(DATE_FORMATTER));
        bindDayButtons(dayContainer);
        showSearchMessage(searchResultsContainer, "Start typing to see live product suggestions");

        btnClose.setOnClickListener(v -> dismiss());
        btnAddItem.setOnClickListener(v -> addItemRow(itemsEditor, null, null));
        btnSearchProduct.setOnClickListener(v -> triggerProductSearch(false));
        btnVoiceSearch.setOnClickListener(v -> startVoiceSearch());

        // Address UI bindings (IDs per provided layout snippet)
        // Use dynamic lookup to avoid layout-id static analysis mismatch in some build environments
        int idTvLocationTitle = view.getResources().getIdentifier("tvLocationTitle", "id", requireContext().getPackageName());
        if (idTvLocationTitle != 0) tvLocationTitle = view.findViewById(idTvLocationTitle);
        int idTvLocationAddress = view.getResources().getIdentifier("tvLocationAddress", "id", requireContext().getPackageName());
        if (idTvLocationAddress != 0) tvLocationAddress = view.findViewById(idTvLocationAddress);
        int idBtnChangeLocation = view.getResources().getIdentifier("btnChangeLocation", "id", requireContext().getPackageName());
        if (idBtnChangeLocation != 0) btnChangeLocation = view.findViewById(idBtnChangeLocation);
        int idBtnAddAnother = view.getResources().getIdentifier("btnAddAnotherAddress", "id", requireContext().getPackageName());
        if (idBtnAddAnother != 0) btnAddAnotherAddress = view.findViewById(idBtnAddAnother);
        int idCbDifferentBilling = view.getResources().getIdentifier("cbDifferentBilling", "id", requireContext().getPackageName());
        if (idCbDifferentBilling != 0) cbDifferentBilling = view.findViewById(idCbDifferentBilling);
        int idCardBilling = view.getResources().getIdentifier("cardBillingAddress", "id", requireContext().getPackageName());
        if (idCardBilling != 0) cardBillingAddressView = view.findViewById(idCardBilling);
        int idTvBilling = view.getResources().getIdentifier("tvBillingAddress", "id", requireContext().getPackageName());
        if (idTvBilling != 0) tvBillingAddress = view.findViewById(idTvBilling);
        int idBtnChangeBilling = view.getResources().getIdentifier("btnChangeBilling", "id", requireContext().getPackageName());
        if (idBtnChangeBilling != 0) btnChangeBilling = view.findViewById(idBtnChangeBilling);
        int idBtnAddBilling = view.getResources().getIdentifier("btnAddBillingAddress", "id", requireContext().getPackageName());
        if (idBtnAddBilling != 0) btnAddBillingAddress = view.findViewById(idBtnAddBilling);

        // Load user's default addresses to prefill the cards
        loadDefaultAddress();

        if (btnChangeLocation != null) btnChangeLocation.setOnClickListener(v -> showAddressBottomSheet(AddressSelectionTarget.DELIVERY));
        if (btnAddAnotherAddress != null) btnAddAnotherAddress.setOnClickListener(v -> openProfileAddresses());
        if (btnChangeBilling != null) btnChangeBilling.setOnClickListener(v -> showAddressBottomSheet(AddressSelectionTarget.BILLING));
        if (btnAddBillingAddress != null) btnAddBillingAddress.setOnClickListener(v -> openProfileAddresses());

        cbDifferentBilling.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (cardBillingAddressView != null) cardBillingAddressView.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (btnAddBillingAddress != null) btnAddBillingAddress.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            // If unchecked, mirror delivery address
            if (!isChecked) {
                selectedBillingAddress = selectedDeliveryAddress;
                if (tvBillingAddress != null && selectedDeliveryAddress != null) {
                    tvBillingAddress.setText(selectedDeliveryAddress.getFullAddress());
                }
            }
        });

        etSearchProduct.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                triggerProductSearch(true);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        etSearchProduct.setOnEditorActionListener((v, actionId, event) -> {
            triggerProductSearch(false);
            return true;
        });

        view.findViewById(R.id.btnChangeTime).setOnClickListener(v -> {
            TimePickerDialog dialog = new TimePickerDialog(requireContext(), (tp, hour, minute) -> {
                String ampm = hour >= 12 ? "PM" : "AM";
                int displayHour = hour % 12;
                if (displayHour == 0) {
                    displayHour = 12;
                }
                tvTime.setText(String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, ampm));
            }, 7, 0, false);
            dialog.show();
        });

        view.findViewById(R.id.btnChangeDate).setOnClickListener(v -> {
            LocalDate base;
            try {
                base = LocalDate.parse(String.valueOf(tvDate.getText()), DATE_FORMATTER);
            } catch (Exception ignored) {
                base = LocalDate.now().plusDays(1);
            }
            DatePickerDialog dialog = new DatePickerDialog(
                    requireContext(),
                    (picker, year, month, dayOfMonth) -> tvDate.setText(LocalDate.of(year, month + 1, dayOfMonth).format(DATE_FORMATTER)),
                    base.getYear(),
                    base.getMonthValue() - 1,
                    base.getDayOfMonth()
            );
            dialog.show();
        });

        btnSave.setOnClickListener(v -> createSchedule(etName, rgFrequency, tvTime, tvDate));
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        searchHandler.removeCallbacksAndMessages(null);
        pendingSearchRunnable = null;
        etSearchProduct = null;
        tvTime = null;
        tvDate = null;
        itemsEditor = null;
        searchResultsContainer = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != VOICE_REQUEST_CODE || resultCode != Activity.RESULT_OK || data == null || etSearchProduct == null) {
            return;
        }

        ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) {
            return;
        }

        String spokenText = results.get(0);
        etSearchProduct.setText(spokenText);
        etSearchProduct.setSelection(spokenText.length());
        triggerProductSearch(false);
    }

    private void startVoiceSearch() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say product name");
            startActivityForResult(intent, VOICE_REQUEST_CODE);
        } catch (Exception e) {
            CustomToast.showWarning(getContext(), "Voice search is not available on this device");
        }
    }

    private void triggerProductSearch(boolean debounce) {
        if (etSearchProduct == null) {
            return;
        }
        String query = etSearchProduct.getText() != null ? etSearchProduct.getText().toString().trim() : "";
        if (query.isEmpty()) {
            activeSearchRequestId++;
            searchHandler.removeCallbacksAndMessages(null);
            showSearchMessage(searchResultsContainer, "Start typing to see live product suggestions");
            return;
        }

        if (debounce) {
            if (pendingSearchRunnable != null) {
                searchHandler.removeCallbacks(pendingSearchRunnable);
            }
            pendingSearchRunnable = () -> performProductSearch(query);
            searchHandler.postDelayed(pendingSearchRunnable, SEARCH_DEBOUNCE_MS);
            return;
        }

        searchHandler.removeCallbacksAndMessages(null);
        pendingSearchRunnable = null;
        performProductSearch(query);
    }

    private void performProductSearch(@NonNull String query) {
        if (searchResultsContainer == null) {
            return;
        }

        final long requestId = ++activeSearchRequestId;
        showSearchMessage(searchResultsContainer, "Searching products...");
        apiService.getCatalogProducts(query, null, null, null, null, null, null)
                .enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                        if (!isAdded() || searchResultsContainer == null || requestId != activeSearchRequestId) {
                            return;
                        }
                        List<JsonObject> rawResults = response.body();
                        if (!response.isSuccessful() || rawResults == null || rawResults.isEmpty()) {
                            showSearchMessage(searchResultsContainer, "No matching products found");
                            return;
                        }

                        List<SearchCatalogProduct> mappedResults = new ArrayList<>();
                        for (JsonObject raw : rawResults) {
                            SearchCatalogProduct mapped = mapSearchResult(raw);
                            if (mapped.productId == null) {
                                continue;
                            }
                            mappedResults.add(mapped);
                            if (mappedResults.size() >= MAX_SUGGESTIONS) {
                                break;
                            }
                        }

                        if (mappedResults.isEmpty()) {
                            showSearchMessage(searchResultsContainer, "No matching products found");
                            return;
                        }
                        bindSearchResults(mappedResults, requestId);
                    }

                    @Override
                    public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                        if (isAdded() && searchResultsContainer != null && requestId == activeSearchRequestId) {
                            showSearchMessage(searchResultsContainer, "Unable to search products right now");
                        }
                    }
                });
    }

    private void bindSearchResults(@NonNull List<SearchCatalogProduct> results, long requestId) {
        if (searchResultsContainer == null) {
            return;
        }
        searchResultsContainer.removeAllViews();
        for (SearchCatalogProduct result : results) {
            SearchResultViewHolder holder = inflateSearchResultCard(result);
            searchResultsContainer.addView(holder.root);
            loadVariantsForResult(result, holder, requestId);
        }
    }

    @NonNull
    private SearchResultViewHolder inflateSearchResultCard(@NonNull SearchCatalogProduct result) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.item_subscription_search_result, searchResultsContainer, false);
        SearchResultViewHolder holder = new SearchResultViewHolder(view);
        holder.name.setText(result.name);
        holder.meta.setText(result.subtitle);
        holder.price.setText(result.price > 0
                ? String.format(Locale.getDefault(), "Rs. %.2f", result.price)
                : "Price updates with selected variant");
        holder.stockNotice.setText("Loading variants...");

        Glide.with(this)
                .load(result.imageUrl)
                .placeholder(R.drawable.logo)
                .error(R.drawable.logo)
                .into(holder.image);
        return holder;
    }

    private void loadVariantsForResult(@NonNull SearchCatalogProduct result,
                                       @NonNull SearchResultViewHolder holder,
                                       long requestId) {
        if (result.productId == null) {
            holder.stockNotice.setText("Product details unavailable");
            return;
        }

        apiService.getVariantsByProductId(result.productId).enqueue(new Callback<List<ProductVariant>>() {
            @Override
            public void onResponse(Call<List<ProductVariant>> call, Response<List<ProductVariant>> response) {
                if (!isAdded() || requestId != activeSearchRequestId) {
                    return;
                }
                List<ProductVariant> variants = response.body() != null ? response.body() : new ArrayList<>();
                result.variants.clear();
                result.variants.addAll(variants);
                bindVariantChips(result, holder);
            }

            @Override
            public void onFailure(Call<List<ProductVariant>> call, Throwable t) {
                if (!isAdded() || requestId != activeSearchRequestId) {
                    return;
                }
                holder.variantGroup.removeAllViews();
                holder.stockNotice.setText("Unable to load variants right now");
            }
        });
    }

    private void bindVariantChips(@NonNull SearchCatalogProduct result, @NonNull SearchResultViewHolder holder) {
        holder.variantGroup.removeAllViews();
        if (result.variants.isEmpty()) {
            holder.stockNotice.setText("No stock-safe variants available for quick add");
            return;
        }

        int inStockCount = 0;
        int outOfStockCount = 0;
        for (ProductVariant variant : result.variants) {
            if (variant != null && variant.isInStock()) {
                inStockCount++;
            } else {
                outOfStockCount++;
            }
        }

        if (inStockCount == 0) {
            holder.stockNotice.setText("All variants are out of stock right now");
        } else if (outOfStockCount > 0) {
            holder.stockNotice.setText(String.format(Locale.getDefault(), "%d variants available • %d out of stock", inStockCount, outOfStockCount));
        } else {
            holder.stockNotice.setText("Tap an in-stock variant to add it to the schedule");
        }

        for (ProductVariant variant : result.variants) {
            if (variant == null) {
                continue;
            }
            Chip chip = new Chip(requireContext());
            chip.setCheckable(false);
            chip.setChipBackgroundColorResource(R.color.chip_background_selector);
            chip.setText(buildVariantLabel(variant));
            chip.setEnsureMinTouchTargetSize(false);

            if (!variant.isInStock()) {
                chip.setEnabled(false);
                chip.setAlpha(0.55f);
                chip.setText(buildVariantLabel(variant) + " • Out of stock");
                chip.setOnClickListener(v -> CustomToast.showWarning(getContext(), "This variant is currently out of stock"));
            } else {
                chip.setOnClickListener(v -> addSelectedProductToSchedule(result, variant));
            }
            holder.variantGroup.addView(chip);
        }
    }

    @NonNull
    private String buildVariantLabel(@NonNull ProductVariant variant) {
        String variantName = variant.getVariantName() != null && !variant.getVariantName().trim().isEmpty()
                ? variant.getVariantName().trim()
                : "Variant";
        return String.format(Locale.getDefault(), "%s • Rs. %.2f", variantName, variant.getEffectivePrice());
    }

    private void addSelectedProductToSchedule(@NonNull SearchCatalogProduct result, @NonNull ProductVariant variant) {
        if (!variant.isInStock()) {
            CustomToast.showWarning(getContext(), "Cannot add an out-of-stock variant");
            return;
        }
        if (itemsEditor == null) {
            return;
        }

        View existingRow = findExistingRow(result.productId, variant.getId());
        if (existingRow != null) {
            EditText etQty = existingRow.findViewById(R.id.etQty);
            int quantity = 1;
            try {
                quantity = Integer.parseInt(etQty.getText() != null ? etQty.getText().toString().trim() : "1");
            } catch (NumberFormatException ignored) {
                quantity = 1;
            }
            etQty.setText(String.valueOf(quantity + 1));
            CustomToast.showInfo(getContext(), "Variant already selected — quantity increased");
            return;
        }

        SelectedItemMeta meta = new SelectedItemMeta();
        meta.productId = result.productId;
        meta.variantId = variant.getId();
        meta.unitPrice = variant.getEffectivePrice();
        meta.imageUrl = result.imageUrl;
        meta.productName = result.name;
        meta.variantName = variant.getVariantName();
        meta.inStock = true;

        GrocerySubscriptionItem existing = GrocerySubscriptionItem.builder()
                .productId(result.productId)
                .variantId(variant.getId())
                .name(result.name)
                .variantName(variant.getVariantName())
                .quantity(1)
                .unitPrice(variant.getEffectivePrice())
                .imageUrl(result.imageUrl)
                .build();
        addItemRow(itemsEditor, existing, meta);
        CustomToast.showSuccess(getContext(), "Item added to schedule");
    }

    @Nullable
    private View findExistingRow(@Nullable Long productId, @Nullable Long variantId) {
        for (View row : itemRows) {
            Object tag = row.getTag();
            if (!(tag instanceof SelectedItemMeta)) {
                continue;
            }
            SelectedItemMeta meta = (SelectedItemMeta) tag;
            if (equalsLong(meta.productId, productId) && equalsLong(meta.variantId, variantId)) {
                return row;
            }
        }
        return null;
    }

    private boolean equalsLong(@Nullable Long first, @Nullable Long second) {
        return first != null && second != null && first.equals(second);
    }

    @NonNull
    private SearchCatalogProduct mapSearchResult(@NonNull JsonObject result) {
        SearchCatalogProduct mapped = new SearchCatalogProduct();
        mapped.productId = readLong(result, "id", "productId");
        mapped.name = readString(result, "name", "title", "productName");
        mapped.imageUrl = readStringOrNull(result, "primaryImage", "imageUrl", "image", "thumbnailUrl");
        mapped.price = readDouble(result, "effectivePrice", "price", "unitPrice", "finalPrice");

        String category = readStringOrNull(result, "category", "categoryName");
        String unit = readStringOrNull(result, "unit");
        if (hasText(category) && hasText(unit)) {
            mapped.subtitle = category + " • " + unit;
        } else if (hasText(category)) {
            mapped.subtitle = category;
        } else if (hasText(unit)) {
            mapped.subtitle = unit;
        } else {
            mapped.subtitle = "Choose an in-stock variant";
        }
        return mapped;
    }

    private void showSearchMessage(@Nullable LinearLayout container, @NonNull String message) {
        if (container == null) {
            return;
        }
        container.removeAllViews();
        TextView textView = new TextView(requireContext());
        textView.setText(message);
        textView.setTextSize(14f);
        textView.setTextColor(requireContext().getColor(R.color.md_theme_onPrimaryContainer));
        textView.setPadding(0, dp(8), 0, dp(4));
        container.addView(textView);
    }

    private void addItemRow(@NonNull LinearLayout container,
                            @Nullable GrocerySubscriptionItem existing,
                            @Nullable SelectedItemMeta meta) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_subscription_edit_row, container, false);
        EditText etItemName = row.findViewById(R.id.etItemName);
        EditText etVariantName = row.findViewById(R.id.etVariantName);
        EditText etQty = row.findViewById(R.id.etQty);
        ImageButton btnRemove = row.findViewById(R.id.btnRemoveItem);

        if (existing != null) {
            etItemName.setText(existing.getName() != null ? existing.getName() : "");
            etVariantName.setText(existing.getVariantName() != null ? existing.getVariantName() : "");
            etQty.setText(String.valueOf(existing.getQuantity() != null && existing.getQuantity() > 0 ? existing.getQuantity() : 1));
        }

        row.setTag(meta);
        btnRemove.setOnClickListener(v -> {
            itemRows.remove(row);
            container.removeView(row);
        });

        itemRows.add(row);
        container.addView(row);
    }

    private void createSchedule(@NonNull TextInputEditText etName,
                                @NonNull RadioGroup rgFrequency,
                                @NonNull TextView timeView,
                                @NonNull TextView dateView) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            CustomToast.showWarning(getContext(), "Please sign in to create a subscription");
            return;
        }

        if (!validateSelectedItems()) {
            return;
        }

        // Validate stock counts for selected items before creating schedule
        List<GrocerySubscriptionItem> itemsToValidate = collectItemsFromEditor();
        if (itemsToValidate.isEmpty()) {
            CustomToast.showWarning(getContext(), "Add at least one item to create a schedule");
            return;
        }

        // Run async stock validation then continue with actual creation in postCreateSchedule
        validateItemsStock(itemsToValidate, () -> postCreateSchedule(etName, rgFrequency, timeView, dateView, itemsToValidate));
    }

    // Extracted schedule creation that runs after stock validation
    private void postCreateSchedule(@NonNull TextInputEditText etName,
                                    @NonNull RadioGroup rgFrequency,
                                    @NonNull TextView timeView,
                                    @NonNull TextView dateView,
                                    @NonNull List<GrocerySubscriptionItem> items) {
        String scheduleName = etName.getText() != null ? etName.getText().toString().trim() : "";
        String frequency = rgFrequency.getCheckedRadioButtonId() == R.id.rbBiWeekly ? "BI_WEEKLY" : "WEEKLY";
        int intervalDays = "BI_WEEKLY".equals(frequency) ? 14 : 7;
        LocalDate startDate;
        try {
            startDate = LocalDate.parse(String.valueOf(dateView.getText()), DATE_FORMATTER);
        } catch (Exception ignored) {
            startDate = LocalDate.now().plusDays(1);
        }
        LocalDate nextDeliveryDate = findNextDeliveryDate(startDate, selectedDeliveryDay);
        ArrayList<Long> productIds = new ArrayList<>();
        for (GrocerySubscriptionItem item : items) {
            if (item.getProductId() != null) {
                productIds.add(item.getProductId());
            }
        }

        double totalAmount = 0.0;
        for (GrocerySubscriptionItem item : items) {
            double unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : 0.0;
            int quantity = item.getQuantity() != null ? item.getQuantity() : 1;
            totalAmount += unitPrice * Math.max(1, quantity);
        }

        Map<String, Object> data = new HashMap<>();
        String uid = FirebaseAuth.getInstance().getUid();
        data.put("firebaseUid", uid);
        data.put("name", TextUtils.isEmpty(scheduleName) ? "My Subscription" : scheduleName);
        data.put("frequency", frequency);
        data.put("deliveryDay", selectedDeliveryDay);
        data.put("intervalDays", intervalDays);
        data.put("status", "ACTIVE");
        data.put("skip_next", false);
        data.put("start_date", startDate.format(DATE_FORMATTER));
        data.put("next_delivery_date", nextDeliveryDate.format(DATE_FORMATTER));
        data.put("deliveryTimeSlot", timeView.getText().toString().trim());
        data.put("totalAmount", totalAmount);
        // subscription discount: 5%
        double discountAmount = totalAmount * 0.05;
        data.put("discountAmount", discountAmount);
        // bonus points: rule: if total > 1000 give 5 points (as in checkout requirement)
        int bonusPoints = calculateSubscriptionBonusPoints(totalAmount);
        data.put("bonusPoints", bonusPoints);
        data.put("itemCount", items.size());
        data.put("productIds", productIds);
        // Convert items to Firestore-friendly maps to ensure fields like unitPrice and imageUrl are written
        List<Map<String, Object>> itemMaps = new ArrayList<>();
        for (GrocerySubscriptionItem it : items) {
            Map<String, Object> im = new HashMap<>();
            im.put("productId", it.getProductId());
            im.put("variantId", it.getVariantId());
            im.put("name", it.getName());
            im.put("variantName", it.getVariantName());
            im.put("quantity", it.getQuantity() != null ? it.getQuantity() : 1);
            im.put("unitPrice", it.getUnitPrice() != null ? it.getUnitPrice() : 0.0);
            im.put("imageUrl", it.getImageUrl());
            itemMaps.add(im);
        }
        data.put("items", itemMaps);
        // Attach chosen delivery/billing address ids (prefer server id if available)
        String delivAddrId = null;
        if (selectedDeliveryAddress != null) {
            delivAddrId = selectedDeliveryAddress.getId() != null ? selectedDeliveryAddress.getId() : selectedDeliveryAddress.getFirestoreDocId();
        }
        String billAddrId = null;
        if (cbDifferentBilling != null && cbDifferentBilling.isChecked()) {
            if (selectedBillingAddress != null) {
                billAddrId = selectedBillingAddress.getId() != null ? selectedBillingAddress.getId() : selectedBillingAddress.getFirestoreDocId();
            }
        } else {
            billAddrId = delivAddrId;
        }
        data.put("deliveryAddressId", delivAddrId);
        data.put("billingAddressId", billAddrId);
        // Promo / points defaults (no promo/points selected when creating from this sheet)
        data.put("appliedPromoCode", null);
        data.put("promoCodeDiscount", 0.0);
        data.put("pointsRedeemed", 0);
        data.put("redeemValue", 0.0);
        data.put("createdAt", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        data.put("updatedAt", LocalDateTime.now().format(DATE_TIME_FORMATTER));

        db.collection("grocery_subscriptions")
                .add(data)
                .addOnSuccessListener(ref -> {
                    syncNewSubscriptionToMySql(uid, ref.getId(), data, items);
                    if (onCreated != null) {
                        onCreated.run();
                    }
                    CustomToast.showSuccess(getContext(), "Schedule created");
                    dismiss();
                })
                .addOnFailureListener(e -> CustomToast.showError(getContext(), "Unable to create schedule"));
    }

    private boolean validateSelectedItems() {
        for (View row : itemRows) {
            Object tag = row.getTag();
            if (!(tag instanceof SelectedItemMeta)) {
                continue;
            }
            SelectedItemMeta meta = (SelectedItemMeta) tag;
            if (meta.productId != null && (meta.variantId == null || !meta.inStock)) {
                String label = hasText(meta.productName) ? meta.productName : "One of your selected items";
                CustomToast.showWarning(getContext(), label + " has an unavailable variant. Choose an in-stock variant.");
                return false;
            }
        }
        return true;
    }

    // Validate stock for a list of subscription items asynchronously.
    private void validateItemsStock(@NonNull List<GrocerySubscriptionItem> items, @NonNull Runnable onValid) {
        if (items.isEmpty()) {
            onValid.run();
            return;
        }
        final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(items.size());
        final java.util.List<String> errors = new java.util.ArrayList<>();

        for (GrocerySubscriptionItem it : items) {
            if (it == null || it.getProductId() == null) {
                if (pending.decrementAndGet() == 0) {
                    if (errors.isEmpty()) onValid.run(); else CustomToast.showWarning(getContext(), errors.get(0));
                }
                continue;
            }
            apiService.getVariantsByProductId(it.getProductId()).enqueue(new retrofit2.Callback<List<com.hansanie.greencart.model.ProductVariant>>() {
                @Override
                public void onResponse(retrofit2.Call<List<com.hansanie.greencart.model.ProductVariant>> call, retrofit2.Response<List<com.hansanie.greencart.model.ProductVariant>> response) {
                    if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                        synchronized (errors) { errors.add((it.getName() != null ? it.getName() : "Item") + " stock check failed"); }
                        if (pending.decrementAndGet() == 0) {
                            if (errors.isEmpty()) onValid.run(); else CustomToast.showWarning(getContext(), errors.get(0));
                        }
                        return;
                    }
                    com.hansanie.greencart.model.ProductVariant match = findMatchingVariantForStock(it, response.body());
                    if (match == null) {
                        synchronized (errors) { errors.add((it.getName() != null ? it.getName() : "Item") + " is unavailable"); }
                        if (pending.decrementAndGet() == 0) {
                            if (errors.isEmpty()) onValid.run(); else CustomToast.showWarning(getContext(), errors.get(0));
                        }
                        return;
                    }
                    Integer stock = match.getResolvedStockCount();
                    int available = stock != null ? Math.max(0, stock) : (match.isInStock() ? Integer.MAX_VALUE : 0);
                    int qty = it.getQuantity() != null ? it.getQuantity() : 1;
                    if (available <= 0) {
                        synchronized (errors) { errors.add((it.getName() != null ? it.getName() : "Item") + " is out of stock"); }
                    } else if (available != Integer.MAX_VALUE && qty > available) {
                        synchronized (errors) { errors.add("Only " + available + " left for " + (it.getName() != null ? it.getName() : "Item")); }
                    }
                    if (pending.decrementAndGet() == 0) {
                        if (errors.isEmpty()) onValid.run(); else CustomToast.showWarning(getContext(), errors.get(0));
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<List<com.hansanie.greencart.model.ProductVariant>> call, Throwable t) {
                    synchronized (errors) { errors.add((it.getName() != null ? it.getName() : "Item") + " stock check failed"); }
                    if (pending.decrementAndGet() == 0) {
                        if (errors.isEmpty()) onValid.run(); else CustomToast.showWarning(getContext(), errors.get(0));
                    }
                }
            });
        }
    }

    @Nullable
    private com.hansanie.greencart.model.ProductVariant findMatchingVariantForStock(@NonNull GrocerySubscriptionItem item,
                                                                                     @NonNull List<com.hansanie.greencart.model.ProductVariant> variants) {
        if (item.getVariantId() != null) {
            for (com.hansanie.greencart.model.ProductVariant variant : variants) {
                if (variant != null && item.getVariantId().equals(variant.getId())) return variant;
            }
        }
        if (item.getVariantName() != null && !item.getVariantName().trim().isEmpty()) {
            for (com.hansanie.greencart.model.ProductVariant variant : variants) {
                if (variant == null || variant.getVariantName() == null) continue;
                if (item.getVariantName().trim().equalsIgnoreCase(variant.getVariantName().trim())) return variant;
            }
        }
        return variants.get(0);
    }

    private int calculateSubscriptionBonusPoints(double totalAmount) {
        // As requested: if order total exceeds 1000 give 5 points; otherwise 0.
        return totalAmount > 1000.0 ? 5 : 0;
    }

    @NonNull
    private List<GrocerySubscriptionItem> collectItemsFromEditor() {
        List<GrocerySubscriptionItem> out = new ArrayList<>();
        for (View row : itemRows) {
            EditText etItemName = row.findViewById(R.id.etItemName);
            EditText etVariantName = row.findViewById(R.id.etVariantName);
            EditText etQty = row.findViewById(R.id.etQty);

            String name = etItemName.getText() != null ? etItemName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                continue;
            }

            int qty;
            try {
                qty = Integer.parseInt(etQty.getText() != null ? etQty.getText().toString().trim() : "1");
            } catch (NumberFormatException ignored) {
                qty = 1;
            }

            SelectedItemMeta meta = row.getTag() instanceof SelectedItemMeta ? (SelectedItemMeta) row.getTag() : null;
            GrocerySubscriptionItem item = GrocerySubscriptionItem.builder()
                    .productId(meta != null ? meta.productId : null)
                    .variantId(meta != null ? meta.variantId : null)
                    .name(name)
                    .variantName(etVariantName.getText() != null ? etVariantName.getText().toString().trim() : "")
                    .quantity(Math.max(1, qty))
                    .unitPrice(meta != null ? meta.unitPrice : null)
                    .imageUrl(meta != null ? meta.imageUrl : null)
                    .build();
            out.add(item);
        }
        return out;
    }

    private void bindDayButtons(@NonNull LinearLayout container) {
        container.removeAllViews();
        for (String day : DELIVERY_DAYS) {
            MaterialButton button = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(day.substring(0, 3));
            button.setCheckable(true);
            button.setChecked(day.equalsIgnoreCase(selectedDeliveryDay));
            button.setAllCaps(false);
            button.setCornerRadius(dp(50));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMarginEnd(dp(12));
            button.setLayoutParams(lp);
            button.setOnClickListener(v -> {
                selectedDeliveryDay = day;
                bindDayButtons(container);
            });
            container.addView(button);
        }
    }

    @NonNull
    private LocalDate findNextDeliveryDate(@NonNull LocalDate base, @NonNull String targetDay) {
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
        return next;
    }

    private void syncNewSubscriptionToMySql(@NonNull String uid,
                                            @NonNull String firestoreId,
                                            @NonNull Map<String, Object> data,
                                            @NonNull List<GrocerySubscriptionItem> items) {

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
                .intervalDays(asInt(data.get("intervalDays"), 7))
                .deliveryTimeSlot(readString(data.get("deliveryTimeSlot")))
                .deliveryAddressId(readString(data.get("deliveryAddressId")))
                .billingAddressId(readString(data.get("billingAddressId")))
                .totalAmount(asDouble(data.get("totalAmount"), 0.0))
                .discountAmount(asDouble(data.get("discountAmount"), 0.0))
                .itemCount(requestItems.size())
                .bonusPoints(asInt(data.get("bonusPoints"), 0))
                .firestoreId(firestoreId)
                .skipNextDelivery(false)
                .items(new ArrayList<>()) // ← FIX: empty list, items separately update වෙනවා
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

                Long subscriptionId = response.body().getId();
                Map<String, Object> syncPatch = new HashMap<>();
                syncPatch.put("id", subscriptionId);
                syncPatch.put("mysqlSyncStatus", "SYNCED");
                db.collection("grocery_subscriptions")
                        .document(firestoreId)
                        .set(syncPatch, SetOptions.merge());

                apiService.updateSubscriptionItems(subscriptionId, requestItems)
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

    // ---------------- Address helpers adapted from CheckoutFragment (minimal, local) ----------------
    private enum AddressSelectionTarget { DELIVERY, BILLING }

    private void loadDefaultAddress() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        apiService.getUserAddresses(uid).enqueue(new Callback<List<com.hansanie.greencart.model.Address>>() {
            @Override
            public void onResponse(Call<List<com.hansanie.greencart.model.Address>> call, Response<List<com.hansanie.greencart.model.Address>> response) {
                if (!isAdded() || !response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    return;
                }
                userAddressList.clear();
                userAddressList.addAll(response.body());

                com.hansanie.greencart.model.Address selected = response.body().get(0);
                for (com.hansanie.greencart.model.Address a : response.body()) {
                    if (a.isDefault()) { selected = a; break; }
                }
                selectedDeliveryAddress = selected;
                if (tvLocationTitle != null) tvLocationTitle.setText(selected.getTitle() != null ? selected.getTitle() : "Address");
                if (tvLocationAddress != null) tvLocationAddress.setText(selected.getFullAddress());

                if (cbDifferentBilling == null || !cbDifferentBilling.isChecked()) {
                    selectedBillingAddress = selected;
                    if (tvBillingAddress != null) tvBillingAddress.setText(selected.getFullAddress());
                }
            }

            @Override
            public void onFailure(Call<List<com.hansanie.greencart.model.Address>> call, Throwable t) {
                // ignore — leave placeholders
            }
        });
    }

    private void openProfileAddresses() {
        // Open ProfileFragment where user can add addresses
        try {
            if (!isAdded()) return;
            ProfileFragment pf = new ProfileFragment();
            // Use the activity's fragment manager and ensure the container exists. If the
            // activity layout doesn't have R.id.fragmentContainer (e.g., dialog or other
            // host), fall back to replacing the activity's content view to avoid
            // IllegalArgumentException: No view found for id ...
            androidx.fragment.app.FragmentManager fm = requireActivity().getSupportFragmentManager();
            android.view.View container = requireActivity().findViewById(R.id.fragmentContainer);
            if (container != null) {
                fm.beginTransaction()
                        .replace(R.id.fragmentContainer, pf)
                        .addToBackStack(null)
                        .commitAllowingStateLoss();
            } else {
                // fallback to placing fragment in the activity's root content
                fm.beginTransaction()
                        .replace(android.R.id.content, pf)
                        .addToBackStack(null)
                        .commitAllowingStateLoss();
            }
            dismiss();
        } catch (Exception ignored) {}
    }

    private void showAddressBottomSheet(@NonNull AddressSelectionTarget target) {
        if (!isAdded()) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_addresses, null);
        dialog.setContentView(sheet);

        RecyclerView rvAddresses = sheet.findViewById(R.id.rvAddresses);
        LinearLayout layoutForm = sheet.findViewById(R.id.layoutAddressForm);
        TextView sheetTitle = sheet.findViewById(R.id.sheetTitle);
        MaterialButton btnAddNew = sheet.findViewById(R.id.btnAddNewAddress);
        MaterialButton btnCancel = sheet.findViewById(R.id.btnCancelAddress);

        rvAddresses.setLayoutManager(new LinearLayoutManager(requireContext()));
        AddressAdapter adapter = new AddressAdapter(userAddressList, new AddressAdapter.OnAddressActionListener() {
            @Override
            public void onEdit(com.hansanie.greencart.model.Address address) {
                // Open profile addresses for editing/adding (keeps changes minimal)
                openProfileAddresses();
                dialog.dismiss();
            }

            @Override
            public void onDelete(com.hansanie.greencart.model.Address address) {
                // No-op for now
            }
        });

        adapter.setOnAddressSelectListener(address -> {
            // Apply selected address
            if (target == AddressSelectionTarget.DELIVERY) {
                selectedDeliveryAddress = address;
                if (tvLocationTitle != null) tvLocationTitle.setText(address.getTitle() != null ? address.getTitle() : "Address");
                if (tvLocationAddress != null) tvLocationAddress.setText(address.getFullAddress());
                if (cbDifferentBilling == null || !cbDifferentBilling.isChecked()) {
                    selectedBillingAddress = address;
                    if (tvBillingAddress != null) tvBillingAddress.setText(address.getFullAddress());
                }
            } else {
                selectedBillingAddress = address;
                if (tvBillingAddress != null) tvBillingAddress.setText(address.getFullAddress());
            }
            dialog.dismiss();
        });

        rvAddresses.setAdapter(adapter);

        btnAddNew.setOnClickListener(v -> {
            // Open ProfileFragment to add new address
            openProfileAddresses();
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
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
        } catch (Exception ignored) {
            return "HTTP " + response.code();
        }
    }

    @Nullable
    private String readString(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    @NonNull
    private String readString(@NonNull JsonObject object, @NonNull String... keys) {
        String value = readStringOrNull(object, keys);
        return value != null ? value : "Product";
    }

    @Nullable
    private String readStringOrNull(@NonNull JsonObject object, @NonNull String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            try {
                String value = element.getAsString().trim();
                if (!value.isEmpty()) {
                    return value;
                }
            } catch (Exception ignored) {
                // Ignore malformed values.
            }
        }
        return null;
    }

    private long readDoubleAsLong(@NonNull JsonElement element) {
        try {
            return Math.round(element.getAsDouble());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    @Nullable
    private Long readLong(@NonNull JsonObject object, @NonNull String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            try {
                return element.getAsLong();
            } catch (Exception ignored) {
                try {
                    return Long.parseLong(element.getAsString().trim());
                } catch (Exception ignoredAgain) {
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                        return readDoubleAsLong(element);
                    }
                }
            }
        }
        return null;
    }

    private double readDouble(@NonNull JsonObject object, @NonNull String... keys) {
        for (String key : keys) {
            JsonElement element = object.get(key);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            try {
                return element.getAsDouble();
            } catch (Exception ignored) {
                try {
                    return Double.parseDouble(element.getAsString().trim());
                } catch (Exception ignoredAgain) {
                    // Ignore and keep checking.
                }
            }
        }
        return 0.0;
    }

    private boolean hasText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    private double asDouble(@Nullable Object value, double fallback) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
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

    private int dp(int value) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    @Override
    public int getTheme() {
        return R.style.CustomBottomSheetDialogTheme;
    }

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
        @NonNull List<ProductVariant> variants = new ArrayList<>();
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
            this.root = root;
            image = root.findViewById(R.id.imgProduct);
            name = root.findViewById(R.id.tvProductName);
            meta = root.findViewById(R.id.tvProductMeta);
            price = root.findViewById(R.id.tvProductPrice);
            stockNotice = root.findViewById(R.id.tvStockNotice);
            variantGroup = root.findViewById(R.id.chipGroupVariants);
        }
    }
}

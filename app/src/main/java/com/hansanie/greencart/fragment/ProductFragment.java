package com.hansanie.greencart.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.RangeSlider;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hansanie.greencart.R;
import com.hansanie.greencart.adapter.ProductAdapter;
import com.hansanie.greencart.model.Category;
import com.hansanie.greencart.model.Product;
import com.hansanie.greencart.model.ProductStockSummary;
import com.hansanie.greencart.model.ProductVariant;
import com.hansanie.greencart.network.ApiService;
import com.hansanie.greencart.network.RetrofitClient;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProductFragment extends Fragment {

    private static final String TAG = "ProductFragment";
    private static final String ARG_CATEGORY_ID = "arg_category_id";
    private static final String ARG_CATEGORY_NAME = "arg_category_name";
    private static final String ARG_DEALS_ONLY = "arg_deals_only";
    private static final String ARG_INITIAL_QUERY = "arg_initial_query";
    private static final int VOICE_REQUEST_CODE = 202;
    private static final float DEFAULT_PRICE_MIN = 0f;
    private static final float DEFAULT_PRICE_MAX = 5000f;
    private static final long SEARCH_DEBOUNCE_MS = 350L;

    private RecyclerView rvAllProducts;
    private ProductAdapter productAdapter;
    private final List<Product> productList = new ArrayList<>();
    private final List<Category> categories = new ArrayList<>();

    private ApiService apiService;
    private EditText searchEditText;
    private ImageButton btnOpenFilterSheet;
    private ImageView btnQRSearch, btnVoiceSearch;
    private ChipGroup chipGroupQuickFilters;

    private boolean suppressQuickChipCallback;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    // Filter state
    private String activeSortKey = null;
    private String activeCategoryName = "All";
    private Long activeCategoryId = null;
    private float activePriceMin = DEFAULT_PRICE_MIN;
    private float activePriceMax = DEFAULT_PRICE_MAX;
    private boolean activeInStockOnly = false;
    private boolean activeDealsOnly = false;

    // QR Scanner launcher
    private final ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null && searchEditText != null) {
                    handleScannedCode(result.getContents());
                }
            });

    public ProductFragment() {}

    @NonNull
    public static ProductFragment newInstance(@Nullable Long categoryId,
                                              @Nullable String categoryName,
                                              boolean dealsOnly,
                                              @Nullable String initialQuery) {
        ProductFragment fragment = new ProductFragment();
        Bundle args = new Bundle();
        if (categoryId != null) args.putLong(ARG_CATEGORY_ID, categoryId);
        if (categoryName != null) args.putString(ARG_CATEGORY_NAME, categoryName);
        args.putBoolean(ARG_DEALS_ONLY, dealsOnly);
        if (initialQuery != null) args.putString(ARG_INITIAL_QUERY, initialQuery);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_product, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvAllProducts         = view.findViewById(R.id.rvAllProducts);
        searchEditText        = view.findViewById(R.id.searchEditText);
        btnOpenFilterSheet    = view.findViewById(R.id.btnOpenFilterSheet);
        btnQRSearch           = view.findViewById(R.id.btnQRSearch);
        btnVoiceSearch        = view.findViewById(R.id.btnVoiceSearch);
        chipGroupQuickFilters = view.findViewById(R.id.chipGroupQuickFilters);

        rvAllProducts.setLayoutManager(new GridLayoutManager(getContext(), getGridSpanCount()));
        productAdapter = new ProductAdapter(productList, false);
        rvAllProducts.setAdapter(productAdapter);

        apiService = RetrofitClient.getApiService();

        productAdapter.setOnItemClickListener(product -> {
            ProductDetailsFragment details =
                    ProductDetailsFragment.newInstance(String.valueOf(product.getId()));
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, details)
                    .addToBackStack(null)
                    .commit();
        });

        applyFragmentArguments();

        if (searchEditText != null) {
            String initialQuery = getInitialQueryArgument();
            if (initialQuery != null && !initialQuery.trim().isEmpty()) {
                searchEditText.setText(initialQuery.trim());
            }
        }

        loadCategoriesFromBackend();
        syncQuickFilterChips();
        loadProductsFromBackend(getCurrentSearchQuery());

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyAllFilters(s == null ? "" : s.toString(), true);
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        if (btnQRSearch != null) {
            btnQRSearch.setOnClickListener(v -> {
                ScanOptions options = new ScanOptions();
                options.setPrompt("Scan product QR");
                options.setBeepEnabled(true);
                barcodeLauncher.launch(options);
            });
        }

        if (btnVoiceSearch != null) {
            btnVoiceSearch.setOnClickListener(v -> {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say product name");
                startActivityForResult(intent, VOICE_REQUEST_CODE);
            });
        }

        if (btnOpenFilterSheet != null) {
            btnOpenFilterSheet.setOnClickListener(v -> showFilterBottomSheet());
        }

        if (chipGroupQuickFilters != null) {
            chipGroupQuickFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (suppressQuickChipCallback) return;

                if (checkedIds == null || checkedIds.isEmpty()) {
                    setQuickFilterState(null, false);
                    return;
                }

                int selectedId = checkedIds.get(0);
                if      (selectedId == R.id.chipAll)          setQuickFilterState(null, false);
                else if (selectedId == R.id.chipDeals)        setQuickFilterState(null, true);
                else if (selectedId == R.id.chipPopular)      setQuickFilterState("popular", false);
                else if (selectedId == R.id.chipLowestPrice)  setQuickFilterState("lowprice", false);
                else if (selectedId == R.id.chipHighestPrice) setQuickFilterState("price_desc", false);
                else if (selectedId == R.id.chipNewest)       setQuickFilterState("newest", false);
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        searchHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST_CODE
                && resultCode == requireActivity().RESULT_OK
                && data != null) {
            ArrayList<String> results =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty() && searchEditText != null) {
                String spoken = results.get(0);
                searchEditText.setText(spoken);
                applyAllFilters(spoken, false);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filter / load
    // ─────────────────────────────────────────────────────────────────────────

    private void applyAllFilters(String searchText, boolean debounce) {
        if (debounce) {
            scheduleCatalogReload(searchText);
        } else {
            searchHandler.removeCallbacksAndMessages(null);
            loadProductsFromBackend(searchText);
        }
    }

    private void scheduleCatalogReload(String searchText) {
        searchHandler.removeCallbacksAndMessages(null);
        searchRunnable = () -> loadProductsFromBackend(searchText);
        searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // QR / Voice helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void handleScannedCode(@NonNull String scannedValue) {
        String normalized = scannedValue.trim();
        if (searchEditText != null) searchEditText.setText(normalized);
        Long scannedProductId = parseLongSafely(normalized);
        if (scannedProductId == null) {
            applyAllFilters(normalized, false);
            return;
        }
        loadSingleProductById(scannedProductId, normalized);
    }

    @Nullable
    private Long parseLongSafely(@Nullable String value) {
        if (value == null) return null;
        try { return Long.parseLong(value.trim()); }
        catch (NumberFormatException ex) { return null; }
    }

    private void loadSingleProductById(long productId, @NonNull String fallbackQuery) {
        apiService.getProductDetails(productId).enqueue(new Callback<Product>() {
            @Override
            public void onResponse(@NonNull Call<Product> call, @NonNull Response<Product> response) {
                if (!isAdded()) return;
                Product product = response.body();
                if (!response.isSuccessful() || product == null) {
                    applyAllFilters(fallbackQuery, false);
                    return;
                }
                if (product.getId() == null) product.setId(productId);
                if (product.getCategoryObject() == null && product.getCategory() != null) {
                    Category cat = new Category();
                    cat.setName(product.getCategory());
                    product.setCategoryObject(cat);
                }
                apiService.getVariantsByProductId(productId).enqueue(new Callback<List<ProductVariant>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ProductVariant>> call,
                                           @NonNull Response<List<ProductVariant>> variantsResponse) {
                        if (!isAdded()) return;
                        if (variantsResponse.isSuccessful() && variantsResponse.body() != null
                                && !variantsResponse.body().isEmpty()) {
                            product.setVariants(variantsResponse.body());
                            product.setDealLabel(hasDeals(variantsResponse.body()));
                        }
                        updateNewestLabelFromStock(product, () -> publishSingleScannedProduct(product));
                    }
                    @Override
                    public void onFailure(@NonNull Call<List<ProductVariant>> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        updateNewestLabelFromStock(product, () -> publishSingleScannedProduct(product));
                    }
                });
            }
            @Override
            public void onFailure(@NonNull Call<Product> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Log.e(TAG, "QR product lookup failed: " + t.getMessage(), t);
                applyAllFilters(fallbackQuery, false);
            }
        });
    }

    private void publishSingleScannedProduct(@NonNull Product product) {
        productList.clear();
        productList.add(product);
        productAdapter.notifyDataSetChanged();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Categories
    // ─────────────────────────────────────────────────────────────────────────

    private void loadCategoriesFromBackend() {
        apiService.getCategories().enqueue(new Callback<List<Category>>() {
            @Override
            public void onResponse(@NonNull Call<List<Category>> call, @NonNull Response<List<Category>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    categories.clear();
                    categories.addAll(response.body());
                    resolveCategoryIdFromLoadedCategories();
                } else {
                    Log.e(TAG, "Failed to load categories: " + response.code());
                }
            }
            @Override
            public void onFailure(@NonNull Call<List<Category>> call, @NonNull Throwable t) {
                Log.e(TAG, "Category fetch failed: " + t.getMessage(), t);
            }
        });
    }

    private void applyFragmentArguments() {
        Bundle args = getArguments();
        if (args == null) return;
        if (args.containsKey(ARG_CATEGORY_ID)) activeCategoryId = args.getLong(ARG_CATEGORY_ID);
        String categoryName = args.getString(ARG_CATEGORY_NAME);
        if (categoryName != null && !categoryName.trim().isEmpty()) activeCategoryName = categoryName.trim();
        activeDealsOnly = args.getBoolean(ARG_DEALS_ONLY, false);
        if (activeDealsOnly) activeSortKey = null;
    }

    @Nullable
    private String getInitialQueryArgument() {
        Bundle args = getArguments();
        return args == null ? null : args.getString(ARG_INITIAL_QUERY);
    }

    private void resolveCategoryIdFromLoadedCategories() {
        if (activeCategoryId != null || activeCategoryName == null
                || "All".equalsIgnoreCase(activeCategoryName)) return;
        for (Category category : categories) {
            if (category != null && category.getName() != null
                    && activeCategoryName.equalsIgnoreCase(category.getName())) {
                activeCategoryId = category.getId();
                break;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Catalog API call
    // ─────────────────────────────────────────────────────────────────────────

    private void loadProductsFromBackend(String searchText) {
        String q = normalizeQuery(searchText);

        Boolean stockOnly = activeInStockOnly ? Boolean.TRUE : null;
        Boolean dealsOnly = activeDealsOnly   ? Boolean.TRUE : null;
        Double  minPrice  = activePriceMin > DEFAULT_PRICE_MIN ? (double) activePriceMin : null;
        Double  maxPrice  = activePriceMax < DEFAULT_PRICE_MAX ? (double) activePriceMax : null;

        requestCatalogProducts(false, q, stockOnly, dealsOnly, minPrice, maxPrice)
                .enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<JsonObject>> call,
                                           @NonNull Response<List<JsonObject>> response) {
                        if (!isAdded()) return;
                        if (response.code() == 405) {
                            retryCatalogLoadWithPost(q, stockOnly, dealsOnly, minPrice, maxPrice);
                            return;
                        }
                        handleCatalogResponse(response);
                    }
                    @Override
                    public void onFailure(@NonNull Call<List<JsonObject>> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        Log.e(TAG, "Catalog request failed: " + t.getMessage(), t);
                        Toast.makeText(requireContext(), "Network error loading products", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @NonNull
    private Call<List<JsonObject>> requestCatalogProducts(boolean usePost,
                                                          @Nullable String q,
                                                          @Nullable Boolean stockOnly,
                                                          @Nullable Boolean dealsOnly,
                                                          @Nullable Double minPrice,
                                                          @Nullable Double maxPrice) {
        return usePost
                ? apiService.getCatalogProductsPost(q, activeCategoryId, activeSortKey, stockOnly, dealsOnly, minPrice, maxPrice)
                : apiService.getCatalogProducts    (q, activeCategoryId, activeSortKey, stockOnly, dealsOnly, minPrice, maxPrice);
    }

    private void retryCatalogLoadWithPost(@Nullable String q,
                                          @Nullable Boolean stockOnly,
                                          @Nullable Boolean dealsOnly,
                                          @Nullable Double minPrice,
                                          @Nullable Double maxPrice) {
        Log.w(TAG, "Catalog GET returned 405, retrying with POST");
        requestCatalogProducts(true, q, stockOnly, dealsOnly, minPrice, maxPrice)
                .enqueue(new Callback<List<JsonObject>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<JsonObject>> call,
                                           @NonNull Response<List<JsonObject>> response) {
                        if (!isAdded()) return;
                        handleCatalogResponse(response);
                    }
                    @Override
                    public void onFailure(@NonNull Call<List<JsonObject>> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        Log.e(TAG, "Catalog POST retry failed: " + t.getMessage(), t);
                        Toast.makeText(requireContext(), "Network error loading products", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Response handling
    // ─────────────────────────────────────────────────────────────────────────

    private void handleCatalogResponse(@NonNull Response<List<JsonObject>> response) {
        if (!response.isSuccessful()) {
            Log.e(TAG, "Catalog load failed: HTTP " + response.code()
                    + " " + response.message() + " | body=" + extractErrorBody(response));
            Toast.makeText(requireContext(), "Failed to load products", Toast.LENGTH_SHORT).show();
            return;
        }

        List<JsonObject> responseBody = response.body() != null
                ? response.body() : Collections.emptyList();

        List<Product> mapped = new ArrayList<>();
        for (JsonObject item : responseBody) {
            Product product = mapCatalogItemToProduct(item);
            if (product != null) mapped.add(product);
        }

        List<Product> filtered = applyClientSideFilter(mapped);
        List<Product> sorted   = applyClientSideSort(filtered);

        productList.clear();
        productList.addAll(filterNewestIfRequired(sorted));
        productAdapter.notifyDataSetChanged();

        enrichVariantsInBackground(filtered);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Client-side filter & sort  (single source of truth)
    // ─────────────────────────────────────────────────────────────────────────

    @NonNull
    private List<Product> applyClientSideFilter(@NonNull List<Product> source) {
        boolean priceFilterActive = activePriceMin > DEFAULT_PRICE_MIN
                || activePriceMax < DEFAULT_PRICE_MAX;

        List<Product> result = new ArrayList<>();
        for (Product p : source) {
            boolean categoryMatch = activeCategoryId == null
                    || p.getCategoryId() == null
                    || p.getCategoryId().equals(activeCategoryId);

            boolean priceMatch = true;
            if (priceFilterActive) {
                Double pMin = p.getMinPrice();
                Double pMax = p.getMaxPrice();
                if (pMin != null && pMax != null) {
                    priceMatch = pMin <= activePriceMax && pMax >= activePriceMin;
                }
            }

            if (categoryMatch && priceMatch) result.add(p);
        }
        return result;
    }

    @NonNull
    private List<Product> applyClientSideSort(@NonNull List<Product> source) {
        List<Product> result = new ArrayList<>(source);
        if ("price_desc".equals(activeSortKey)) {
            result.sort((p1, p2) -> {
                Double m1 = p1.getMaxPrice(), m2 = p2.getMaxPrice();
                if (m1 == null && m2 == null) return 0;
                if (m1 == null) return 1;
                if (m2 == null) return -1;
                return Double.compare(m2, m1);
            });
        } else if ("lowprice".equals(activeSortKey)) {
            result.sort((p1, p2) -> {
                Double m1 = p1.getMinPrice(), m2 = p2.getMinPrice();
                if (m1 == null && m2 == null) return 0;
                if (m1 == null) return 1;
                if (m2 == null) return -1;
                return Double.compare(m1, m2);
            });
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Background variant enrichment
    // ─────────────────────────────────────────────────────────────────────────

    private void enrichVariantsInBackground(@NonNull List<Product> displayedProducts) {
        if (displayedProducts.isEmpty()) return;

        List<Product> withIds = new ArrayList<>();
        for (Product p : displayedProducts) {
            if (p.getId() != null) withIds.add(p);
        }
        if (withIds.isEmpty()) return;

        AtomicInteger pending = new AtomicInteger(withIds.size());

        for (Product product : withIds) {
            Long productId = product.getId();
            if (productId == null) {
                if (pending.decrementAndGet() == 0 && isAdded()) refreshDisplayedList(displayedProducts);
                continue;
            }

            apiService.getVariantsByProductId(productId).enqueue(new Callback<List<ProductVariant>>() {
                @Override
                public void onResponse(@NonNull Call<List<ProductVariant>> call,
                                       @NonNull Response<List<ProductVariant>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        product.setVariants(response.body());
                        product.setDealLabel(hasDeals(response.body()));
                    }
                    updateNewestLabelFromStock(product, () -> {
                        if (pending.decrementAndGet() == 0 && isAdded()) {
                            refreshDisplayedList(displayedProducts);
                        }
                    });
                }
                @Override
                public void onFailure(@NonNull Call<List<ProductVariant>> call, @NonNull Throwable t) {
                    updateNewestLabelFromStock(product, () -> {
                        if (pending.decrementAndGet() == 0 && isAdded()) {
                            refreshDisplayedList(displayedProducts);
                        }
                    });
                }
            });
        }
    }

    private void refreshDisplayedList(@NonNull List<Product> displayedProducts) {
        List<Product> reFiltered = applyClientSideFilter(displayedProducts);
        List<Product> reSorted   = applyClientSideSort(reFiltered);
        productList.clear();
        productList.addAll(filterNewestIfRequired(reSorted));
        productAdapter.notifyDataSetChanged();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Newest label helpers
    // ─────────────────────────────────────────────────────────────────────────

    @NonNull
    private List<Product> filterNewestIfRequired(@NonNull List<Product> source) {
        if (!isNewestOnlyMode()) return source;
        List<Product> filtered = new ArrayList<>();
        for (Product product : source) {
            if (product != null && Boolean.TRUE.equals(product.getNewestLabel())) filtered.add(product);
        }
        return filtered;
    }

    private boolean isNewestOnlyMode() {
        return "newest".equals(activeSortKey);
    }

    private void updateNewestLabelFromStock(@NonNull Product product, @Nullable Runnable onDone) {
        Long productId = product.getId();
        ProductVariant variant = resolveVariantForBatchCheck(product.getVariants());
        if (productId == null || variant == null || variant.getId() == null) {
            if (onDone != null) onDone.run();
            return;
        }

        apiService.getStockBatchesByProductAndVariant(productId, variant.getId())
                .enqueue(new Callback<List<ProductStockSummary>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ProductStockSummary>> call,
                                           @NonNull Response<List<ProductStockSummary>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            for (ProductStockSummary summary : response.body()) {
                                if (summary != null && isCreatedToday(summary.createdAt)) {
                                    product.setNewestLabel(true);
                                    break;
                                }
                            }
                        }
                        if (onDone != null) onDone.run();
                    }
                    @Override
                    public void onFailure(@NonNull Call<List<ProductStockSummary>> call, @NonNull Throwable t) {
                        if (onDone != null) onDone.run();
                    }
                });
    }

    @Nullable
    private ProductVariant resolveVariantForBatchCheck(@Nullable List<ProductVariant> variants) {
        if (variants == null || variants.isEmpty()) return null;
        for (ProductVariant variant : variants) {
            if (variant != null && variant.getId() != null && variant.isInStock()) return variant;
        }
        for (ProductVariant variant : variants) {
            if (variant != null && variant.getId() != null) return variant;
        }
        return null;
    }

    private boolean isCreatedToday(@Nullable String createdAt) {
        if (createdAt == null) return false;
        String trimmed = createdAt.trim();
        if (trimmed.length() < 10) return false;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        return trimmed.startsWith(today);
    }

    private boolean hasDeals(@NonNull List<ProductVariant> variants) {
        for (ProductVariant variant : variants) {
            if (variant != null && variant.hasDeal()) return true;
        }
        return false;
    }

    @NonNull
    private String extractErrorBody(@NonNull Response<?> response) {
        if (response.errorBody() == null) return "<empty>";
        try {
            String body = response.errorBody().string();
            return body == null || body.trim().isEmpty() ? "<empty>" : body;
        } catch (IOException e) {
            return "<unreadable error body>";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON → Product mapping
    // ─────────────────────────────────────────────────────────────────────────

    @Nullable
    private Product mapCatalogItemToProduct(@Nullable JsonObject item) {
        if (item == null) return null;
        try {
            Product product = new Product();

            Long id = getAsLong(item, "id");
            if (id != null) product.setId(id);

            String name = getAsString(item, "name");
            if (name != null) product.setName(name);

            String categoryName = getAsString(item, "categoryName");
            Long   categoryId   = getAsLong(item, "categoryId");
            if (categoryName != null) {
                Category cat = new Category();
                cat.setName(categoryName);
                if (categoryId != null) trySetLong(cat, "setId", categoryId);
                product.setCategoryObject(cat);
                trySetString(product, "setCategory", categoryName);
            }
            if (categoryId != null) product.setCategoryId(categoryId);

            trySetString(product, "setSku",         getAsString(item, "sku"));
            trySetString(product, "setUnit",        getAsString(item, "unit"));
            trySetString(product, "setDescription", getAsString(item, "description"));
            trySetString(product, "setStatus",      getAsString(item, "status"));
            trySetString(product, "setImageUrl",    getAsString(item, "imageUrl"));

            Double  effectiveMinPrice = getAsDouble(item, "effectiveMinPrice");
            Double  minRegularPrice   = getAsDouble(item, "minRegularPrice");
            Double  minDealPrice      = getAsDouble(item, "minDealPrice");
            Integer totalStock        = getAsInt(item, "totalStock");
            Boolean hasActiveDeal     = getAsBoolean(item, "hasActiveDeal");

            Boolean hasDeal = firstNonNullBoolean(
                    hasActiveDeal,
                    getAsBoolean(item, "hasDeal"),
                    getAsBoolean(item, "dealActive"),
                    getAsBoolean(item, "onDeal"));

            Boolean isNewest = firstNonNullBoolean(
                    getAsBoolean(item, "isNewest"),
                    getAsBoolean(item, "newest"),
                    getAsBoolean(item, "isNew"),
                    getAsBoolean(item, "newArrival"));

            if (hasDeal  != null) product.setDealLabel(hasDeal);
            if (isNewest != null) product.setNewestLabel(isNewest);

            ProductVariant variant = new ProductVariant();
            variant.setVariantName(resolveVariantDisplayName(item, product));

            if (effectiveMinPrice != null) {
                variant.setPrice(effectiveMinPrice);
            } else if (minRegularPrice != null) {
                variant.setPrice(minRegularPrice);
            }

            variant.setStock(totalStock != null ? totalStock : 0);

            if (minDealPrice      != null) trySetDouble (variant, "setDealPrice",     minDealPrice);
            if (effectiveMinPrice != null) trySetDouble (variant, "setEffectivePrice", effectiveMinPrice);
            if (hasActiveDeal     != null) trySetBoolean(variant, "setHasActiveDeal",  hasActiveDeal);

            product.setVariants(Collections.singletonList(variant));
            return product;

        } catch (Exception e) {
            Log.e(TAG, "Failed to map catalog product: " + e.getMessage(), e);
            return null;
        }
    }

    @NonNull
    private String resolveVariantDisplayName(@NonNull JsonObject item, @NonNull Product product) {
        String v = getAsString(item, "variantName");
        if (isValidVariantLabel(v)) return v.trim();
        v = getAsString(item, "displayVariant");
        if (isValidVariantLabel(v)) return v.trim();
        v = getAsString(item, "unit");
        if (isValidVariantLabel(v)) return v.trim();
        String unit = product.getUnit();
        if (isValidVariantLabel(unit)) return unit.trim();
        return "1 item";
    }

    private boolean isValidVariantLabel(@Nullable String value) {
        if (value == null) return false;
        String n = value.trim();
        return !n.isEmpty() && !"default".equalsIgnoreCase(n);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────

    private int getGridSpanCount() {
        int w = getResources().getConfiguration().screenWidthDp;
        if (w >= 840) return 4;
        if (w >= 600) return 3;
        return 2;
    }

    private void showFilterBottomSheet() {
        BottomSheetDialog filterSheet =
                new BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);
        View sheetView = LayoutInflater.from(getContext())
                .inflate(R.layout.layout_filter_bottom_sheet, null);

        RangeSlider    priceSlider     = sheetView.findViewById(R.id.priceSlider);
        TextView       txtPriceDisplay = sheetView.findViewById(R.id.txtPriceDisplay);
        ChipGroup      sortChipGroup   = sheetView.findViewById(R.id.sortChipGroup);
        ChipGroup      categoryGroup   = sheetView.findViewById(R.id.categoryChipGroup);
        MaterialButton btnApply        = sheetView.findViewById(R.id.btnApply);
        TextView       btnReset        = sheetView.findViewById(R.id.btnReset);
        MaterialSwitch switchInStock   = sheetView.findViewById(R.id.switchInStock);
        MaterialSwitch switchDeals     = sheetView.findViewById(R.id.switchDeals);

        bindCategoryChips(categoryGroup);

        priceSlider.setValues(activePriceMin, activePriceMax);
        txtPriceDisplay.setText("LKR " + (int) activePriceMin + " - LKR " + (int) activePriceMax);
        priceSlider.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> vals = slider.getValues();
            txtPriceDisplay.setText("LKR " + vals.get(0).intValue() + " - LKR " + vals.get(1).intValue());
        });

        switchInStock.setChecked(activeInStockOnly);
        switchDeals.setChecked(activeDealsOnly);
        restoreSortSelection(sortChipGroup);

        btnReset.setOnClickListener(v -> {
            activeSortKey      = null;
            activeCategoryName = "All";
            activeCategoryId   = null;
            activePriceMin     = DEFAULT_PRICE_MIN;
            activePriceMax     = DEFAULT_PRICE_MAX;
            activeInStockOnly  = false;
            activeDealsOnly    = false;
            filterSheet.dismiss();
            // quick chips touch කරන්නේ නෑ — filter sheet reset only
            applyAllFilters(getCurrentSearchQuery(), false);
        });

        btnApply.setOnClickListener(v -> {
            // ✅ ID-based sort resolve — text parsing නෑ
            String selectedSort = resolveSortKeyFromChipGroup(sortChipGroup);
            activeDealsOnly = switchDeals.isChecked() || "deals".equals(selectedSort);
            activeSortKey   = activeDealsOnly ? null : selectedSort;

            activeCategoryName = "All";
            activeCategoryId   = null;
            int catCheckedId = categoryGroup.getCheckedChipId();
            if (catCheckedId != -1) {
                Chip chip = sheetView.findViewById(catCheckedId);
                if (chip != null) {
                    activeCategoryName = chip.getText() != null ? chip.getText().toString() : "All";
                    Object tag = chip.getTag();
                    if      (tag instanceof Long)    activeCategoryId = (Long) tag;
                    else if (tag instanceof Integer) activeCategoryId = ((Integer) tag).longValue();
                }
            }

            List<Float> vals = priceSlider.getValues();
            activePriceMin    = vals.get(0);
            activePriceMax    = vals.get(1);
            activeInStockOnly = switchInStock.isChecked();

            filterSheet.dismiss();
            // quick chips touch කරන්නේ නෑ — filter sheet only
            applyAllFilters(getCurrentSearchQuery(), false);
        });

        filterSheet.setContentView(sheetView);
        filterSheet.show();
    }

    private void bindCategoryChips(ChipGroup categoryGroup) {
        categoryGroup.removeAllViews();
        categoryGroup.setSingleSelection(true);
        categoryGroup.setSelectionRequired(true);

        Chip allChip = new Chip(getContext());
        allChip.setId(View.generateViewId());
        allChip.setText("All");
        allChip.setCheckable(true);
        allChip.setChipCornerRadius(12f);
        allChip.setTag(null);
        categoryGroup.addView(allChip);

        for (Category cat : categories) {
            Chip chip = new Chip(getContext());
            chip.setId(View.generateViewId());
            chip.setText(cat.getName());
            chip.setCheckable(true);
            chip.setChipCornerRadius(12f);
            chip.setTag(cat.getId());
            categoryGroup.addView(chip);
        }

        boolean selected = false;
        for (int i = 0; i < categoryGroup.getChildCount(); i++) {
            Chip chip = (Chip) categoryGroup.getChildAt(i);
            String label = chip.getText() == null ? "" : chip.getText().toString();
            if (activeCategoryName.equalsIgnoreCase(label)) {
                chip.setChecked(true);
                selected = true;
                break;
            }
        }
        if (!selected && categoryGroup.getChildCount() > 0) {
            ((Chip) categoryGroup.getChildAt(0)).setChecked(true);
        }
    }

    /**
     * ✅ FIX: ID-based restore — text parsing remove කළා.
     * "Price High → Low" contains "low" නිසා text match ambiguous වෙනවා.
     */
    private void restoreSortSelection(ChipGroup sortChipGroup) {
        if (activeSortKey == null && !activeDealsOnly) {
            sortChipGroup.clearCheck();
            return;
        }

        int targetId = -1;
        if      ("popular".equals(activeSortKey))    targetId = R.id.chipSortPopular;
        else if ("newest".equals(activeSortKey))     targetId = R.id.chipSortNewest;
        else if ("lowprice".equals(activeSortKey))   targetId = R.id.chipSortPriceLow;
        else if ("price_desc".equals(activeSortKey)) targetId = R.id.chipSortPriceHigh;
        // activeDealsOnly → no sort chip to select, leave cleared

        if (targetId != -1) {
            sortChipGroup.check(targetId);
        } else {
            sortChipGroup.clearCheck();
        }
    }

    private void setQuickFilterState(@Nullable String sortKey, boolean dealsOnly) {
        activeSortKey   = dealsOnly ? null : sortKey;
        activeDealsOnly = dealsOnly;
        syncQuickFilterChips();
        applyAllFilters(getCurrentSearchQuery(), false);
    }

    private void syncQuickFilterChips() {
        if (chipGroupQuickFilters == null) return;
        suppressQuickChipCallback = true;
        int targetChipId = R.id.chipAll;
        if      (activeDealsOnly)                    targetChipId = R.id.chipDeals;
        else if ("popular".equals(activeSortKey))    targetChipId = R.id.chipPopular;
        else if ("lowprice".equals(activeSortKey))   targetChipId = R.id.chipLowestPrice;
        else if ("price_desc".equals(activeSortKey)) targetChipId = R.id.chipHighestPrice;
        else if ("newest".equals(activeSortKey))     targetChipId = R.id.chipNewest;
        chipGroupQuickFilters.check(targetChipId);
        suppressQuickChipCallback = false;
    }

    /**
     * ✅ FIX: ID-based sort resolve — text parsing remove කළා.
     * "Price High → Low" contains "low" නිසා text match ambiguous වෙනවා.
     */
    @Nullable
    private String resolveSortKeyFromChipGroup(ChipGroup sortChipGroup) {
        int checkedId = sortChipGroup.getCheckedChipId();
        if (checkedId == -1) return null;

        if      (checkedId == R.id.chipSortPopular)   return "popular";
        else if (checkedId == R.id.chipSortNewest)    return "newest";
        else if (checkedId == R.id.chipSortPriceLow)  return "lowprice";
        else if (checkedId == R.id.chipSortPriceHigh) return "price_desc";

        return null;
    }

    private String getCurrentSearchQuery() {
        return searchEditText == null || searchEditText.getText() == null
                ? "" : searchEditText.getText().toString();
    }

    @Nullable
    private String normalizeQuery(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON helpers
    // ─────────────────────────────────────────────────────────────────────────

    @Nullable private String  getAsString (JsonObject obj, String key) { JsonElement el = obj.get(key); return (el == null || el.isJsonNull()) ? null : el.getAsString();  }
    @Nullable private Long    getAsLong   (JsonObject obj, String key) { JsonElement el = obj.get(key); return (el == null || el.isJsonNull()) ? null : el.getAsLong();    }
    @Nullable private Integer getAsInt    (JsonObject obj, String key) { JsonElement el = obj.get(key); return (el == null || el.isJsonNull()) ? null : el.getAsInt();     }
    @Nullable private Double  getAsDouble (JsonObject obj, String key) { JsonElement el = obj.get(key); return (el == null || el.isJsonNull()) ? null : el.getAsDouble();  }
    @Nullable private Boolean getAsBoolean(JsonObject obj, String key) { JsonElement el = obj.get(key); return (el == null || el.isJsonNull()) ? null : el.getAsBoolean(); }

    @Nullable
    private Boolean firstNonNullBoolean(@Nullable Boolean... values) {
        if (values == null) return null;
        for (Boolean value : values) { if (value != null) return value; }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reflection setters
    // ─────────────────────────────────────────────────────────────────────────

    private void trySetString (Object t, String m, @Nullable String  v) { if (t==null||v==null) return; try { t.getClass().getMethod(m,String.class) .invoke(t,v); } catch (Exception ignored) {} }
    private void trySetLong   (Object t, String m, @Nullable Long    v) { if (t==null||v==null) return; try { t.getClass().getMethod(m,Long.class)  .invoke(t,v); } catch (Exception ignored) {} }
    private void trySetDouble (Object t, String m, @Nullable Double  v) { if (t==null||v==null) return; try { t.getClass().getMethod(m,Double.class) .invoke(t,v); } catch (Exception ignored) {} }
    private void trySetBoolean(Object t, String m, @Nullable Boolean v) {
        if (t==null||v==null) return;
        try { t.getClass().getMethod(m,boolean.class).invoke(t,v); return; } catch (Exception ignored) {}
        try { t.getClass().getMethod(m,Boolean.class).invoke(t,v);         } catch (Exception ignored) {}
    }
}
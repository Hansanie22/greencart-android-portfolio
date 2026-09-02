package com.hansanie.greencart.fragment;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import androidx.viewpager2.widget.ViewPager2;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import com.hansanie.greencart.R;
import com.hansanie.greencart.adapter.CategoryAdapter;
import com.hansanie.greencart.adapter.OfferBannerAdapter;
import com.hansanie.greencart.adapter.ProductAdapter;
import com.hansanie.greencart.model.*;
import com.hansanie.greencart.network.ApiService;
import com.hansanie.greencart.network.RetrofitClient;
import com.hansanie.greencart.util.CustomToast;
import com.journeyapps.barcodescanner.*;

import java.util.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.*;

public class HomeFragment extends Fragment implements CategoryAdapter.OnCategoryClickListener {
    // Adjustable shake sensitivity threshold (higher = less sensitive)
    private static float SHAKE_THRESHOLD = 15f;
    // Optionally expose this in settings for user adjustment

    private static final int VOICE_REQUEST_CODE = 101;

    private static final class FrequentVariantPattern {
        final long productId;
        @Nullable final Long variantId;
        @Nullable final String variantName;
        final int quantity;
        int purchaseCount;
        int totalUnits;

        FrequentVariantPattern(long productId,
                               @Nullable Long variantId,
                               @Nullable String variantName,
                               int quantity) {
            this.productId = productId;
            this.variantId = variantId;
            this.variantName = variantName;
            this.quantity = quantity;
        }
    }

    private RecyclerView rvCategories, rvProducts, rvQuickReorder;
    private CategoryAdapter categoryAdapter;
    private ProductAdapter productAdapter, quickReorderAdapter;

    private List<Category> categoryList = new ArrayList<>();
    private List<Product> productList = new ArrayList<>();
    private List<Product> quickReorderList = new ArrayList<>();
    private List<Product> fullProductList = new ArrayList<>();
    private final List<Product> searchableProductList = new ArrayList<>();
    private final Map<Long, Integer> frequentPurchaseCounts = new HashMap<>();
    private final Map<Long, ProductAdapter.PreferredQuickAddChoice> preferredQuickAddChoices = new HashMap<>();

    private FirebaseFirestore db;
    private ApiService apiService;

    private EditText searchEditText;
    private TextView tvProductCount, tvGreeting, tvUserName, btnViewAllProducts;
    private View btnVoiceSearch, btnQRSearch;

    /* ── PROMO BANNER ─────────────────────────────────────────────────────── */
    private View promoBannerContainer;
    private ViewPager2 vpOfferBanners;
    private LinearLayout layoutOfferIndicators;
    private OfferBannerAdapter offerBannerAdapter;
    private final List<Offer> availableOffers = new ArrayList<>();
    private final Set<Long> claimedOfferIds = new HashSet<>();
    private final Set<Long> activeSubscriptionProductIds = new HashSet<>();
    private double subscriptionDiscountPercent = 10.0;

    private boolean hasActiveOffer = false;
    private long lastShakeTs = 0L;

    /* ── QR SCANNER ───────────────────────────────────────────────────────── */
    private final ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    handleScannedProductId(result.getContents());
                }
            });

    private final Set<Long> shownOfferIds = new HashSet<>();
    private ListenerRegistration offerListenerRegistration;

    public HomeFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = FirebaseFirestore.getInstance();
        apiService = RetrofitClient.getApiService();
        initViews(view);
        setupRecyclerViews();
        setupSearch();
        loadCategories();
        loadProducts();
        loadFrequentlyOrderedProducts();
        loadActiveSubscriptionState();
        listenForOffersRealtime(); // replaces checkForUserOffers()
        tvGreeting.setText(getTimeGreeting() + ",");
        setCurrentUserName();
    }

    private void initViews(View view) {
        rvCategories    = view.findViewById(R.id.rvCategories);
        rvProducts      = view.findViewById(R.id.rvProducts);
        rvQuickReorder  = view.findViewById(R.id.rvQuickReorder);

        searchEditText      = view.findViewById(R.id.searchEditText);
        tvProductCount      = view.findViewById(R.id.tvProductCount);
        tvGreeting          = view.findViewById(R.id.tvGreeting);
        tvUserName          = view.findViewById(R.id.tvUserName);
        btnVoiceSearch      = view.findViewById(R.id.btnVoiceSearch);
        btnQRSearch         = view.findViewById(R.id.btnQRSearch);
        btnViewAllProducts  = view.findViewById(R.id.btnViewAllProducts);

        btnVoiceSearch.setOnClickListener(v -> startVoiceRecognition());
        btnQRSearch.setOnClickListener(v -> startQRScanner());
        btnViewAllProducts.setOnClickListener(v -> openProductCatalog(null, "All", true, null));

        /* PROMO BANNER VIEWS */
        promoBannerContainer = view.findViewById(R.id.promo_banner_root);
        vpOfferBanners       = view.findViewById(R.id.vpOfferBanners);
        layoutOfferIndicators = view.findViewById(R.id.layoutOfferIndicators);

        promoBannerContainer.setVisibility(View.GONE);

        offerBannerAdapter = new OfferBannerAdapter(this::navigateToShakeToWin);
        vpOfferBanners.setAdapter(offerBannerAdapter);
        vpOfferBanners.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateOfferIndicators(position);
            }
        });
    }

    private String getTimeGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return "Good Morning";
        if (hour < 17) return "Good Afternoon";
        return "Good Evening";
    }

    private void setCurrentUserName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { tvUserName.setText("Guest 👋"); return; }
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("first_name");
                    tvUserName.setText(name != null ? name + " 👋" : "User 👋");
                });
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say product name");
        startActivityForResult(intent, VOICE_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST_CODE
                && resultCode == requireActivity().RESULT_OK
                && data != null) {
            ArrayList<String> result =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                String spoken = result.get(0);
                searchEditText.setText(spoken);
                navigateToProductDetailsFromQuery(spoken);
            }
        }
    }

    private void startQRScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan product QR");
        options.setBeepEnabled(true);
        barcodeLauncher.launch(options);
    }

    private void handleScannedProductId(String id) {
        searchEditText.setText(id);
        Long numericId = asLong(id);
        if (numericId != null) {
            apiService.getProductDetails(numericId).enqueue(new Callback<Product>() {
                @Override
                public void onResponse(Call<Product> call, Response<Product> response) {
                    if (!isAdded()) return;
                    if (response.isSuccessful() && response.body() != null) {
                        Product p = response.body();
                        if (p.getId() == null) p.setId(numericId);
                        openProductDetails(p);
                    } else {
                        navigateToProductDetailsFromQuery(id);
                    }
                }

                @Override
                public void onFailure(Call<Product> call, Throwable t) {
                    if (!isAdded()) return;
                    navigateToProductDetailsFromQuery(id);
                }
            });
            return;
        }

        navigateToProductDetailsFromQuery(id);
    }

    private void setupRecyclerViews() {
        rvCategories.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new CategoryAdapter(categoryList, this);
        rvCategories.setAdapter(categoryAdapter);

        rvQuickReorder.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        quickReorderAdapter = new ProductAdapter(quickReorderList, true);
        quickReorderAdapter.setFrequentPurchaseCounts(frequentPurchaseCounts);
        quickReorderAdapter.setPreferredQuickAddChoices(preferredQuickAddChoices);
        quickReorderAdapter.setOnItemClickListener(this::openProductDetails);
        rvQuickReorder.setAdapter(quickReorderAdapter);

        rvProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        productAdapter = new ProductAdapter(productList, false);
        rvProducts.setAdapter(productAdapter);
        productAdapter.setOnItemClickListener(this::openProductDetails);
    }

    private void openProductDetails(Product product) {
        if (!isAdded()) return;
        ProductDetailsFragment fragment =
                ProductDetailsFragment.newInstance(String.valueOf(product.getId()));
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)  // ← correct container
                .addToBackStack(null)
                .commit();
    }

    private void loadCategories() {
        db.collection("categories").get().addOnSuccessListener(snapshot -> {
            categoryList.clear();
            for (QueryDocumentSnapshot doc : snapshot) {
                Category cat = doc.toObject(Category.class);
                if (doc.contains("id")) cat.setId(doc.getLong("id"));
                categoryList.add(cat);
            }
            categoryAdapter.notifyDataSetChanged();
        });
    }

    // ── Load products from Firestore then fetch variants from backend ─────────
    private void loadProducts() {
        db.collection("products").whereEqualTo("status", "active").get()
                .addOnSuccessListener(snapshot -> {
                    List<Product> tempList = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Product p = doc.toObject(Product.class);
                        if (doc.contains("id")) p.setId(doc.getLong("id"));
                        if (p.getCategory() != null) {
                            Category cat = new Category();
                            cat.setName(p.getCategory());
                            p.setCategoryObject(cat);
                        }
                        tempList.add(p);
                    }
                    loadVariantsForProducts(tempList);
                })
                .addOnFailureListener(e ->
                        Log.e("HOME_FIRESTORE", "loadProducts failed: " + e.getMessage()));
    }

    /** Fetch variants from backend for each product (mirrors ProductFragment logic). */
    private void loadVariantsForProducts(List<Product> products) {
        if (products.isEmpty()) {
            updateProductUI(products);
            return;
        }

        AtomicInteger pending = new AtomicInteger(products.size());
        for (Product product : products) {
            if (product.getId() == null) {
                if (pending.decrementAndGet() == 0) updateProductUI(products);
                continue;
            }
            apiService.getVariantsByProductId(product.getId())
                    .enqueue(new Callback<List<ProductVariant>>() {
                        @Override
                        public void onResponse(Call<List<ProductVariant>> call,
                                               Response<List<ProductVariant>> response) {
                            if (response.isSuccessful() && response.body() != null)
                                product.setVariants(response.body());
                            if (pending.decrementAndGet() == 0) updateProductUI(products);
                        }

                        @Override
                        public void onFailure(Call<List<ProductVariant>> call, Throwable t) {
                            Log.e("HOME_VARIANTS", "Variant fetch failed: " + t.getMessage());
                            if (pending.decrementAndGet() == 0) updateProductUI(products);
                        }
                    });
        }
    }

    private void updateProductUI(List<Product> products) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            List<Product> dealProducts = new ArrayList<>();
            for (Product product : products) {
                if (hasDealVariant(product)) {
                    dealProducts.add(product);
                }
            }

            searchableProductList.clear();
            searchableProductList.addAll(products);
            fullProductList.clear();
            fullProductList.addAll(dealProducts);
            productList.clear();
            productList.addAll(dealProducts);
            productAdapter.notifyDataSetChanged();
            if (tvProductCount != null)
                tvProductCount.setText(productList.size() + " Items Available");
        });
    }

    private void loadFrequentlyOrderedProducts() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            frequentPurchaseCounts.clear();
            preferredQuickAddChoices.clear();
            quickReorderAdapter.setFrequentPurchaseCounts(frequentPurchaseCounts);
            quickReorderAdapter.setPreferredQuickAddChoices(preferredQuickAddChoices);
            loadFallbackQuickReorder();
            return;
        }

        db.collection("orders")
                .whereEqualTo("firebaseUid", user.getUid())
                .limit(25)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<Long, Integer> scoreByProduct = new HashMap<>();
                    Map<Long, Integer> totalUnitsByProduct = new HashMap<>();
                    Map<String, FrequentVariantPattern> patternByKey = new HashMap<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Object itemsObj = doc.get("items");
                        if (!(itemsObj instanceof List)) {
                            continue;
                        }
                        List<?> items = (List<?>) itemsObj;
                        for (Object raw : items) {
                            if (!(raw instanceof Map)) {
                                continue;
                            }
                            Map<?, ?> item = (Map<?, ?>) raw;
                            Long productId = asLong(item.get("productId"));
                            Integer qty = asInt(item.get("quantity"));
                            if (productId == null) {
                                continue;
                            }

                            int resolvedQty = qty != null && qty > 0 ? qty : 1;
                            scoreByProduct.put(productId, scoreByProduct.getOrDefault(productId, 0) + 1);
                            totalUnitsByProduct.put(productId,
                                    totalUnitsByProduct.getOrDefault(productId, 0) + resolvedQty);

                            Long variantId = asLong(item.get("variantId"));
                            String variantName = asString(item.get("variantName"));
                            String comboKey = buildFrequentPatternKey(productId, variantId, variantName, resolvedQty);
                            FrequentVariantPattern pattern = patternByKey.get(comboKey);
                            if (pattern == null) {
                                pattern = new FrequentVariantPattern(productId, variantId, variantName, resolvedQty);
                                patternByKey.put(comboKey, pattern);
                            }
                            pattern.purchaseCount++;
                            pattern.totalUnits += resolvedQty;
                        }
                    }

                    if (scoreByProduct.isEmpty()) {
                        frequentPurchaseCounts.clear();
                        preferredQuickAddChoices.clear();
                        quickReorderAdapter.setFrequentPurchaseCounts(frequentPurchaseCounts);
                        quickReorderAdapter.setPreferredQuickAddChoices(preferredQuickAddChoices);
                        loadFallbackQuickReorder();
                        return;
                    }

                    frequentPurchaseCounts.clear();
                    frequentPurchaseCounts.putAll(scoreByProduct);
                    preferredQuickAddChoices.clear();
                    preferredQuickAddChoices.putAll(buildPreferredQuickAddChoices(patternByKey.values()));
                    quickReorderAdapter.setFrequentPurchaseCounts(frequentPurchaseCounts);
                    quickReorderAdapter.setPreferredQuickAddChoices(preferredQuickAddChoices);

                    List<Long> sortedProductIds = new ArrayList<>(scoreByProduct.keySet());
                    sortedProductIds.sort((a, b) -> {
                        int hitCompare = Integer.compare(scoreByProduct.getOrDefault(b, 0), scoreByProduct.getOrDefault(a, 0));
                        if (hitCompare != 0) {
                            return hitCompare;
                        }
                        return Integer.compare(totalUnitsByProduct.getOrDefault(b, 0), totalUnitsByProduct.getOrDefault(a, 0));
                    });
                    if (sortedProductIds.size() > 8) {
                        sortedProductIds = sortedProductIds.subList(0, 8);
                    }
                    loadProductsByIds(sortedProductIds);
                })
                .addOnFailureListener(e -> {
                    frequentPurchaseCounts.clear();
                    preferredQuickAddChoices.clear();
                    quickReorderAdapter.setFrequentPurchaseCounts(frequentPurchaseCounts);
                    quickReorderAdapter.setPreferredQuickAddChoices(preferredQuickAddChoices);
                    loadFallbackQuickReorder();
                });
    }

    private void loadProductsByIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            loadFallbackQuickReorder();
            return;
        }

        quickReorderList.clear();
        Map<Long, Product> fetchedById = new HashMap<>();
        AtomicInteger pending = new AtomicInteger(productIds.size());
        for (Long id : productIds) {
            db.collection("products")
                    .whereEqualTo("id", id)
                    .whereEqualTo("status", "active")
                    .limit(1)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (!snapshot.isEmpty()) {
                            QueryDocumentSnapshot doc = (QueryDocumentSnapshot) snapshot.getDocuments().get(0);
                            Product product = doc.toObject(Product.class);
                            if (doc.contains("id")) product.setId(doc.getLong("id"));
                            if (product.getCategory() != null) {
                                Category cat = new Category();
                                cat.setName(product.getCategory());
                                product.setCategoryObject(cat);
                            }
                            if (product.getId() != null) {
                                fetchedById.put(product.getId(), product);
                            }
                        }
                        if (pending.decrementAndGet() == 0) {
                            quickReorderList.clear();
                            for (Long productId : productIds) {
                                Product product = fetchedById.get(productId);
                                if (product != null) {
                                    quickReorderList.add(product);
                                }
                            }
                            loadVariantsForQuickReorder();
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (pending.decrementAndGet() == 0) {
                            loadVariantsForQuickReorder();
                        }
                    });
        }
    }

    private void loadFallbackQuickReorder() {
        db.collection("products")
                .whereEqualTo("status", "active")
                .limit(6)
                .get()
                .addOnSuccessListener(snapshot -> {
                    quickReorderList.clear();
                    frequentPurchaseCounts.clear();
                    preferredQuickAddChoices.clear();
                    quickReorderAdapter.setFrequentPurchaseCounts(frequentPurchaseCounts);
                    quickReorderAdapter.setPreferredQuickAddChoices(preferredQuickAddChoices);
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Product p = doc.toObject(Product.class);
                        if (doc.contains("id")) p.setId(doc.getLong("id"));
                        quickReorderList.add(p);
                    }
                    loadVariantsForQuickReorder();
                });
    }

    private void loadVariantsForQuickReorder() {
        if (quickReorderList.isEmpty()) {
            quickReorderAdapter.notifyDataSetChanged();
            return;
        }
        AtomicInteger pending = new AtomicInteger(quickReorderList.size());
        for (Product p : quickReorderList) {
            if (p.getId() == null) {
                if (pending.decrementAndGet() == 0 && isAdded()) {
                    requireActivity().runOnUiThread(() -> quickReorderAdapter.notifyDataSetChanged());
                }
                continue;
            }
            apiService.getVariantsByProductId(p.getId())
                    .enqueue(new Callback<List<ProductVariant>>() {
                        @Override
                        public void onResponse(Call<List<ProductVariant>> call, Response<List<ProductVariant>> r) {
                            if (r.isSuccessful() && r.body() != null) {
                                p.setVariants(r.body());
                            }
                            if (pending.decrementAndGet() == 0 && isAdded()) {
                                requireActivity().runOnUiThread(() -> {
                                    quickReorderAdapter.setQuickReorderSubscriptionState(
                                            activeSubscriptionProductIds,
                                            subscriptionDiscountPercent
                                    );
                                    quickReorderAdapter.setPreferredQuickAddChoices(preferredQuickAddChoices);
                                    quickReorderAdapter.notifyDataSetChanged();
                                });
                            }
                        }

                        @Override
                        public void onFailure(Call<List<ProductVariant>> call, Throwable t) {
                            if (pending.decrementAndGet() == 0 && isAdded()) {
                                requireActivity().runOnUiThread(() -> {
                                    quickReorderAdapter.setQuickReorderSubscriptionState(
                                            activeSubscriptionProductIds,
                                            subscriptionDiscountPercent
                                    );
                                    quickReorderAdapter.setPreferredQuickAddChoices(preferredQuickAddChoices);
                                    quickReorderAdapter.notifyDataSetChanged();
                                });
                            }
                        }
                    });
        }
    }

    private void loadActiveSubscriptionState() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            activeSubscriptionProductIds.clear();
            if (quickReorderAdapter != null) {
                quickReorderAdapter.setQuickReorderSubscriptionState(activeSubscriptionProductIds, subscriptionDiscountPercent);
                quickReorderAdapter.setFrequentPurchaseCounts(frequentPurchaseCounts);
                quickReorderAdapter.setPreferredQuickAddChoices(preferredQuickAddChoices);
                quickReorderAdapter.notifyDataSetChanged();
            }
            return;
        }

        db.collection("grocery_subscriptions")
                .whereEqualTo("firebaseUid", user.getUid())
                .whereEqualTo("status", "ACTIVE")
                .get()
                .addOnSuccessListener(snapshot -> {
                    activeSubscriptionProductIds.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Object rawProductIds = doc.get("productIds");
                        if (rawProductIds instanceof List) {
                            for (Object value : (List<?>) rawProductIds) {
                                Long id = asLong(value);
                                if (id != null) {
                                    activeSubscriptionProductIds.add(id);
                                }
                            }
                        }
                    }
                    loadSubscriptionDiscountForQuickReorder();
                })
                .addOnFailureListener(e -> {
                    activeSubscriptionProductIds.clear();
                    if (quickReorderAdapter != null) {
                        quickReorderAdapter.setQuickReorderSubscriptionState(activeSubscriptionProductIds, subscriptionDiscountPercent);
                        quickReorderAdapter.setFrequentPurchaseCounts(frequentPurchaseCounts);
                        quickReorderAdapter.setPreferredQuickAddChoices(preferredQuickAddChoices);
                        quickReorderAdapter.notifyDataSetChanged();
                    }
                });
    }

    private void loadSubscriptionDiscountForQuickReorder() {
        db.collection("offers")
                .whereEqualTo("status", "active")
                .whereEqualTo("promoType", "subscription")
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    subscriptionDiscountPercent = 10.0;
                    if (!snapshot.isEmpty()) {
                        Double value = asDouble(snapshot.getDocuments().get(0).get("discountPercentage"));
                        if (value != null && value > 0) {
                            subscriptionDiscountPercent = value;
                        }
                    }
                    if (quickReorderAdapter != null) {
                        quickReorderAdapter.setQuickReorderSubscriptionState(activeSubscriptionProductIds, subscriptionDiscountPercent);
                        quickReorderAdapter.setFrequentPurchaseCounts(frequentPurchaseCounts);
                        quickReorderAdapter.setPreferredQuickAddChoices(preferredQuickAddChoices);
                        quickReorderAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    if (quickReorderAdapter != null) {
                        quickReorderAdapter.setQuickReorderSubscriptionState(activeSubscriptionProductIds, subscriptionDiscountPercent);
                        quickReorderAdapter.setFrequentPurchaseCounts(frequentPurchaseCounts);
                        quickReorderAdapter.setPreferredQuickAddChoices(preferredQuickAddChoices);
                        quickReorderAdapter.notifyDataSetChanged();
                    }
                });
    }

    @Nullable
    private Double asDouble(@Nullable Object value) {
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

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void afterTextChanged(Editable s) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                filterProducts(s.toString());
            }
        });

        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_NULL) {
                navigateToProductDetailsFromQuery(v.getText() == null ? "" : v.getText().toString());
                return true;
            }
            return false;
        });
    }

    private void filterProducts(String query) {
        List<Product> filtered = new ArrayList<>();
        for (Product p : fullProductList) {
            if (p.getName() != null &&
                    p.getName().toLowerCase().contains(query.toLowerCase()))
                filtered.add(p);
        }
        productAdapter.updateList(filtered);
    }

    private void navigateToProductDetailsFromQuery(@Nullable String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            return;
        }

        Long numericId = asLong(query);
        Product best = null;
        if (numericId != null) {
            for (Product product : searchableProductList) {
                if (product.getId() != null && numericId.equals(product.getId())) {
                    best = product;
                    break;
                }
            }
        }

        if (best == null) {
            for (Product product : searchableProductList) {
                boolean matchesName = product.getName() != null
                        && product.getName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
                boolean matchesSku = product.getSku() != null
                        && product.getSku().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
                if (matchesName || matchesSku) {
                    best = product;
                    break;
                }
            }
        }

        if (best != null) {
            openProductDetails(best);
            return;
        }

        CustomToast.showWarning(getContext(), "Product not found");
    }

    private boolean hasDealVariant(@Nullable Product product) {
        if (product == null || product.getVariants() == null) {
            return false;
        }

        for (ProductVariant variant : product.getVariants()) {
            if (variant != null && variant.hasDeal()) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private Map<Long, ProductAdapter.PreferredQuickAddChoice> buildPreferredQuickAddChoices(
            @NonNull Collection<FrequentVariantPattern> patterns) {
        Map<Long, FrequentVariantPattern> bestPatternByProduct = new HashMap<>();
        for (FrequentVariantPattern candidate : patterns) {
            FrequentVariantPattern current = bestPatternByProduct.get(candidate.productId);
            if (current == null || isBetterFrequentPattern(candidate, current)) {
                bestPatternByProduct.put(candidate.productId, candidate);
            }
        }

        Map<Long, ProductAdapter.PreferredQuickAddChoice> resolved = new HashMap<>();
        for (Map.Entry<Long, FrequentVariantPattern> entry : bestPatternByProduct.entrySet()) {
            FrequentVariantPattern pattern = entry.getValue();
            resolved.put(entry.getKey(), new ProductAdapter.PreferredQuickAddChoice(
                    pattern.variantId,
                    pattern.variantName,
                    Math.max(1, pattern.quantity),
                    Math.max(1, pattern.purchaseCount)
            ));
        }
        return resolved;
    }

    private boolean isBetterFrequentPattern(@NonNull FrequentVariantPattern candidate,
                                            @NonNull FrequentVariantPattern current) {
        if (candidate.purchaseCount != current.purchaseCount) {
            return candidate.purchaseCount > current.purchaseCount;
        }
        if (candidate.totalUnits != current.totalUnits) {
            return candidate.totalUnits > current.totalUnits;
        }
        if (candidate.quantity != current.quantity) {
            return candidate.quantity > current.quantity;
        }

        String candidateName = candidate.variantName == null ? "" : candidate.variantName.trim();
        String currentName = current.variantName == null ? "" : current.variantName.trim();
        return candidateName.compareToIgnoreCase(currentName) < 0;
    }

    @NonNull
    private String buildFrequentPatternKey(long productId,
                                           @Nullable Long variantId,
                                           @Nullable String variantName,
                                           int quantity) {
        return productId
                + "|"
                + (variantId != null ? "id:" + variantId : "name:" + normalizeVariantName(variantName))
                + "|qty:"
                + quantity;
    }

    @Nullable
    private String asString(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    @NonNull
    private String normalizeVariantName(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /* ── OFFERS LOGIC ─────────────────────────────────────────────────────── */

    private void listenForOffersRealtime() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        db.collection("user_offers")
            .whereEqualTo("firebaseUid", user.getUid())
            .addSnapshotListener((snapshot, error) -> {
                if (error != null || snapshot == null) return;
                claimedOfferIds.clear();
                for (QueryDocumentSnapshot doc : snapshot) {
                    Object offerIdObj = doc.get("offerId");
                    Long offerId = asLong(offerIdObj);
                    if (offerId != null) claimedOfferIds.add(offerId);
                }
                // Now listen for offers in real-time
                if (offerListenerRegistration != null) offerListenerRegistration.remove();
                offerListenerRegistration = db.collection("offers")
                    .whereEqualTo("status", "active")
                    .addSnapshotListener((offerSnap, offerErr) -> {
                        if (offerErr != null || offerSnap == null) return;
                        List<Offer> newOffers = new ArrayList<>();
                        Set<Long> newOfferIds = new HashSet<>();
                        for (QueryDocumentSnapshot doc : offerSnap) {
                            // Skip offers that have expired according to the expiryDate field in Firestore
                            try {
                                if (isDocumentOfferExpired(doc)) {
                                    continue;
                                }
                            } catch (Exception e) {
                                // If parsing fails, don't block showing the offer; log for debugging
                                Log.w("HOME_OFFERS", "Failed to parse expiryDate: " + e.getMessage());
                            }

                            Offer offer = doc.toObject(Offer.class);
                            if (doc.contains("id")) offer.setId(doc.getLong("id"));
                            if (offer.getId() != null && !claimedOfferIds.contains(offer.getId())) {
                                newOffers.add(offer);
                                newOfferIds.add(offer.getId());
                            }
                        }
                        // Detect new offers for notification
                        if (!isAdded()) return;
                        for (Offer offer : newOffers) {
                            if (!shownOfferIds.contains(offer.getId())) {
                                // Skip if user dismissed/cleared this offer previously
                                if (com.hansanie.greencart.util.NotificationHelper.isOfferDismissed(requireContext().getApplicationContext(), offer.getId())) {
                                    continue;
                                }
                                com.hansanie.greencart.util.NotificationHelper.showOfferNotificationIfNew(
                                    requireContext(),
                                    "New Offer Available!",
                                    offer.getTitle() != null ? offer.getTitle() : "Check out our latest offer!",
                                    offer.getId()
                                );
                            }
                        }
                        shownOfferIds.clear();
                        shownOfferIds.addAll(newOfferIds);
                        availableOffers.clear();
                        availableOffers.addAll(newOffers);
                        hasActiveOffer = !availableOffers.isEmpty();
                        requireActivity().runOnUiThread(this::showOfferCarousel);
                    });
            });
    }

    private void claimUserOffer(Offer offer) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || offer == null || offer.getId() == null) {
            CustomToast.showWarning(getContext(), "Please log in to claim this offer");
            return;
        }
        if (claimedOfferIds.contains(offer.getId())) {
            CustomToast.showInfo(getContext(), "You have already claimed this offer");
            return;
        }
        // Disable claim button (if possible)
        // Save claim to Firestore
        Map<String, Object> data = new HashMap<>();
        data.put("firebaseUid", user.getUid());
        data.put("offerId", offer.getId());
        data.put("promoCode", offer.getPromoCode());
        data.put("discount", offer.getDiscountPercentage());
        data.put("claimedAt", com.google.firebase.Timestamp.now());
        data.put("status", "ACTIVE");
        db.collection("user_offers")
            .add(data)
            .addOnSuccessListener(ref -> {
                CustomToast.showSuccess(getContext(), "Offer claimed! Check your rewards.");
            })
            .addOnFailureListener(e -> {
                CustomToast.showError(getContext(), "Failed to claim offer: " + e.getMessage());
            });
    }

    private void showOfferCarousel() {
        if (promoBannerContainer == null || offerBannerAdapter == null) return;
        if (availableOffers.isEmpty()) {
            promoBannerContainer.setVisibility(View.GONE);
            return;
        }
        // Update the adapter's data and notify
        offerBannerAdapter.submitOffers(availableOffers); // Use correct method name
        promoBannerContainer.setVisibility(View.VISIBLE);
        // Optionally update indicators
    }

    private void showNewOfferNotification(Offer offer) {
        Context context = getContext();
        if (context == null || offer == null) return;
        String channelId = "offers_channel";
        String title = "New Offer Available!";
        String message = offer.getTitle() != null ? offer.getTitle() : "Check out our latest offer!";
        android.app.NotificationManager notificationManager = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(channelId, "Offers", android.app.NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification) // Use a valid drawable icon
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true);
        notificationManager.notify((int) (offer.getId() != null ? offer.getId() : System.currentTimeMillis()), builder.build());
    }

    // Navigation method for banner 'Reveal Now' button
    private void navigateToShakeToWin(Offer offer) {
        ShakeToWinFragment fragment = new ShakeToWinFragment();
        Bundle args = new Bundle();
        if (offer != null) {
            args.putLong("offer_id", offer.getId() != null ? offer.getId() : -1L);
            args.putString("promo_code", offer.getPromoCode());
            args.putDouble("discount", offer.getDiscountPercentage() != null ? offer.getDiscountPercentage() : 0.0);
        }
        fragment.setArguments(args);
        requireActivity().getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit();
    }

    @Override
    public void onCategoryClick(Category category) {
        if (category == null) {
            return;
        }
        openProductCatalog(category.getId(), category.getName(), false, null);
    }

    private void openProductCatalog(@Nullable Long categoryId,
                                    @Nullable String categoryName,
                                    boolean dealsOnly,
                                    @Nullable String initialQuery) {
        if (!isAdded()) {
            return;
        }

        ProductFragment fragment = ProductFragment.newInstance(
                categoryId,
                categoryName,
                dealsOnly,
                initialQuery
        );

        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void buildOfferIndicators(int count) {
        if (layoutOfferIndicators == null) {
            return;
        }
        layoutOfferIndicators.removeAllViews();
        if (count <= 1) {
            layoutOfferIndicators.setVisibility(View.GONE);
            return;
        }
        layoutOfferIndicators.setVisibility(View.VISIBLE);
        for (int i = 0; i < count; i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(16, 6);
            params.setMargins(4, 0, 4, 0);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(i == 0 ? R.drawable.indicator_dot_active : R.drawable.indicator_dot_inactive);
            layoutOfferIndicators.addView(dot);
        }
    }

    private void updateOfferIndicators(int selected) {
        if (layoutOfferIndicators == null) {
            return;
        }
        for (int i = 0; i < layoutOfferIndicators.getChildCount(); i++) {
            View dot = layoutOfferIndicators.getChildAt(i);
            dot.setBackgroundResource(i == selected ? R.drawable.indicator_dot_active : R.drawable.indicator_dot_inactive);
            ViewGroup.LayoutParams lp = dot.getLayoutParams();
            lp.width = i == selected ? 24 : 16;
            dot.setLayoutParams(lp);
        }
    }

    private Long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Returns true if the Firestore document contains an expiryDate and it is before now.
     * Supports Timestamp, Date and several ISO-like string formats (with or without seconds).
     */
    private boolean isDocumentOfferExpired(DocumentSnapshot doc) {
        if (doc == null) return false;
        Object expiryObj = doc.get("expiryDate");
        if (expiryObj == null) return false;

        Date expiry = null;
        if (expiryObj instanceof com.google.firebase.Timestamp) {
            expiry = ((com.google.firebase.Timestamp) expiryObj).toDate();
        } else if (expiryObj instanceof Date) {
            expiry = (Date) expiryObj;
        } else if (expiryObj instanceof String) {
            expiry = parseIsoLikeDate((String) expiryObj);
        }

        if (expiry == null) return false;
        return expiry.getTime() < System.currentTimeMillis();
    }

    private Date parseIsoLikeDate(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        // Try a few common patterns: yyyy-MM-dd'T'HH:mm:ss, yyyy-MM-dd'T'HH:mm, yyyy-MM-dd HH:mm:ss, yyyy-MM-dd
        String[] patterns = new String[]{"yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"};
        for (String pat : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pat, Locale.getDefault());
                sdf.setLenient(false);
                return sdf.parse(raw);
            } catch (ParseException ignored) {
            }
        }
        // As a last resort, try parsing as millis
        try {
            long ts = Long.parseLong(raw);
            return new Date(ts);
        } catch (NumberFormatException ignored) {
        }
        return null;
    }
}

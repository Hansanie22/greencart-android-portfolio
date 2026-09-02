package com.hansanie.greencart.fragment;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.Timestamp;
import com.hansanie.greencart.R;
import com.hansanie.greencart.adapter.ImageSliderAdapter;
import com.hansanie.greencart.adapter.ProductAdapter;
import com.hansanie.greencart.adapter.ReviewAdapter;
import com.hansanie.greencart.dao.WishlistDao;
import com.hansanie.greencart.database.AppDatabase;
import com.hansanie.greencart.model.CartEntity;
import com.hansanie.greencart.model.Farm;
import com.hansanie.greencart.model.Product;
import com.hansanie.greencart.model.ProductVariant;
import com.hansanie.greencart.model.Review;
import com.hansanie.greencart.model.Wishlist;
import com.hansanie.greencart.network.ApiService;
import com.hansanie.greencart.network.CartManager;
import com.hansanie.greencart.network.RetrofitClient;
import com.hansanie.greencart.util.CustomToast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProductDetailsFragment extends Fragment {

    private static final String ARG_PRODUCT_ID = "product_id";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private String productId;

    // Views
    private ViewPager2 imagePager;
    private LinearLayout indicatorContainer;
    private MaterialToolbar toolbar;
    private TextView tvProductName, tvProductPrice, tvPriceUnit, tvQuantity,
            tvDescription, tvCategory, tvAvailability, tvRating;
    private TextView tvReviewCount;
    private TextView tvRatingSummaryValue, tvRatingSummaryCount, tvReviewsEmptyState;
    private TextView tvFiveStarPercent, tvFourStarPercent, tvThreeStarPercent, tvTwoStarPercent, tvOneStarPercent;
    private TextView tvNutritionTitle;
    private TextView tvFarmName, tvFarmAddress, txtFarmCertification;
    private ImageView imgFarmLogo;
    private com.google.android.gms.maps.MapView farmMapView;
    private com.google.android.gms.maps.GoogleMap farmGoogleMap;
    private com.google.android.material.button.MaterialButton btnFarmMyLocation;
    private com.google.android.gms.location.FusedLocationProviderClient fusedClient;
    private TextView tvOldPrice, tvDiscount;
    private ImageButton btnPlus, btnMinus, btnWishlist, btnShare;
    private MaterialButton btnAddToCart, btnBuyNow;
    private MaterialButton btnVisitFarm, btnCallFarm;
    private ChipGroup chipGroupWeight;
    private ChipGroup chipGroupSubscription;
    private ChipGroup farmCertChipGroup;
    private Chip chipWeekly;
    private Chip chipBiWeekly;
    private TextView tvSubscriptionPrice;
    private View subscriptionSection;
    private TextView tvSubscriptionStatus, tvPrice, subscriptionError;
    private Button btnSubscribe;
    private ProgressBar subscriptionProgress;
    private RecyclerView relatedProductsRecyclerView;
    private RecyclerView reviewsRecyclerView;
    private LinearLayout nutritionCardsContainer;
    private View flashDealBadge;
    private View discountContainer;
    private View ratingBreakdownLayout;
    private ProgressBar progressFiveStar, progressFourStar, progressThreeStar, progressTwoStar, progressOneStar;

    // State
    private int quantity = 1;
    private int maxStock = Integer.MAX_VALUE;
    private double unitPrice = 0.0;
    private Long numericId;
    private Long selectedVariantId;
    private String productName = "";
    private String productShareUrl = "";
    private String productCategory = null;
    private Long productCategoryId = null;
    private Farm currentFarm;

    private FirebaseFirestore db;
    private ApiService apiService;
    private FirebaseAuth mAuth;
    private List<ProductVariant> variantList = new ArrayList<>();
    private final List<Product> relatedProducts = new ArrayList<>();
    private final List<Review> reviewList = new ArrayList<>();
    // All reviews fetched from Firestore (kept in memory). We initially show a preview
    // (top 2) and expand to the full list when the user taps "View All".
    private final List<Review> allReviews = new ArrayList<>();
    private boolean showingAllReviews = false;
    // Reference to the "View All" button in the layout
    private View btnViewAllReviews;
    private final List<DocumentSnapshot> activeSubscriptionDocs = new ArrayList<>();
    private ProductAdapter relatedAdapter;
    private ReviewAdapter reviewAdapter;
    private boolean hasLoadedFirebaseReviewSummary = false;
    private boolean isCurrentSelectionSubscribed = false;
    @Nullable
    private String subscribedFrequency = null;

    public ProductDetailsFragment() {}

    public static ProductDetailsFragment newInstance(String productId) {
        ProductDetailsFragment fragment = new ProductDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PRODUCT_ID, productId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            productId = getArguments().getString(ARG_PRODUCT_ID);
        }
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        apiService = RetrofitClient.getApiService();
        try {
            numericId = Long.parseLong(productId);
        } catch (NumberFormatException e) {
            numericId = null;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_product_details, container, false);

        imagePager         = view.findViewById(R.id.productImagePager);
        indicatorContainer = view.findViewById(R.id.indicatorContainer);
        toolbar            = view.findViewById(R.id.toolbar);
        tvProductName   = view.findViewById(R.id.txtProductName);
        tvProductPrice  = view.findViewById(R.id.txtPrice);
        tvPriceUnit     = view.findViewById(R.id.txtPriceUnit);
        tvQuantity      = view.findViewById(R.id.tvQuantity);
        tvDescription   = view.findViewById(R.id.txtDescription);
        tvCategory      = view.findViewById(R.id.txtCategory);
        tvAvailability  = view.findViewById(R.id.txtAvailability);
        tvRating        = view.findViewById(R.id.txtRating);
        tvReviewCount   = view.findViewById(R.id.txtReviewCount);
        tvRatingSummaryValue = view.findViewById(R.id.txtRatingSummaryValue);
        tvRatingSummaryCount = view.findViewById(R.id.txtRatingSummaryCount);
        tvReviewsEmptyState = view.findViewById(R.id.txtReviewsEmptyState);
        tvFiveStarPercent = view.findViewById(R.id.txtFiveStarPercent);
        tvFourStarPercent = view.findViewById(R.id.txtFourStarPercent);
        tvThreeStarPercent = view.findViewById(R.id.txtThreeStarPercent);
        tvTwoStarPercent = view.findViewById(R.id.txtTwoStarPercent);
        tvOneStarPercent = view.findViewById(R.id.txtOneStarPercent);
        tvNutritionTitle = view.findViewById(R.id.txtNutritionTitle);
        nutritionCardsContainer = view.findViewById(R.id.nutritionCardsContainer);
        tvFarmName      = view.findViewById(R.id.txtFarmName);
        tvFarmAddress   = view.findViewById(R.id.txtFarmAddress);
        txtFarmCertification = view.findViewById(R.id.txtFarmCertification);
        imgFarmLogo     = view.findViewById(R.id.imgFarmLogo);
        farmMapView     = view.findViewById(R.id.farmMapView);
        if (farmMapView != null) {
            farmMapView.onCreate(savedInstanceState);
        }
        btnCallFarm     = view.findViewById(R.id.btnCallFarm);
        farmCertChipGroup = view.findViewById(R.id.farmCertChipGroup);
        tvOldPrice      = view.findViewById(R.id.txtOldPrice);
        tvDiscount      = view.findViewById(R.id.txtDiscount);
        chipGroupWeight = view.findViewById(R.id.chipGroupWeight);
        chipGroupSubscription = view.findViewById(R.id.chipGroupSubscription);
        chipWeekly = view.findViewById(R.id.chipWeekly);
        chipBiWeekly = view.findViewById(R.id.chipBiWeekly);
        tvSubscriptionPrice = view.findViewById(R.id.tvSubscriptionPrice);
        subscriptionSection = view.findViewById(R.id.subscriptionSection);
        tvSubscriptionStatus = view.findViewById(R.id.txtSubscriptionStatus);
        tvPrice = view.findViewById(R.id.tvPrice);
        subscriptionProgress = view.findViewById(R.id.subscriptionProgress);
        subscriptionError = view.findViewById(R.id.subscriptionError);
        relatedProductsRecyclerView = view.findViewById(R.id.relatedProductsRecyclerView);
        View btnViewAllProducts = view.findViewById(R.id.btnViewAllProducts);
        if (btnViewAllProducts != null) {
            btnViewAllProducts.setOnClickListener(v -> {
                // Open product listing. If this product has a category, pre-filter by it.
                Long catId = productCategoryId;
                String catName = hasText(productCategory) ? productCategory : "All";
                ProductFragment frag = ProductFragment.newInstance(catId, catName, false, null);
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, frag)
                        .addToBackStack(null)
                        .commit();
            });
        }
        reviewsRecyclerView = view.findViewById(R.id.reviewsRecyclerView);
        ratingBreakdownLayout = view.findViewById(R.id.layoutRatingBreakdown);
        progressFiveStar = view.findViewById(R.id.progressFiveStar);
        progressFourStar = view.findViewById(R.id.progressFourStar);
        progressThreeStar = view.findViewById(R.id.progressThreeStar);
        progressTwoStar = view.findViewById(R.id.progressTwoStar);
        progressOneStar = view.findViewById(R.id.progressOneStar);
        flashDealBadge  = view.findViewById(R.id.flashDealBadge);
        discountContainer = view.findViewById(R.id.discountContainer);
        btnVisitFarm = view.findViewById(R.id.btnVisitFarm);
        btnPlus         = view.findViewById(R.id.btnPlus);
        btnMinus        = view.findViewById(R.id.btnMinus);
        btnAddToCart    = view.findViewById(R.id.btnAddToCart);
        btnBuyNow       = view.findViewById(R.id.btnBuyNow);
        btnWishlist     = view.findViewById(R.id.btnWishlist);
        btnShare        = view.findViewById(R.id.btnShare);

        // btnSubscribe is hidden — subscription section is display-only
        // No subscribe/unsubscribe actions from product details page

        setupToolbar();
        setupQuantityPicker();
        setupCartButton();
        setupRelatedProducts();
        setupReviews();
        loadProductFromFirestore();
        loadProductDetailsFromApi();
        loadVariantsFromBackend();
        setupWishlistButton();
        setupFarmMapAction();
        setupReviewActions(view);
        loadActiveSubscriptionState();

        // Fused location client for centering the farm map
        fusedClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(requireActivity());


        if (chipGroupSubscription != null) {
            chipGroupSubscription.setOnCheckedStateChangeListener((group, checkedIds) -> {
                refreshSubscriptionStatusUi();
            });
        }

        return view;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUBSCRIPTION STATE — loads from Firestore using firebaseUid
    // Firestore structure:
    //   firebaseUid: "..."
    //   frequency: "WEEKLY"
    //   productIds: [6, 7, 3]          ← flat array, index-aligned with items[]
    //   items: [{name:"Brinjol", imageUrl:..., variantName:"500g"?, ...}]
    // ─────────────────────────────────────────────────────────────────────────

    private void loadActiveSubscriptionState() {
        activeSubscriptionDocs.clear();
        String uid = mAuth != null ? mAuth.getUid() : null;
        if (uid == null || numericId == null) {
            isCurrentSelectionSubscribed = false;
            subscribedFrequency = null;
            refreshSubscriptionStatusUi();
            return;
        }

        // Query using firebaseUid (camelCase — confirmed in Firestore screenshot)
        db.collection("grocery_subscriptions")
                .whereEqualTo("firebaseUid", uid)
                .whereEqualTo("status", "ACTIVE")
                .get()
                .addOnSuccessListener(snapshot -> {
                    activeSubscriptionDocs.clear();
                    activeSubscriptionDocs.addAll(snapshot.getDocuments());
                    Log.d("SUB_DEBUG", "Loaded " + activeSubscriptionDocs.size() + " active subscription docs");
                    resolveSubscriptionStateForCurrentSelection();
                    // Show subscription section ONLY when the selected variant is subscribed.
                    if (subscriptionSection != null) {
                        subscriptionSection.setVisibility(
                                isCurrentSelectionSubscribed ? View.VISIBLE : View.GONE);
                        Log.d("SUB_DEBUG", "Section visibility: " + isCurrentSelectionSubscribed);
                    }
                    refreshSubscriptionStatusUi();
                })
                .addOnFailureListener(e -> {
                    Log.e("SUB_DEBUG", "Failed to load subscriptions: " + e.getMessage());
                    activeSubscriptionDocs.clear();
                    isCurrentSelectionSubscribed = false;
                    subscribedFrequency = null;
                    refreshSubscriptionStatusUi();
                });
    }

    /**
     * Checks whether the currently displayed product (numericId) is in ANY of
     * the user's active subscription documents.
     *
     * Firestore document structure (confirmed from screenshot):
     *   - productIds: [6, 7, 3]   — flat numeric array  ← PRIMARY match path
     *   - items: [{name, imageUrl, variantName?, ...}]  — index-aligned with productIds
     *   - frequency: "WEEKLY"
     *
     * Variant name is resolved from items[i].variantName where productIds[i] == numericId.
     */
    private void resolveSubscriptionStateForCurrentSelection() {
        isCurrentSelectionSubscribed = false;
        subscribedFrequency = null;

        if (numericId == null || activeSubscriptionDocs.isEmpty()) {
            Log.d("SUB_DEBUG", "resolve: no docs or numericId null");
            return;
        }

        // normalizeVariantName removes all whitespace + uppercases so:
        // "500 g" → "500G",  "500g" → "500G",  "1 kg" → "1KG",  "1kg" → "1KG"
        // This makes Firestore "500 g" match the chip label "500g".
        String selectedVariantName = getSelectedVariantName();
        String selectedVariantNorm = normalizeVariantName(selectedVariantName);
        Log.d("SUB_DEBUG", "resolve: product=" + numericId
                + " selected='" + selectedVariantName + "' norm='" + selectedVariantNorm + "'");

        for (DocumentSnapshot doc : activeSubscriptionDocs) {
            String docFrequency = readString(doc, "frequency");
            Log.d("SUB_DEBUG", "  doc=" + doc.getId() + " freq=" + docFrequency);

            // ── PATH 1: productIds[] flat array (confirmed structure) ──────────
            // productIds[i] is index-aligned with items[i].variantName
            Object rawProductIds = doc.get("productIds");
            if (rawProductIds instanceof List) {
                List<?> pidList = (List<?>) rawProductIds;
                for (int i = 0; i < pidList.size(); i++) {
                    Long pid = asLong(pidList.get(i));
                    if (pid == null || !pid.equals(numericId)) continue;

                    String itemVariantName = getVariantNameFromItemsAt(doc, i);
                    String itemVariantNorm = normalizeVariantName(itemVariantName);
                    Log.d("SUB_DEBUG", "  PATH1 idx=" + i
                            + " itemVariant='" + itemVariantName + "' norm='" + itemVariantNorm + "'");

                    if (hasText(itemVariantNorm) && hasText(selectedVariantNorm)) {
                        // Normalised comparison: "500 g" == "500g" == "500G"
                        if (selectedVariantNorm.equals(itemVariantNorm)) {
                            isCurrentSelectionSubscribed = true;
                            subscribedFrequency = docFrequency;
                            Log.d("SUB_DEBUG", "  ✓ PATH1 variant match");
                            return;
                        }
                        // variantName stored but doesn't match this chip — keep scanning
                    } else {
                        // No variantName stored in items → product-level match
                        isCurrentSelectionSubscribed = true;
                        subscribedFrequency = docFrequency;
                        Log.d("SUB_DEBUG", "  ✓ PATH1 product-level match (no variantName)");
                        return;
                    }
                }
            }

            // ── PATH 2: items[] with embedded productId (legacy schema) ──────
            Object rawItems = doc.get("items");
            if (rawItems instanceof List) {
                for (Object item : (List<?>) rawItems) {
                    if (!(item instanceof Map)) continue;
                    Map<?, ?> map = (Map<?, ?>) item;

                    Long itemPid = asLong(firstNonNull(map.get("productId"), map.get("product_id")));
                    if (itemPid == null || !itemPid.equals(numericId)) continue;

                    Object rawVN = firstNonNull(map.get("variantName"), map.get("variant_name"));
                    String itemVariantNorm = normalizeVariantName(
                            rawVN != null ? String.valueOf(rawVN) : null);

                    if (hasText(itemVariantNorm) && hasText(selectedVariantNorm)) {
                        if (selectedVariantNorm.equals(itemVariantNorm)) {
                            isCurrentSelectionSubscribed = true;
                            subscribedFrequency = docFrequency;
                            Log.d("SUB_DEBUG", "  ✓ PATH2 embedded productId match");
                            return;
                        }
                    } else {
                        isCurrentSelectionSubscribed = true;
                        subscribedFrequency = docFrequency;
                        return;
                    }
                }
            }

            // ── PATH 3: top-level single productId ──────────────────────────
            Long topPid = asLong(firstNonNull(doc.get("productId"), doc.get("product_id")));
            if (topPid != null && topPid.equals(numericId)) {
                isCurrentSelectionSubscribed = true;
                subscribedFrequency = docFrequency;
                Log.d("SUB_DEBUG", "  ✓ PATH3 top-level productId match");
                return;
            }
        }

        Log.d("SUB_DEBUG", "resolve: no match → not subscribed");
    }

    /**
     * Safely reads items[index].variantName from a Firestore document.
     * Returns null if the array is missing, index is out of range, or
     * the variantName field is absent/blank.
     */
    @Nullable
    private String getVariantNameFromItemsAt(DocumentSnapshot doc, int index) {
        Object rawItems = doc.get("items");
        if (!(rawItems instanceof List)) return null;
        List<?> items = (List<?>) rawItems;
        if (index < 0 || index >= items.size()) return null;
        Object item = items.get(index);
        if (!(item instanceof Map)) return null;
        Map<?, ?> map = (Map<?, ?>) item;
        Object rawName = firstNonNull(map.get("variantName"), map.get("variant_name"));
        if (rawName == null) return null;
        String name = String.valueOf(rawName).trim();
        return name.isEmpty() ? null : name;
    }

    /**
     * Normalises a variant label for comparison by removing all whitespace
     * and converting to uppercase.
     * "500 g" → "500G",  "500g" → "500G",  "1 kg" → "1KG",  "2KG" → "2KG"
     * Returns empty string for null/blank input.
     */
    @NonNull
    private String normalizeVariantName(@Nullable String name) {
        if (name == null) return "";
        return name.replaceAll("\\s+", "").toUpperCase(Locale.getDefault());
    }

    @Nullable
    private Object firstNonNull(@Nullable Object first, @Nullable Object second) {
        return first != null ? first : second;
    }

    @Nullable
    private Long asLong(@Nullable Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try { return Long.parseLong(((String) value).trim()); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private boolean containsProductId(@Nullable Object rawProductIds, long targetProductId) {
        if (!(rawProductIds instanceof List)) return false;
        for (Object value : (List<?>) rawProductIds) {
            Long parsed = asLong(value);
            if (parsed != null && parsed == targetProductId) return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUBSCRIPTION UI
    // ─────────────────────────────────────────────────────────────────────────

    private void refreshSubscriptionStatusUi() {
        // Section must be visible first
        boolean sectionVisible = subscriptionSection != null
                && subscriptionSection.getVisibility() == View.VISIBLE;
        if (!sectionVisible) {
            if (tvSubscriptionStatus != null) tvSubscriptionStatus.setVisibility(View.GONE);
            if (btnSubscribe != null)         btnSubscribe.setVisibility(View.GONE);
            return;
        }

        // ── Subscribe button — always hidden (display-only card) ──
        if (btnSubscribe != null) btnSubscribe.setVisibility(View.GONE);

        // ── Update discounted price from current variant price ──
        if (tvSubscriptionPrice != null && unitPrice > 0) {
            tvSubscriptionPrice.setText(String.format(Locale.getDefault(),
                    "Rs. %.2f", unitPrice * 0.90));
        }

        if (isCurrentSelectionSubscribed) {
            // Highlight the correct frequency chip (read-only, non-clickable)
            selectFrequencyChipForSubscription(subscribedFrequency);
            setSubscriptionChipsEnabled(false);

            // Build status line: ✓ Active · 500 g · Weekly
            String variantLabel = getSelectedVariantName();
            String freqLabel    = getReadableFrequency(subscribedFrequency);
            StringBuilder status = new StringBuilder("✓ Active");
            if (hasText(variantLabel) && !"Default".equals(variantLabel)) {
                status.append(" · ").append(variantLabel);
            }
            if (hasText(freqLabel)) {
                status.append(" · ").append(freqLabel);
            }
            if (tvSubscriptionStatus != null) {
                tvSubscriptionStatus.setVisibility(View.VISIBLE);
                tvSubscriptionStatus.setText(status.toString());
            }
        } else {
            // No subscription for this variant — hide status, chips still shown (read-only)
            if (tvSubscriptionStatus != null) tvSubscriptionStatus.setVisibility(View.GONE);
            setSubscriptionChipsEnabled(false);
        }

        if (subscriptionProgress != null) subscriptionProgress.setVisibility(View.GONE);
        if (subscriptionError   != null) subscriptionError.setVisibility(View.GONE);
    }

    /** Selects the frequency chip matching the stored subscription frequency. */
    private void selectFrequencyChipForSubscription(@Nullable String frequency) {
        if (frequency == null) return;
        String norm = frequency.trim().toUpperCase(Locale.getDefault());
        if ("WEEKLY".equals(norm)) {
            if (chipWeekly != null) chipWeekly.setChecked(true);
        } else if ("BI_WEEKLY".equals(norm) || "BIWEEKLY".equals(norm)) {
            if (chipBiWeekly != null) chipBiWeekly.setChecked(true);
        }
    }

    /** Enables or disables frequency chips (read-only when already subscribed). */
    private void setSubscriptionChipsEnabled(boolean enabled) {
        if (chipWeekly != null)   chipWeekly.setClickable(enabled);
        if (chipBiWeekly != null) chipBiWeekly.setClickable(enabled);
        float alpha = enabled ? 1.0f : 0.85f;
        if (chipGroupSubscription != null) chipGroupSubscription.setAlpha(alpha);
    }

    /** Produces a reference code like "SUB-500G-WEEKLY". */
    private String buildSubscriptionCode(@Nullable String variantName, @Nullable String frequency) {
        if (!hasText(variantName) && !hasText(frequency)) return "";
        StringBuilder code = new StringBuilder("SUB");
        if (hasText(variantName)) {
            code.append("-").append(variantName.trim()
                    .toUpperCase(Locale.getDefault())
                    .replaceAll("\\s+", ""));
        }
        if (hasText(frequency)) {
            code.append("-").append(frequency.trim().toUpperCase(Locale.getDefault()));
        }
        return code.toString();
    }

    private String getReadableFrequency(@Nullable String frequency) {
        if (!hasText(frequency)) return "";
        switch (frequency.trim().toUpperCase(Locale.getDefault())) {
            case "DAILY":    return "Daily";
            case "WEEKLY":   return "Weekly";
            case "MONTHLY":  return "Monthly";
            case "BI_WEEKLY":
            case "BIWEEKLY": return "Bi-weekly";
            default:         return frequency;
        }
    }

    // Keep old method name for compatibility with any places it's called
    private String getReadableSubscriptionFrequency(@Nullable String frequency) {
        return getReadableFrequency(frequency);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SUBSCRIBE / UNSUBSCRIBE
    // ─────────────────────────────────────────────────────────────────────────

    private void bindSubscriptionEligibility(boolean eligible) {
        // Show subscription section ONLY if the currently selected variant is subscribed.
        // Pure display card — no subscribe/unsubscribe actions from product details page.
        if (subscriptionSection != null) {
            subscriptionSection.setVisibility(
                    isCurrentSelectionSubscribed ? View.VISIBLE : View.GONE);
        }
        refreshSubscriptionStatusUi();
    }

    private void handleSubscribe() {
        if (subscriptionProgress != null) subscriptionProgress.setVisibility(View.VISIBLE);
        if (subscriptionError != null) subscriptionError.setVisibility(View.GONE);
        btnSubscribe.setEnabled(false);

        String uid = mAuth != null ? mAuth.getUid() : null;
        if (uid == null || numericId == null) {
            showSubscriptionError("Missing user or product info");
            return;
        }

        Long variantId = selectedVariantId;
        if (variantId == null && !variantList.isEmpty()) {
            ProductVariant fallback = findInitialVariantSelection();
            if (fallback != null) {
                variantId = fallback.getId();
                selectedVariantId = variantId;
            }
        }
        if (variantId == null) {
            showSubscriptionError("Please select a product variant");
            return;
        }

        String frequency = getSelectedSubscriptionFrequency();
        if (frequency == null) {
            showSubscriptionError("Please select a frequency (Daily / Weekly / Monthly)");
            return;
        }

        String variantName = getSelectedVariantName();

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("firebaseUid", uid);
        data.put("productId", numericId);
        data.put("variantId", variantId);
        data.put("variantName", variantName);
        data.put("frequency", frequency);
        data.put("status", "ACTIVE");
        data.put("createdAt", System.currentTimeMillis());

        db.collection("grocery_subscriptions")
                .add(data)
                .addOnSuccessListener(docRef -> {
                    if (subscriptionProgress != null) subscriptionProgress.setVisibility(View.GONE);
                    btnSubscribe.setEnabled(true);
                    loadActiveSubscriptionState();
                })
                .addOnFailureListener(e -> showSubscriptionError("Failed to subscribe: " + e.getMessage()));
    }

    private void handleUnsubscribe() {
        if (subscriptionProgress != null) subscriptionProgress.setVisibility(View.VISIBLE);
        if (subscriptionError != null) subscriptionError.setVisibility(View.GONE);
        btnSubscribe.setEnabled(false);

        String uid = mAuth != null ? mAuth.getUid() : null;
        if (uid == null || numericId == null) {
            showSubscriptionError("Missing user or product info");
            return;
        }

        // Find the doc that contains this productId in productIds[]
        DocumentSnapshot toCancel = null;
        for (DocumentSnapshot doc : activeSubscriptionDocs) {
            if (containsProductId(doc.get("productIds"), numericId)) {
                toCancel = doc;
                break;
            }
            // Fallback: top-level productId
            Long pid = asLong(doc.get("productId"));
            if (pid != null && pid.equals(numericId)) {
                toCancel = doc;
                break;
            }
        }

        if (toCancel == null) {
            showSubscriptionError("No active subscription found");
            return;
        }

        db.collection("grocery_subscriptions").document(toCancel.getId())
                .update("status", "CANCELLED")
                .addOnSuccessListener(aVoid -> {
                    if (subscriptionProgress != null) subscriptionProgress.setVisibility(View.GONE);
                    btnSubscribe.setEnabled(true);
                    loadActiveSubscriptionState();
                })
                .addOnFailureListener(e -> showSubscriptionError("Failed to unsubscribe: " + e.getMessage()));
    }

    private void showSubscriptionError(String message) {
        if (subscriptionProgress != null) subscriptionProgress.setVisibility(View.GONE);
        if (subscriptionError != null) {
            subscriptionError.setText(message);
            subscriptionError.setVisibility(View.VISIBLE);
        }
        btnSubscribe.setEnabled(true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REST OF FRAGMENT (unchanged from previous version)
    // ─────────────────────────────────────────────────────────────────────────

    private void setupToolbar() {
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                }
            });
        }
        if (btnShare != null) {
            btnShare.setOnClickListener(v -> shareProduct());
        }
    }

    private void shareProduct() {
        String shareText = "🌿 Check out " + productName + " on GreenCart!\n"
                + "Price: Rs. " + String.format(Locale.getDefault(), "%.2f", unitPrice)
                + " / " + (tvPriceUnit != null ? tvPriceUnit.getText() : "")
                + "\n\nOrder fresh, organic groceries on GreenCart 🛒";
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "GreenCart — " + productName);
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(intent, "Share via"));
    }

    private void loadProductFromFirestore() {
        if (numericId == null) return;
        db.collection("products")
                .whereEqualTo("id", numericId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) return;
                    QueryDocumentSnapshot doc = (QueryDocumentSnapshot) querySnapshot.getDocuments().get(0);

                    productName = doc.getString("name") != null ? doc.getString("name") : "";
                    String description = doc.getString("description");
                    String category = doc.getString("category");
                    productCategory = category;
                    productCategoryId = doc.getLong("categoryId");
                    Object ratingObj = doc.get("rating");
                    List<String> images = (List<String>) doc.get("images");

                    Long farmId = doc.getLong("farmId");
                    if (farmId == null) farmId = doc.getLong("farm_id");

                    if (tvProductName != null) tvProductName.setText(productName);
                    if (tvDescription != null) tvDescription.setText(description);
                    if (tvCategory != null && category != null) tvCategory.setText(category.toUpperCase());
                    if (tvRating != null) tvRating.setText(ratingObj != null ? String.valueOf(ratingObj) : "0.0");
                    Object reviewCount = doc.get("reviewCount");
                    if (tvReviewCount != null) {
                        int totalReviews = reviewCount instanceof Number ? ((Number) reviewCount).intValue() : 0;
                        tvReviewCount.setText(String.format(Locale.getDefault(), "(%d reviews)", totalReviews));
                    }

                    if (images != null && !images.isEmpty()) {
                        productShareUrl = images.get(0);
                        setupImageSlider(images);
                    }

                    Product firestoreProduct = doc.toObject(Product.class);
                    if (farmId != null && firestoreProduct.getFarmId() == null) {
                        firestoreProduct.setFarmId(farmId);
                    }
                    bindProductDetails(firestoreProduct);
                })
                .addOnFailureListener(e -> Log.w("PRODUCT_DETAILS", "Firestore load failed: " + e.getMessage()));
    }

    private void loadProductDetailsFromApi() {
        if (numericId == null) return;
        apiService.getProductDetails(numericId).enqueue(new Callback<Product>() {
            @Override
            public void onResponse(Call<Product> call, Response<Product> response) {
                if (!isAdded() || !response.isSuccessful() || response.body() == null) return;
                bindProductDetails(response.body());
            }
            @Override
            public void onFailure(Call<Product> call, Throwable t) {
                Log.w("PRODUCT_DETAILS", "API fallback: " + t.getMessage());
            }
        });
    }

    private void bindProductDetails(Product product) {
        if (product == null) return;

        if (product.getName() != null && !product.getName().isEmpty()) {
            productName = product.getName();
            tvProductName.setText(productName);
        }
        if (product.getDescription() != null) tvDescription.setText(product.getDescription());
        if (product.getCategory() != null) {
            productCategory = product.getCategory();
            tvCategory.setText(productCategory.toUpperCase(Locale.getDefault()));
        }
        if (product.getCategoryId() != null) productCategoryId = product.getCategoryId();

        if (!hasLoadedFirebaseReviewSummary && product.getRating() != null && tvRating != null) {
            tvRating.setText(String.format(Locale.getDefault(), "%.1f", product.getRating()));
        }
        if (!hasLoadedFirebaseReviewSummary && product.getReviewCount() != null
                && tvReviewCount != null && product.getReviewCount() > 0) {
            tvReviewCount.setText(String.format(Locale.getDefault(), "(%d reviews)", product.getReviewCount()));
        }

        if (product.getImages() != null && !product.getImages().isEmpty()) {
            productShareUrl = product.getImages().get(0);
            setupImageSlider(product.getImages());
        }

        if (product.getNutrition() != null && !product.getNutrition().isEmpty()) {
            bindNutrition(product.getNutrition());
        }

        bindSubscriptionEligibility(product.isSubscriptionEligible());

        if (unitPrice <= 0 && product.getVariants() != null && !product.getVariants().isEmpty()) {
            ProductVariant initial = product.getVariants().get(0);
            if (initial != null) {
                unitPrice = initial.getEffectivePrice();
                if (tvProductPrice != null)
                    tvProductPrice.setText(String.format(Locale.getDefault(), "Rs. %.2f", unitPrice));
                if (tvPriceUnit != null)
                    tvPriceUnit.setText("/" + getDisplayVariantName(initial));
                bindDealState(initial);
                updateBuyNowButton();
            }
        }

        if (isFarmDataUsable(product.getFarmObject())) {
            bindFarm(product.getFarmObject());
        } else if (product.getFarmId() != null) {
            loadFarmDetailsFromApi(product.getFarmId());
        }

        loadRelatedProducts();
        loadReviews();
    }

    private void loadFarmDetailsFromApi(Long farmId) {
        if (farmId == null) return;
        apiService.getFarmById(farmId).enqueue(new Callback<Farm>() {
            @Override
            public void onResponse(Call<Farm> call, Response<Farm> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    bindFarm(response.body());
                } else {
                    loadFarmFromFirestore(farmId);
                }
            }
            @Override
            public void onFailure(Call<Farm> call, Throwable t) {
                loadFarmFromFirestore(farmId);
            }
        });
    }

    private void loadFarmFromFirestore(Long farmId) {
        db.collection("farms").whereEqualTo("id", farmId).limit(1).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        Farm farm = snapshot.getDocuments().get(0).toObject(Farm.class);
                        if (isFarmDataUsable(farm)) { bindFarm(farm); }
                    }
                })
                .addOnFailureListener(e -> Log.w("FARM_DETAILS", "Firestore farm load failed: " + e.getMessage()));
    }

    private boolean isFarmDataUsable(Farm farm) {
        if (farm == null) return false;
        return hasText(farm.getName()) || hasText(farm.getAddress())
                || (farm.getLatitude() != null && farm.getLongitude() != null);
    }

    private void bindFarm(Farm farm) {
        if (!isFarmDataUsable(farm)) return;
        currentFarm = farm;
        if (tvFarmName != null && hasText(farm.getName())) tvFarmName.setText(farm.getName());
        if (tvFarmAddress != null && hasText(farm.getAddress())) tvFarmAddress.setText(farm.getAddress());

        if (imgFarmLogo != null && hasText(farm.getImageUrl())) {
            imgFarmLogo.setColorFilter(null);
            Glide.with(this).load(farm.getImageUrl())
                    .placeholder(R.drawable.fresh).error(R.drawable.fresh)
                    .centerCrop().into(imgFarmLogo);
        }

        if (farmCertChipGroup != null) {
            farmCertChipGroup.removeAllViews();
            if (hasText(farm.getCertificationInfo())) {
                farmCertChipGroup.setVisibility(View.VISIBLE);
                for (String cert : farm.getCertificationInfo().split("[,\\n]")) {
                    String label = cert.trim();
                    if (label.isEmpty()) continue;
                    Chip chip = new Chip(requireContext());
                    chip.setText(label);
                    chip.setClickable(false);
                    chip.setCheckable(false);
                    chip.setChipBackgroundColorResource(android.R.color.transparent);
                    chip.setChipStrokeColorResource(R.color.md_theme_outlineVariant);
                    chip.setChipStrokeWidth(dp(1));
                    chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_primary));
                    farmCertChipGroup.addView(chip);
                }
            } else {
                farmCertChipGroup.setVisibility(View.GONE);
            }
        }

        if (btnCallFarm != null) {
            if (hasText(farm.getContactNumber())) {
                btnCallFarm.setVisibility(View.VISIBLE);
                btnCallFarm.setOnClickListener(v -> startActivity(
                        new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + farm.getContactNumber()))));
            } else {
                btnCallFarm.setVisibility(View.GONE);
            }
        }

        if (farmMapView != null && farm.getLatitude() != null && farm.getLongitude() != null) {
            farmMapView.setVisibility(View.VISIBLE);
            com.google.android.gms.maps.model.LatLng latLng =
                    new com.google.android.gms.maps.model.LatLng(farm.getLatitude(), farm.getLongitude());
            farmMapView.getMapAsync(googleMap -> {
                farmGoogleMap = googleMap;
                googleMap.getUiSettings().setScrollGesturesEnabled(true);
                googleMap.getUiSettings().setZoomGesturesEnabled(true);
                googleMap.getUiSettings().setRotateGesturesEnabled(false);
                googleMap.getUiSettings().setTiltGesturesEnabled(false);
                googleMap.getUiSettings().setMapToolbarEnabled(false);
                googleMap.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, 15f));
                googleMap.addMarker(new com.google.android.gms.maps.model.MarkerOptions()
                        .position(latLng)
                        .title(hasText(farm.getName()) ? farm.getName() : "Farm"));
                googleMap.setOnMapClickListener(clicked -> openMapIntent(farm));
                // Allow tapping the surrounding card area to open a full-screen map
                View card = getView() != null ? getView().findViewById(R.id.farmMapCard) : null;
                if (card != null) card.setOnClickListener(v -> openFullscreenMapForFarm(farm));

                // Fullscreen toggle button (overlay on the embedded map)
                ImageButton btnMapFullscreen = getView() != null ? getView().findViewById(R.id.btnMapFullscreen) : null;
                if (btnMapFullscreen != null) {
                    btnMapFullscreen.setOnClickListener(v -> openFullscreenMapForFarm(farm));
                }
                enableMyLocation();
                if (btnFarmMyLocation != null) {
                    btnFarmMyLocation.setOnClickListener(v -> {
                        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                                != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1003);
                            return;
                        }
                        try { googleMap.setMyLocationEnabled(true); } catch (SecurityException ignored) {}
                        fusedClient.getLastLocation().addOnSuccessListener(loc -> {
                            if (loc == null) { CustomToast.showInfo(getContext(), "Unable to get current location"); return; }
                            com.google.android.gms.maps.model.LatLng p = new com.google.android.gms.maps.model.LatLng(loc.getLatitude(), loc.getLongitude());
                            googleMap.animateCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(p, 16f));
                        });
                    });
                }
            });
        } else if (farmMapView != null) {
            farmMapView.setVisibility(View.GONE);
        }
    }

    private void openFullscreenMapForFarm(@NonNull Farm farm) {
        double[] lats = new double[]{farm.getLatitude()};
        double[] lons = new double[]{farm.getLongitude()};
        FullscreenMapFragment frag = FullscreenMapFragment.newInstance(lats, lons);
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, frag)
                .addToBackStack(null)
                .commit();
    }

    private void openMapIntent(Farm farm) {
        if (farm == null) return;
        String uri;
        if (farm.getLatitude() != null && farm.getLongitude() != null) {
            uri = String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)",
                    farm.getLatitude(), farm.getLongitude(),
                    farm.getLatitude(), farm.getLongitude(),
                    farm.getName() != null ? farm.getName() : "Farm");
        } else {
            uri = "geo:0,0?q=" + Uri.encode(farm.getAddress() != null ? farm.getAddress() : "Farm");
        }
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
    }

    private void setupFarmMapAction() {
        if (btnVisitFarm != null) {
            btnVisitFarm.setOnClickListener(v -> {
                if (currentFarm == null) {
                    CustomToast.showInfo(getContext(), "Farm location not available");
                    return;
                }
                openMapIntent(currentFarm);
            });
        }
    }

    private void enableMyLocation() {
        if (farmGoogleMap == null || !isAdded()) return;
        boolean fineGranted = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (fineGranted || coarseGranted) {
            try {
                farmGoogleMap.setMyLocationEnabled(true);
                farmGoogleMap.getUiSettings().setMyLocationButtonEnabled(true);
            } catch (SecurityException ignored) {}
            return;
        }
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, LOCATION_PERMISSION_REQUEST_CODE);
    }

    private void bindNutrition(Map<String, Object> nutrition) {
        if (nutritionCardsContainer == null) return;
        nutritionCardsContainer.removeAllViews();
        if (nutrition == null || nutrition.isEmpty()) { setNutritionSectionVisibility(false); return; }

        List<Map.Entry<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<String, Object> entry : nutrition.entrySet()) {
            if (entry.getKey() == null || entry.getKey().trim().isEmpty() || entry.getValue() == null) continue;
            entries.add(entry);
        }
        if (entries.isEmpty()) { setNutritionSectionVisibility(false); return; }

        Collections.sort(entries, Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));
        setNutritionSectionVisibility(true);

        for (int i = 0; i < entries.size(); i += 2) {
            LinearLayout row = createNutritionRow(i > 0);
            row.addView(createNutritionCard(entries.get(i).getKey(),
                    formatNutritionValue(entries.get(i).getValue()), true));
            if (i + 1 < entries.size()) {
                row.addView(createNutritionCard(entries.get(i + 1).getKey(),
                        formatNutritionValue(entries.get(i + 1).getValue()), false));
            } else {
                View spacer = new View(requireContext());
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
                row.addView(spacer);
            }
            nutritionCardsContainer.addView(row);
        }
    }

    private String formatNutritionValue(Object value) {
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            return Math.floor(d) == d ? String.valueOf((long) d)
                    : String.format(Locale.getDefault(), "%.2f", d);
        }
        return String.valueOf(value);
    }

    private void setNutritionSectionVisibility(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        if (tvNutritionTitle != null) tvNutritionTitle.setVisibility(v);
        if (nutritionCardsContainer != null) nutritionCardsContainer.setVisibility(v);
    }

    private LinearLayout createNutritionRow(boolean addTopMargin) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (addTopMargin) lp.topMargin = dp(12);
        row.setLayoutParams(lp);
        return row;
    }

    private MaterialCardView createNutritionCard(String title, String value, boolean isLeft) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (isLeft) lp.rightMargin = dp(8); else lp.leftMargin = dp(8);
        card.setLayoutParams(lp);
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.md_theme_surfaceContainerLowest));
        card.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.md_theme_outlineVariant));
        card.setStrokeWidth(dp(1));
        card.setCardElevation(0f);
        card.setRadius(dp(16));

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(android.view.Gravity.CENTER);
        content.setPadding(dp(18), dp(18), dp(18), dp(18));

        TextView titleView = new TextView(requireContext());
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        titleView.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_onSurfaceVariant));

        TextView valueView = new TextView(requireContext());
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.topMargin = dp(4);
        valueView.setLayoutParams(vp);
        valueView.setText(value);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        valueView.setTypeface(ResourcesCompat.getFont(requireContext(), R.font.poppins));
        valueView.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_onSurface));

        content.addView(titleView);
        content.addView(valueView);
        card.addView(content);
        return card;
    }

    private void loadVariantsFromBackend() {
        if (numericId == null) return;
        apiService.getVariantsByProductId(numericId).enqueue(new Callback<List<ProductVariant>>() {
            @Override
            public void onResponse(Call<List<ProductVariant>> call, Response<List<ProductVariant>> response) {
                if (isAdded() && response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    variantList = response.body();
                    ProductVariant initial = findInitialVariantSelection();
                    selectedVariantId = initial != null ? initial.getId() : null;
                    buildVariantChips();
                    if (initial != null) selectVariant(initial);
                }
            }
            @Override
            public void onFailure(Call<List<ProductVariant>> call, Throwable t) {}
        });
    }

    @Nullable
    private ProductVariant findInitialVariantSelection() {
        for (ProductVariant v : variantList) { if (v != null && v.isInStock()) return v; }
        return variantList.isEmpty() ? null : variantList.get(0);
    }

    private void buildVariantChips() {
        if (chipGroupWeight == null) return;
        chipGroupWeight.removeAllViews();
        for (int i = 0; i < variantList.size(); i++) {
            ProductVariant variant = variantList.get(i);
            Chip chip = new Chip(requireContext());
            chip.setText(hasText(variant.getVariantName()) ? variant.getVariantName() : "Default");
            chip.setCheckable(true);
            chip.setChecked(selectedVariantId != null
                    ? selectedVariantId.equals(variant.getId())
                    : i == 0);
            chip.setChipBackgroundColorResource(R.color.chip_background_selector);
            if (!variant.isInStock()) { chip.setAlpha(0.45f); chip.setEnabled(false); }
            chip.setOnCheckedChangeListener((btn, isChecked) -> {
                if (isChecked) {
                    quantity = 1;
                    if (tvQuantity != null) tvQuantity.setText("1");
                    selectVariant(variant);
                }
            });
            chipGroupWeight.addView(chip);
        }
    }

    private void selectVariant(ProductVariant variant) {
        selectedVariantId = variant.getId();
        unitPrice = variant.getEffectivePrice();
        Integer stock = variant.getResolvedStockCount();
        boolean inStock = variant.isInStock();
        maxStock = stock != null ? stock : (inStock ? Integer.MAX_VALUE : 0);

        if (tvProductPrice != null)
            tvProductPrice.setText(String.format(Locale.getDefault(), "Rs. %.2f", unitPrice));
        if (tvPriceUnit != null)
            tvPriceUnit.setText("/" + getDisplayVariantName(variant));
        if (tvPrice != null)
            tvPrice.setText(String.format(Locale.getDefault(), "Rs. %.2f /%s",
                    unitPrice, getDisplayVariantName(variant)));
        // Show 10% discounted price in subscription section
        if (tvSubscriptionPrice != null) {
            double discountedPrice = unitPrice * 0.90;
            tvSubscriptionPrice.setText(String.format(Locale.getDefault(),
                    "Rs. %.2f", discountedPrice));
        }

        bindDealState(variant);

        if (tvAvailability != null) {
            int primaryColor  = ContextCompat.getColor(requireContext(), R.color.md_theme_primary);
            int warningColor  = ContextCompat.getColor(requireContext(), R.color.toast_warning);
            int errorColor    = ContextCompat.getColor(requireContext(), R.color.md_theme_error);
            if (inStock) {
                String avail;
                if (stock != null && stock > 0 && stock < 10) avail = "Only " + stock + " left";
                else if (stock != null && stock > 0)          avail = stock + " available";
                else {
                    String lbl = variant.getResolvedAvailabilityLabel();
                    avail = hasText(lbl) ? lbl : "In Stock";
                }
                tvAvailability.setText(avail);
                tvAvailability.setTextColor(stock != null && stock > 0 && stock < 10 ? warningColor : primaryColor);
                if (btnAddToCart != null) btnAddToCart.setEnabled(true);
                if (btnBuyNow != null)    btnBuyNow.setEnabled(true);
            } else {
                String lbl = variant.getResolvedAvailabilityLabel();
                tvAvailability.setText(hasText(lbl) ? lbl : "Out of Stock");
                tvAvailability.setTextColor(errorColor);
                if (btnAddToCart != null) btnAddToCart.setEnabled(false);
                if (btnBuyNow != null)    btnBuyNow.setEnabled(false);
            }
        }

        resolveSubscriptionStateForCurrentSelection();
        // Show subscription section only if THIS variant is subscribed
        if (subscriptionSection != null) {
            subscriptionSection.setVisibility(
                    isCurrentSelectionSubscribed ? View.VISIBLE : View.GONE);
        }
        refreshSubscriptionStatusUi();
        updateBuyNowButton();
    }

    private void bindDealState(ProductVariant variant) {
        if (variant == null) return;
        boolean hasDeal = variant.hasDeal();
        if (flashDealBadge != null)   flashDealBadge.setVisibility(hasDeal ? View.VISIBLE : View.GONE);
        if (discountContainer != null) discountContainer.setVisibility(hasDeal ? View.VISIBLE : View.GONE);
        if (!hasDeal) return;

        Double oldPrice = variant.getComparableOriginalPrice();
        int discPct = variant.getResolvedDiscountPercent();
        if (tvOldPrice != null && oldPrice != null) {
            tvOldPrice.setText(String.format(Locale.getDefault(), "Rs. %.2f", oldPrice));
            tvOldPrice.setPaintFlags(tvOldPrice.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        }
        if (tvDiscount != null) {
            if (discPct > 0) tvDiscount.setText(String.format(Locale.getDefault(), "%d%% OFF", discPct));
            else if (variant.getDealTag() != null && !variant.getDealTag().isEmpty()) tvDiscount.setText(variant.getDealTag());
            else tvDiscount.setText("Deal");
        }
    }

    private void setupImageSlider(List<String> images) {
        if (!isAdded()) return;
        ImageSliderAdapter adapter = new ImageSliderAdapter(requireContext(), images);
        imagePager.setAdapter(adapter);
        imagePager.setPageTransformer(new ImageSliderAdapter.ZoomOutTransformer());
        buildDots(images.size());
        imagePager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { animateDots(position); }
        });
    }

    private void buildDots(int count) {
        if (indicatorContainer == null) return;
        indicatorContainer.removeAllViews();
        if (count <= 1) { indicatorContainer.setVisibility(View.GONE); return; }
        indicatorContainer.setVisibility(View.VISIBLE);
        for (int i = 0; i < count; i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(i == 0 ? dp(20) : dp(6), dp(6));
            lp.setMargins(dp(3), 0, dp(3), 0);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(i == 0 ? R.drawable.indicator_dot_active : R.drawable.indicator_dot_inactive);
            indicatorContainer.addView(dot);
        }
    }

    private void animateDots(int activeIndex) {
        if (indicatorContainer == null) return;
        for (int i = 0; i < indicatorContainer.getChildCount(); i++) {
            View dot = indicatorContainer.getChildAt(i);
            boolean isActive = i == activeIndex;
            int targetW = dp(isActive ? 20 : 6);
            dot.setBackgroundResource(isActive ? R.drawable.indicator_dot_active : R.drawable.indicator_dot_inactive);
            ValueAnimator anim = ValueAnimator.ofInt(dot.getLayoutParams().width, targetW);
            anim.setDuration(220);
            anim.addUpdateListener(va -> { dot.getLayoutParams().width = (int) va.getAnimatedValue(); dot.requestLayout(); });
            anim.start();
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private void setupQuantityPicker() {
        if (btnPlus != null) btnPlus.setOnClickListener(v -> {
            if (quantity < maxStock) {
                quantity++;
                tvQuantity.setText(String.valueOf(quantity));
                updateBuyNowButton();
            } else {
                CustomToast.showWarning(getContext(), "Max stock reached");
            }
        });
        if (btnMinus != null) btnMinus.setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                tvQuantity.setText(String.valueOf(quantity));
                updateBuyNowButton();
            }
        });
    }

    private void setupCartButton() {
        if (btnAddToCart != null) btnAddToCart.setOnClickListener(v -> ensureLoggedInAndRun(this::addCurrentSelectionToCart));
        if (btnBuyNow != null) btnBuyNow.setOnClickListener(v -> ensureLoggedInAndRun(() -> validateCurrentSelectionStock(() -> {
            CheckoutFragment fragment = CheckoutFragment.newBuyNowInstance(
                    numericId, getSelectedVariantId(), productName,
                    getSelectedVariantName(), unitPrice, quantity,
                    productShareUrl, isSubscriptionSelected(), getSelectedSubscriptionFrequency());
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null).commit();
        })));
    }

    private void addCurrentSelectionToCart() {
        validateCurrentSelectionStock(() -> {
            String uid = mAuth != null ? mAuth.getUid() : null;
            CartEntity entity = new CartEntity(
                    uid != null ? uid : "guest",
                    numericId, getSelectedVariantId(), productName,
                    getSelectedVariantName(), unitPrice, quantity,
                    productShareUrl, isSubscriptionSelected(), getSelectedSubscriptionFrequency());
            CartManager.addToCart(requireContext(), entity,
                    () -> CustomToast.showSuccess(getContext(), "Added to cart"));
        });
    }

    /**
     * Ensures the user is logged in before running the action. If not logged in,
     * navigates to the LoginFragment and shows an informational toast.
     */
    private void ensureLoggedInAndRun(@NonNull Runnable action) {
        String uid = mAuth != null ? mAuth.getUid() : null;
        if (uid == null) {
            CustomToast.showInfo(getContext(), "Please login to continue");
            LoginFragment login = new LoginFragment();
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, login)
                    .addToBackStack(null)
                    .commit();
            return;
        }
        action.run();
    }

    private void validateCurrentSelectionStock(@NonNull Runnable onValid) {
        if (numericId == null) { CustomToast.showWarning(getContext(), "Product unavailable"); return; }
        apiService.getVariantsByProductId(numericId).enqueue(new Callback<List<ProductVariant>>() {
            @Override
            public void onResponse(Call<List<ProductVariant>> call, Response<List<ProductVariant>> response) {
                if (!isAdded()) return;
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    if (maxStock > 0 && quantity <= maxStock) onValid.run();
                    else CustomToast.showWarning(getContext(), "Out of stock. Cannot continue");
                    return;
                }
                ProductVariant selected = findSelectedVariant(response.body());
                if (selected == null) { CustomToast.showWarning(getContext(), "Variant unavailable"); return; }
                Integer stock = selected.getResolvedStockCount();
                int available = stock != null ? Math.max(0, stock) : (selected.isInStock() ? Integer.MAX_VALUE : 0);
                if (available <= 0) { CustomToast.showWarning(getContext(), "Out of stock"); return; }
                if (available != Integer.MAX_VALUE && quantity > available) {
                    quantity = available;
                    if (tvQuantity != null) tvQuantity.setText(String.valueOf(quantity));
                    updateBuyNowButton();
                    CustomToast.showWarning(getContext(), "Only " + available + " left in stock");
                    return;
                }
                onValid.run();
            }
            @Override
            public void onFailure(Call<List<ProductVariant>> call, Throwable t) {
                if (!isAdded()) return;
                if (maxStock > 0 && quantity <= maxStock) onValid.run();
                else CustomToast.showWarning(getContext(), "Stock check failed. Try again");
            }
        });
    }

    @Nullable
    private ProductVariant findSelectedVariant(@NonNull List<ProductVariant> variants) {
        Long variantId = getSelectedVariantId();
        if (variantId != null) {
            for (ProductVariant v : variants) {
                if (v != null && variantId.equals(v.getId())) return v;
            }
        }
        String selectedName = getSelectedVariantName();
        for (ProductVariant v : variants) {
            if (v != null && v.getVariantName() != null
                    && v.getVariantName().trim().equalsIgnoreCase(selectedName)) return v;
        }
        return variants.get(0);
    }

    private boolean isSubscriptionSelected() {
        if (subscriptionSection == null || subscriptionSection.getVisibility() != View.VISIBLE) return false;
        return (chipWeekly != null && chipWeekly.isChecked())
                || (chipBiWeekly != null && chipBiWeekly.isChecked());
    }

    private String getSelectedSubscriptionFrequency() {
        if (!isSubscriptionSelected()) return null;
        if (chipWeekly != null && chipWeekly.isChecked())     return "WEEKLY";
        if (chipBiWeekly != null && chipBiWeekly.isChecked()) return "BI_WEEKLY";
        return null;
    }

    private String getSelectedVariantName() {
        if (chipGroupWeight != null) {
            for (int i = 0; i < chipGroupWeight.getChildCount(); i++) {
                Chip chip = (Chip) chipGroupWeight.getChildAt(i);
                if (chip.isChecked() && i < variantList.size())
                    return variantList.get(i).getVariantName();
            }
        }
        return "Default";
    }

    private Long getSelectedVariantId() {
        if (chipGroupWeight != null) {
            for (int i = 0; i < chipGroupWeight.getChildCount(); i++) {
                Chip chip = (Chip) chipGroupWeight.getChildAt(i);
                if (chip.isChecked() && i < variantList.size())
                    return variantList.get(i).getId();
            }
        }
        return null;
    }

    private void setupRelatedProducts() {
        if (relatedProductsRecyclerView == null) return;
        relatedProductsRecyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        relatedAdapter = new ProductAdapter(relatedProducts, false);
        relatedProductsRecyclerView.setAdapter(relatedAdapter);
        relatedAdapter.setOnItemClickListener(product -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer,
                            ProductDetailsFragment.newInstance(String.valueOf(product.getId())))
                    .addToBackStack(null).commit();
        });
    }

    private void setupReviews() {
        if (reviewsRecyclerView == null) return;
        reviewsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        reviewAdapter = new ReviewAdapter();
        reviewsRecyclerView.setAdapter(reviewAdapter);
        // Reset preview state each time we initialise the view
        allReviews.clear();
        reviewList.clear();
        showingAllReviews = false;
        updateReviewUiState(false);
        resetReviewSummaryCard();
    }

    private void setupReviewActions(View root) {
        btnViewAllReviews = root.findViewById(R.id.btnViewAllReviews);
        if (btnViewAllReviews != null) {
            btnViewAllReviews.setOnClickListener(v -> onViewAllReviewsClicked());
        }
    }

    private void loadReviews() {
        if (numericId == null || reviewAdapter == null) return;
        String pidText = String.valueOf(numericId);
        Task<QuerySnapshot> q1 = db.collection("reviews")
                .whereEqualTo("targetType", "product").whereEqualTo("productId", numericId).get();
        Task<QuerySnapshot> q2 = db.collection("reviews")
                .whereEqualTo("targetType", "product").whereEqualTo("productId", pidText).get();
        Tasks.whenAllSuccess(q1, q2).addOnSuccessListener(results -> {
            List<DocumentSnapshot> docs = mergeReviewDocuments(results);
            if (docs.isEmpty()) loadLegacyReviews(pidText);
            else renderReviews(docs);
        }).addOnFailureListener(e -> showReviewErrorState());
    }

    private void loadLegacyReviews(String pidText) {
        Task<QuerySnapshot> q1 = db.collection("reviews").whereEqualTo("productId", numericId).get();
        Task<QuerySnapshot> q2 = db.collection("reviews").whereEqualTo("productId", pidText).get();
        Tasks.whenAllSuccess(q1, q2)
                .addOnSuccessListener(r -> renderReviews(mergeReviewDocuments(r)))
                .addOnFailureListener(e -> showReviewErrorState());
    }

    @NonNull
    private List<DocumentSnapshot> mergeReviewDocuments(@NonNull List<Object> results) {
        Map<String, DocumentSnapshot> unique = new LinkedHashMap<>();
        for (Object r : results) {
            if (!(r instanceof QuerySnapshot)) continue;
            for (DocumentSnapshot doc : ((QuerySnapshot) r).getDocuments())
                unique.putIfAbsent(doc.getId(), doc);
        }
        List<DocumentSnapshot> list = new ArrayList<>(unique.values());
        list.sort((a, b) -> Long.compare(
                extractReviewCreatedAtMillis(b), extractReviewCreatedAtMillis(a)));
        return list;
    }

    private void renderReviews(@NonNull List<DocumentSnapshot> docs) {
        allReviews.clear();
        for (DocumentSnapshot doc : docs) {
            Review r = mapFirebaseReview(doc);
            if (r != null) allReviews.add(r);
        }

        // If there are no usable reviews, clear UI
        if (allReviews.isEmpty()) {
            reviewList.clear();
            reviewAdapter.submitList(new ArrayList<>(reviewList));
            updateReviewUiState(false);
            hasLoadedFirebaseReviewSummary = false;
            resetReviewSummaryCard();
            if (tvReviewCount != null) tvReviewCount.setText("(0 reviews)");
            return;
        }

        // Always compute the summary from the full set
        applyFirebaseReviewSummary(allReviews);

        // Show preview (top 2) unless already expanded
        List<Review> toShow;
        if (!showingAllReviews && allReviews.size() > 2) {
            toShow = new ArrayList<>(allReviews.subList(0, 2));
        } else {
            toShow = new ArrayList<>(allReviews);
        }
        reviewList.clear();
        reviewList.addAll(toShow);
        reviewAdapter.submitList(new ArrayList<>(reviewList));
        updateReviewUiState(!reviewList.isEmpty());

        // Update "View All" button visibility
        if (btnViewAllReviews != null) {
            btnViewAllReviews.setVisibility(allReviews.size() > 2 && !showingAllReviews ? View.VISIBLE : View.GONE);
            btnViewAllReviews.setEnabled(true);
        }
    }

    private void showReviewErrorState() {
        reviewList.clear();
        reviewAdapter.submitList(new ArrayList<>(reviewList));
        updateReviewUiState(false);
        hasLoadedFirebaseReviewSummary = false;
        resetReviewSummaryCard();
        if (tvReviewCount != null) tvReviewCount.setText("(0 reviews)");
    }

    /**
     * Called when the user taps "View All Reviews". Expands the preview to the full list
     * of reviews already fetched into `allReviews`.
     */
    private void onViewAllReviewsClicked() {
        if (showingAllReviews) return;
        if (allReviews.isEmpty()) return;
        // Simple UI feedback: disable button while we update
        if (btnViewAllReviews != null) btnViewAllReviews.setEnabled(false);
        showingAllReviews = true;
        reviewList.clear();
        reviewList.addAll(allReviews);
        reviewAdapter.submitList(new ArrayList<>(reviewList));
        updateReviewUiState(!reviewList.isEmpty());
        if (btnViewAllReviews != null) btnViewAllReviews.setVisibility(View.GONE);
    }

    private void updateReviewUiState(boolean hasReviews) {
        if (reviewsRecyclerView != null)
            reviewsRecyclerView.setVisibility(hasReviews ? View.VISIBLE : View.GONE);
        if (tvReviewsEmptyState != null)
            tvReviewsEmptyState.setVisibility(hasReviews ? View.GONE : View.VISIBLE);
        if (ratingBreakdownLayout != null)
            ratingBreakdownLayout.setVisibility(hasReviews ? View.VISIBLE : View.GONE);
    }

    private void resetReviewSummaryCard() {
        if (tvRatingSummaryValue != null) tvRatingSummaryValue.setText("0.0");
        if (tvRatingSummaryCount != null) tvRatingSummaryCount.setText("0 reviews");
        bindRatingBreakdown(0, 0, 0, 0, 0, 0);
    }

    @Nullable
    private Review mapFirebaseReview(@NonNull DocumentSnapshot doc) {
        Long pid = readLong(doc, "productId");
        if (pid == null || !pid.equals(numericId)) return null;

        String name = readString(doc, "reviewerName", "userName", "name");
        if (!hasText(name)) name = "GreenCart User";
        String initial = readString(doc, "reviewerInitial", "userInitial");
        if (!hasText(initial)) initial = name.substring(0, 1).toUpperCase(Locale.getDefault());
        String dateLabel = readString(doc, "reviewDateLabel");
        if (!hasText(dateLabel)) dateLabel = buildReviewDateLabel(doc.get("createdAt"));
        Double rating = readDouble(doc, "rating", "stars", "score");
        if (rating == null) rating = 0.0;
        String comment = readString(doc, "comment", "review", "message");
        if (!hasText(comment)) comment = "No written review provided.";
        Boolean verified = readBoolean(doc, "verifiedPurchase", "isVerifiedPurchase");
        Integer helpful = readInt(doc, "helpfulCount", "likes", "helpfulVotes");

        return Review.builder().id(doc.getId()).productId(pid)
                .farmId(readLong(doc, "farmId")).reviewerName(name).reviewerInitial(initial)
                .reviewDateLabel(hasText(dateLabel) ? dateLabel : "Recently")
                .rating(rating).comment(comment)
                .verifiedPurchase(Boolean.TRUE.equals(verified))
                .helpfulCount(helpful != null ? helpful : 0).build();
    }

    private void applyFirebaseReviewSummary(@NonNull List<Review> reviews) {
        if (reviews.isEmpty()) { hasLoadedFirebaseReviewSummary = false; resetReviewSummaryCard(); return; }
        double total = 0;
        for (Review r : reviews) total += r.getRating();
        hasLoadedFirebaseReviewSummary = true;
        double avg = total / reviews.size();
        if (tvRating != null) tvRating.setText(String.format(Locale.getDefault(), "%.1f", avg));
        if (tvReviewCount != null)
            tvReviewCount.setText(String.format(Locale.getDefault(), "(%d reviews)", reviews.size()));
        if (tvRatingSummaryValue != null)
            tvRatingSummaryValue.setText(String.format(Locale.getDefault(), "%.1f", avg));
        if (tvRatingSummaryCount != null)
            tvRatingSummaryCount.setText(String.format(Locale.getDefault(), "%d reviews", reviews.size()));

        int five = 0, four = 0, three = 0, two = 0, one = 0;
        for (Review r : reviews) {
            int rounded = (int) Math.round(r.getRating());
            if (rounded >= 5) five++;
            else if (rounded == 4) four++;
            else if (rounded == 3) three++;
            else if (rounded == 2) two++;
            else one++;
        }
        bindRatingBreakdown(five, four, three, two, one, reviews.size());
    }

    private void bindRatingBreakdown(int five, int four, int three, int two, int one, int total) {
        bindStar(progressFiveStar,  tvFiveStarPercent,  five,  total);
        bindStar(progressFourStar,  tvFourStarPercent,  four,  total);
        bindStar(progressThreeStar, tvThreeStarPercent, three, total);
        bindStar(progressTwoStar,   tvTwoStarPercent,   two,   total);
        bindStar(progressOneStar,   tvOneStarPercent,   one,   total);
    }

    private void bindStar(@Nullable ProgressBar bar, @Nullable TextView label, int count, int total) {
        int pct = total <= 0 ? 0 : Math.round(count * 100f / total);
        if (bar != null)   bar.setProgress(pct);
        if (label != null) label.setText(String.format(Locale.getDefault(), "%d%%", pct));
    }

    @NonNull
    private String buildReviewDateLabel(@Nullable Object createdAt) {
        long millis = extractReviewCreatedAtMillis(createdAt);
        if (millis <= 0) return "Recently";
        long days = Math.max(0, System.currentTimeMillis() - millis) / 86_400_000L;
        if (days <= 0) return "Today";
        if (days == 1)  return "Yesterday";
        if (days < 7)   return days + " days ago";
        if (days < 14)  return "Last week";
        if (days < 30)  return (days / 7) + " weeks ago";
        if (days < 60)  return "Last month";
        return (days / 30) + " months ago";
    }

    private long extractReviewCreatedAtMillis(@NonNull DocumentSnapshot doc) {
        return extractReviewCreatedAtMillis(doc.get("createdAt"));
    }

    private long extractReviewCreatedAtMillis(@Nullable Object v) {
        if (v instanceof Timestamp) return ((Timestamp) v).toDate().getTime();
        if (v instanceof Date)      return ((Date) v).getTime();
        if (v instanceof Number)    return ((Number) v).longValue();
        if (v instanceof String) {
            String t = ((String) v).trim();
            for (String pat : new String[]{
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'"}) {
                try {
                    Date d = new SimpleDateFormat(pat, Locale.getDefault()).parse(t);
                    if (d != null) return d.getTime();
                } catch (ParseException ignored) {}
            }
        }
        return 0L;
    }

    @Nullable
    private String readString(@NonNull DocumentSnapshot doc, @NonNull String... keys) {
        for (String k : keys) {
            Object v = doc.get(k);
            if (v != null) { String s = String.valueOf(v).trim(); if (!s.isEmpty()) return s; }
        }
        return null;
    }

    @Nullable
    private Long readLong(@NonNull DocumentSnapshot doc, @NonNull String... keys) {
        for (String k : keys) {
            Object v = doc.get(k);
            if (v instanceof Number) return ((Number) v).longValue();
            if (v instanceof String) {
                try { return Long.parseLong(((String) v).trim()); }
                catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    @Nullable
    private Integer readInt(@NonNull DocumentSnapshot doc, @NonNull String... keys) {
        Long v = readLong(doc, keys); return v == null ? null : v.intValue();
    }

    @Nullable
    private Double readDouble(@NonNull DocumentSnapshot doc, @NonNull String... keys) {
        for (String k : keys) {
            Object v = doc.get(k);
            if (v instanceof Number) return ((Number) v).doubleValue();
            if (v instanceof String) {
                try { return Double.parseDouble(((String) v).trim()); }
                catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    @Nullable
    private Boolean readBoolean(@NonNull DocumentSnapshot doc, @NonNull String... keys) {
        for (String k : keys) {
            Object v = doc.get(k);
            if (v instanceof Boolean) return (Boolean) v;
            if (v instanceof String) {
                String n = ((String) v).trim().toLowerCase(Locale.getDefault());
                if ("true".equals(n))  return true;
                if ("false".equals(n)) return false;
            }
        }
        return null;
    }

    @NonNull
    private String getDisplayVariantName(@NonNull ProductVariant variant) {
        return hasText(variant.getVariantName()) ? variant.getVariantName() : "Default";
    }

    private void loadRelatedProducts() {
        if (numericId == null || relatedAdapter == null) return;
        if (productCategoryId != null) { requestRelatedProductsByCategoryId(productCategoryId, true); return; }
        if (hasText(productCategory)) { requestRelatedProductsByCategoryName(productCategory, true); return; }
        loadRelatedProductsFromFirestore();
    }

    private void requestRelatedProductsByCategoryId(Long catId, boolean nameFallback) {
        apiService.getProductsByCategoryId(catId).enqueue(new Callback<List<Product>>() {
            @Override
            public void onResponse(Call<List<Product>> c, Response<List<Product>> r) {
                if (!isAdded()) return;
                if (r.isSuccessful() && applyRelatedProducts(r.body())) return;
                if (nameFallback && hasText(productCategory)) requestRelatedProductsByCategoryName(productCategory, true);
                else loadRelatedProductsFromFirestore();
            }
            @Override
            public void onFailure(Call<List<Product>> c, Throwable t) {
                if (nameFallback && hasText(productCategory)) requestRelatedProductsByCategoryName(productCategory, true);
                else loadRelatedProductsFromFirestore();
            }
        });
    }

    private void requestRelatedProductsByCategoryName(String cat, boolean firestoreFallback) {
        apiService.getProductsByCategoryName(cat).enqueue(new Callback<List<Product>>() {
            @Override
            public void onResponse(Call<List<Product>> c, Response<List<Product>> r) {
                if (!isAdded()) return;
                if (!r.isSuccessful() || !applyRelatedProducts(r.body()))
                    if (firestoreFallback) loadRelatedProductsFromFirestore();
            }
            @Override
            public void onFailure(Call<List<Product>> c, Throwable t) {
                if (firestoreFallback) loadRelatedProductsFromFirestore();
            }
        });
    }

    private void loadRelatedProductsFromFirestore() {
        Query q = db.collection("products");
        if (productCategoryId != null) q = q.whereEqualTo("categoryId", productCategoryId);
        else if (hasText(productCategory)) q = q.whereEqualTo("category", productCategory.trim());
        q.limit(20).get().addOnSuccessListener(snap -> {
            List<Product> list = new ArrayList<>();
            snap.getDocuments().forEach(d -> {
                Product p = d.toObject(Product.class);
                if (p == null) return;
                if (p.getId() == null) p.setId(d.getLong("id"));
                list.add(p);
            });
            if (!applyRelatedProducts(list)) relatedAdapter.notifyDataSetChanged();
        }).addOnFailureListener(e -> {
            relatedProducts.clear(); relatedAdapter.notifyDataSetChanged();
        });
    }

    private boolean applyRelatedProducts(List<Product> src) {
        relatedProducts.clear();
        if (src != null) {
            for (Product p : src) {
                if (p == null || p.getId() == null || p.getId().equals(numericId)) continue;
                relatedProducts.add(p);
                if (relatedProducts.size() >= 10) break;
            }
        }
        if (relatedProducts.isEmpty()) { relatedAdapter.notifyDataSetChanged(); return false; }
        loadRelatedProductPrices();
        return true;
    }

    private void loadRelatedProductPrices() {
        AtomicInteger pending = new AtomicInteger(relatedProducts.size());
        for (Product p : relatedProducts) {
            if (p.getId() == null) { if (pending.decrementAndGet() == 0) relatedAdapter.notifyDataSetChanged(); continue; }
            apiService.getVariantsByProductId(p.getId()).enqueue(new Callback<List<ProductVariant>>() {
                @Override
                public void onResponse(Call<List<ProductVariant>> c, Response<List<ProductVariant>> r) {
                    if (r.isSuccessful() && r.body() != null) p.setVariants(r.body());
                    if (pending.decrementAndGet() == 0) relatedAdapter.notifyDataSetChanged();
                }
                @Override
                public void onFailure(Call<List<ProductVariant>> c, Throwable t) {
                    if (pending.decrementAndGet() == 0) relatedAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    private void updateBuyNowButton() {
        if (btnBuyNow != null)
            btnBuyNow.setText(String.format(Locale.getDefault(), "Buy Now — Rs. %.2f", unitPrice * quantity));
    }

    private boolean hasText(String v) { return v != null && !v.trim().isEmpty(); }

    private void setupWishlistButton() {
        if (btnWishlist == null || numericId == null) return;
        String userId = mAuth.getUid();
        if (userId == null) { btnWishlist.setOnClickListener(v -> CustomToast.showInfo(getContext(), "Please login")); return; }
        Executors.newSingleThreadExecutor().execute(() -> {
            boolean fav = AppDatabase.getInstance(requireContext()).wishlistDao().isInWishlist(numericId, userId);
            if (isAdded()) requireActivity().runOnUiThread(() -> updateWishlistIcon(fav));
        });
        btnWishlist.setOnClickListener(v -> Executors.newSingleThreadExecutor().execute(() -> {
            WishlistDao dao = AppDatabase.getInstance(requireContext()).wishlistDao();
            boolean exists = dao.isInWishlist(numericId, userId);
            if (exists) { dao.deleteWishlist(new Wishlist(numericId, userId)); syncWishlistFirestore(numericId, false); }
            else         { dao.insertWishlist(new Wishlist(numericId, userId)); syncWishlistFirestore(numericId, true); }
            if (isAdded()) requireActivity().runOnUiThread(() -> updateWishlistIcon(!exists));
        }));
    }

    private void syncWishlistFirestore(Long pid, boolean add) {
        String uid = mAuth.getUid();
        if (uid == null) return;
        if (add) {
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("productId", pid);
            data.put("addedAt", System.currentTimeMillis());
            db.collection("wishlists").document(uid).collection("items")
                    .document(String.valueOf(pid)).set(data, SetOptions.merge());
        } else {
            db.collection("wishlists").document(uid).collection("items")
                    .document(String.valueOf(pid)).delete();
        }
    }

    private void updateWishlistIcon(boolean isFav) {
        if (btnWishlist == null || !isAdded()) return;
        btnWishlist.setImageResource(isFav ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
        btnWishlist.setColorFilter(requireContext().getColor(
                isFav ? R.color.md_theme_primary : R.color.md_theme_onSurface));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onResume() {
        super.onResume();
        if (farmMapView != null) farmMapView.onResume();
        loadActiveSubscriptionState();
    }

    @Override public void onPause() { super.onPause(); if (farmMapView != null) farmMapView.onPause(); }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (farmMapView != null) farmMapView.onDestroy();
        farmGoogleMap = null;
    }

    @Override
    public void onSaveInstanceState(@NonNull android.os.Bundle out) {
        super.onSaveInstanceState(out);
        if (farmMapView != null) farmMapView.onSaveInstanceState(out);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (farmMapView != null) farmMapView.onLowMemory();
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != LOCATION_PERMISSION_REQUEST_CODE) return;
        boolean granted = false;
        for (int r : results) if (r == PackageManager.PERMISSION_GRANTED) { granted = true; break; }
        if (granted) enableMyLocation();
        else CustomToast.showInfo(getContext(), "Location permission denied");
    }
}
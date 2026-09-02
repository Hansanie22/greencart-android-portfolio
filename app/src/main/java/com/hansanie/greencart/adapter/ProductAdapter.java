package com.hansanie.greencart.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import androidx.fragment.app.FragmentActivity;
import com.hansanie.greencart.fragment.LoginFragment;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.hansanie.greencart.R;
import com.hansanie.greencart.dao.WishlistDao;
import com.hansanie.greencart.database.AppDatabase;
import com.hansanie.greencart.model.CartEntity;
import com.hansanie.greencart.model.Product;
import com.hansanie.greencart.model.ProductVariant;
import com.hansanie.greencart.model.Wishlist;
import com.hansanie.greencart.network.CartManager;
import com.hansanie.greencart.util.CustomToast;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

    private static final int MAX_QUICK_ADD_QTY = 20;

    public static class PreferredQuickAddChoice {
        @Nullable public final Long variantId;
        @Nullable public final String variantName;
        public final int quantity;
        public final int purchaseCount;

        public PreferredQuickAddChoice(@Nullable Long variantId,
                                       @Nullable String variantName,
                                       int quantity,
                                       int purchaseCount) {
            this.variantId = variantId;
            this.variantName = variantName;
            this.quantity = quantity;
            this.purchaseCount = purchaseCount;
        }
    }

    private static class ResolvedQuickAddChoice {
        @Nullable final ProductVariant variant;
        @NonNull final String variantName;
        final int quantity;
        final int purchaseCount;

        ResolvedQuickAddChoice(@Nullable ProductVariant variant,
                               @NonNull String variantName,
                               int quantity,
                               int purchaseCount) {
            this.variant = variant;
            this.variantName = variantName;
            this.quantity = quantity;
            this.purchaseCount = purchaseCount;
        }
    }

    private List<Product> products;
    private int quantity = 1;
    private OnItemClickListener listener;
    private boolean isQuickReorder;
    private Set<Long> quickReorderSubscribedProductIds = new HashSet<>();
    private Map<Long, Integer> frequentPurchaseCounts = new HashMap<>();
    private Map<Long, PreferredQuickAddChoice> preferredQuickAddChoices = new HashMap<>();
    private double quickReorderDiscountPercent = 10.0;
    public interface OnItemClickListener {
        void onItemClick(Product product);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public ProductAdapter(List<Product> products) {
        this.products = products;
        this.isQuickReorder = false;
    }

    public ProductAdapter(List<Product> products, boolean isQuickReorder) {
        this.products = products;
        this.isQuickReorder = isQuickReorder;
    }

    public void setQuickReorderSubscriptionState(Set<Long> subscribedProductIds, double discountPercent) {
        if (subscribedProductIds == null) {
            quickReorderSubscribedProductIds = new HashSet<>();
        } else {
            quickReorderSubscribedProductIds = new HashSet<>(subscribedProductIds);
        }
        quickReorderDiscountPercent = discountPercent > 0 ? discountPercent : 10.0;
    }

    public void setFrequentPurchaseCounts(@Nullable Map<Long, Integer> purchaseCounts) {
        if (purchaseCounts == null) {
            frequentPurchaseCounts = new HashMap<>();
            return;
        }
        frequentPurchaseCounts = new HashMap<>(purchaseCounts);
    }

    public void setPreferredQuickAddChoices(@Nullable Map<Long, PreferredQuickAddChoice> preferredChoices) {
        if (preferredChoices == null) {
            preferredQuickAddChoices = new HashMap<>();
            return;
        }
        preferredQuickAddChoices = new HashMap<>(preferredChoices);
    }

    public void updateList(List<Product> newList) {
        this.products = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId;
        if (isQuickReorder) {
            layoutId = R.layout.item_quick_reorder;
        } else {
            RecyclerView.LayoutManager layoutManager = ((RecyclerView) parent).getLayoutManager();
            boolean isHorizontalList = layoutManager instanceof LinearLayoutManager
                    && ((LinearLayoutManager) layoutManager).getOrientation() == RecyclerView.HORIZONTAL;
            layoutId = isHorizontalList ? R.layout.item_product_related : R.layout.item_product;
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Product product = products.get(position);
        holder.productName.setText(product.getName());

        double price = 0.0;
        String variantDisplayName = "";
        ResolvedQuickAddChoice preferredChoice = isQuickReorder ? resolvePreferredQuickAddChoice(product) : null;
        ProductVariant displayVariant = preferredChoice != null ? preferredChoice.variant : null;
        ProductVariant firstInStockVariant = getFirstInStockVariant(product);
        if (displayVariant == null && firstInStockVariant != null) {
            displayVariant = firstInStockVariant;
        } else if (displayVariant == null && product.getVariants() != null && !product.getVariants().isEmpty()) {
            displayVariant = product.getVariants().get(0);
        }

        if (displayVariant != null) {
            price = displayVariant.getEffectivePrice();
            variantDisplayName = getReadableVariantName(product, displayVariant);
        } else if (preferredChoice != null) {
            variantDisplayName = preferredChoice.variantName;
        } else {
            variantDisplayName = getReadableVariantName(product, null);
        }

        boolean isSubscribedQuickItem = isQuickReorder
                && product.getId() != null
                && quickReorderSubscribedProductIds.contains(product.getId());
        // Bugfix: show the real/original price on the quick-reorder cards (frequently ordered list)
        // Previously we displayed the subscription-discounted price here which confused users.
        // Keep the subscription state for behavior (e.g. add-to-cart), but display the base price.
        double displayPrice = price;

        if (isQuickReorder) {
            holder.productPrice.setText(String.format(Locale.getDefault(), "Rs. %.0f", displayPrice));
        } else {
            holder.productPrice.setText("LKR " + (int) displayPrice);
        }

        if (holder.quickTag != null) {
            if (isSubscribedQuickItem) {
                holder.quickTag.setText("Subscribed");
            } else {
                int boughtCount = preferredChoice != null && preferredChoice.purchaseCount > 0
                        ? preferredChoice.purchaseCount
                        : getFrequentCount(product);
                holder.quickTag.setText(boughtCount > 0 ? "Bought x" + boughtCount : "Frequent");
            }
        }
        String categoryName = (product.getCategoryObject() != null) ? product.getCategoryObject().getName() : "Fresh";
        boolean outOfStock = !hasInStockVariant(product);

        if (holder.itemCount != null) {
            if (outOfStock) {
                holder.itemCount.setText("Out of Stock");
            } else {
                holder.itemCount.setText(categoryName + " • " + variantDisplayName);
            }
        }

        if (holder.badgeDeal != null || holder.badgeNewest != null) {
            ProductVariant firstVariant = product.getVariants() != null && !product.getVariants().isEmpty()
                    ? product.getVariants().get(0)
                    : null;

            boolean showDeal = Boolean.TRUE.equals(product.getDealLabel())
                    || (firstVariant != null && firstVariant.hasDeal());
            boolean showNewest = Boolean.TRUE.equals(product.getNewestLabel())
                    || isNewestStatus(product.getStatus());

            if (holder.badgeDeal != null) {
                holder.badgeDeal.setVisibility(showDeal ? View.VISIBLE : View.GONE);
            }
            if (holder.badgeNewest != null) {
                holder.badgeNewest.setVisibility(showNewest ? View.VISIBLE : View.GONE);
            }
        }

        Glide.with(holder.itemView.getContext())
                .load(product.getPrimaryImage())
                .placeholder(R.drawable.fresh)
                .error(R.drawable.fresh)
                .into(holder.productImage);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null && holder.getAdapterPosition() != RecyclerView.NO_POSITION) {
                listener.onItemClick(products.get(holder.getAdapterPosition()));
            }
        });

        if (holder.btnWishlist != null) {
            setupWishlist(holder, product);
        }

        if (holder.btnQuickAdd != null) {
            double finalPriceForSheet = price;
            holder.btnQuickAdd.setEnabled(!outOfStock);
            holder.btnQuickAdd.setAlpha(outOfStock ? 0.45f : 1f);
            holder.btnQuickAdd.setOnClickListener(v -> {
                if (!hasInStockVariant(product)) {
                    CustomToast.showWarning(holder.itemView.getContext(), "Out of stock. Cannot add to cart");
                    return;
                }

                ResolvedQuickAddChoice latestPreferredChoice = isQuickReorder
                        ? resolvePreferredQuickAddChoice(product)
                        : null;

                if (isQuickReorder && latestPreferredChoice != null && latestPreferredChoice.variant != null) {
                    Integer stock = resolveVariantStock(latestPreferredChoice.variant);
                    if (stock != null && stock <= 0) {
                        showAddToCartSheet(holder, product, finalPriceForSheet);
                        return;
                    }

                    addPreferredQuickReorderToCart(holder, product, latestPreferredChoice);
                    return;
                }

                showAddToCartSheet(holder, product, finalPriceForSheet);
            });
        }
    }

    private void addPreferredQuickReorderToCart(@NonNull ViewHolder holder,
                                                @NonNull Product product,
                                                @NonNull ResolvedQuickAddChoice preferredChoice) {
        Integer stock = resolveVariantStock(preferredChoice.variant);
        int requestedQty = Math.max(1, preferredChoice.quantity);
        if (stock != null && stock <= 0) {
            CustomToast.showWarning(holder.itemView.getContext(), "Out of stock. Cannot add to cart");
            return;
        }
        if (stock != null && requestedQty > stock) {
            CustomToast.showWarning(holder.itemView.getContext(), "Only " + stock + " left in stock");
            return;
        }

        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) {
            CustomToast.showInfo(holder.itemView.getContext(), "Please login to continue");
            if (holder.itemView.getContext() instanceof FragmentActivity) {
                FragmentActivity fa = (FragmentActivity) holder.itemView.getContext();
                fa.getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new LoginFragment())
                        .addToBackStack(null)
                        .commit();
            }
            return;
        }

        boolean isSubscribedQuickItem = product.getId() != null
                && quickReorderSubscribedProductIds.contains(product.getId());

        double price = preferredChoice.variant != null
                ? preferredChoice.variant.getEffectivePrice()
                : 0.0;

        CartEntity entity = new CartEntity(
                userId,
                product.getId() != null ? product.getId() : 0L,
                preferredChoice.variant != null ? preferredChoice.variant.getId() : null,
                product.getName(),
                preferredChoice.variantName,
                price,
                requestedQty,
                product.getPrimaryImage() != null ? product.getPrimaryImage() : "",
                isSubscribedQuickItem,
                isSubscribedQuickItem ? "WEEKLY" : null
        );

        CartManager.addToCart(holder.itemView.getContext(), entity, () ->
                CustomToast.showSuccess(
                        holder.itemView.getContext(),
                        "Added " + requestedQty + " x " + preferredChoice.variantName + " to cart"
                ));
    }

    private void showAddToCartSheet(ViewHolder holder, Product product, double initialPrice) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(holder.itemView.getContext(), R.style.BottomSheetDialogTheme);
        View sheetView = LayoutInflater.from(holder.itemView.getContext()).inflate(R.layout.layout_quick_add_sheet, null);

        TextView sheetProductName = sheetView.findViewById(R.id.sheetProductName);
        TextView tvQuantity = sheetView.findViewById(R.id.tvQuantity);
        TextView tvSheetPrice = sheetView.findViewById(R.id.tvSheetPrice);
        ImageButton btnPlus = sheetView.findViewById(R.id.btnPlus);
        ImageButton btnMinus = sheetView.findViewById(R.id.btnMinus);
        MaterialButton btnConfirmAdd = sheetView.findViewById(R.id.btnConfirmAdd);
        ChipGroup chipGroupVariants = sheetView.findViewById(R.id.chipGroupVariants);

        quantity = 1;
        ResolvedQuickAddChoice preferredChoice = isQuickReorder ? resolvePreferredQuickAddChoice(product) : null;
        if (preferredChoice != null && preferredChoice.quantity > 1) {
            quantity = Math.min(preferredChoice.quantity, MAX_QUICK_ADD_QTY);
        }
        sheetProductName.setText(product.getName());
        tvQuantity.setText(String.valueOf(quantity));

        final double[] selectedPrice = {initialPrice};
        final int[] selectedVariantIndex = {-1};
        final int[] selectedVariantStock = {-1};

        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            for (int i = 0; i < product.getVariants().size(); i++) {
                ProductVariant variant = product.getVariants().get(i);
                Chip chip = new Chip(holder.itemView.getContext());
                String readableVariantName = getReadableVariantName(product, variant);
                chip.setText(readableVariantName);
                chip.setCheckable(true);
                chip.setChipBackgroundColorResource(R.color.chip_background_selector);

                if (variant.getStock() != null && variant.getStock() <= 0) {
                    chip.setAlpha(0.4f);
                    chip.setEnabled(false);
                    chip.setText(readableVariantName + " (Out of Stock)");
                }

                final double variantPrice = variant.getEffectivePrice();
                final int variantIndex = i;
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedVariantIndex[0] = variantIndex;
                        selectedPrice[0] = variantPrice;
                        selectedVariantStock[0] = resolveStockLimit(product.getVariants().get(variantIndex));
                        if (selectedVariantStock[0] > 0 && quantity > selectedVariantStock[0]) {
                            quantity = selectedVariantStock[0];
                            tvQuantity.setText(String.valueOf(quantity));
                        }
                        tvSheetPrice.setText("LKR " + (int) variantPrice);
                        btnConfirmAdd.setText("Add to Cart • LKR " + (int) (variantPrice * quantity));
                    }
                });
                chipGroupVariants.addView(chip);

                boolean shouldSelect = preferredChoice != null
                        ? matchesPreferredChoice(product, variant, preferredChoice)
                        : i == 0;
                chip.setChecked(shouldSelect);
            }
        }

        if (selectedVariantIndex[0] == -1 && chipGroupVariants.getChildCount() > 0) {
            Chip firstChip = (Chip) chipGroupVariants.getChildAt(0);
            firstChip.setChecked(true);
        }
        if (selectedVariantIndex[0] >= 0 && selectedVariantIndex[0] < product.getVariants().size()) {
            selectedVariantStock[0] = resolveStockLimit(product.getVariants().get(selectedVariantIndex[0]));
        }

        tvSheetPrice.setText("LKR " + (int) selectedPrice[0]);
        btnConfirmAdd.setText("Add to Cart • LKR " + (int) (selectedPrice[0] * quantity));

        btnPlus.setOnClickListener(v -> {
            if (selectedVariantStock[0] == 0) {
                CustomToast.showWarning(holder.itemView.getContext(), "Out of stock. Cannot add to cart");
                return;
            }
            if (selectedVariantStock[0] > 0 && quantity >= selectedVariantStock[0]) {
                CustomToast.showWarning(holder.itemView.getContext(), "Only " + selectedVariantStock[0] + " left in stock");
                return;
            }
            quantity = Math.min(quantity + 1, MAX_QUICK_ADD_QTY);
            tvQuantity.setText(String.valueOf(quantity));
            btnConfirmAdd.setText("Add to Cart • LKR " + (int) (selectedPrice[0] * quantity));
        });

        btnMinus.setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                tvQuantity.setText(String.valueOf(quantity));
                btnConfirmAdd.setText("Add to Cart • LKR " + (int) (selectedPrice[0] * quantity));
            }
        });

        btnConfirmAdd.setOnClickListener(v -> {
            String userId = FirebaseAuth.getInstance().getUid();
            if (userId == null) {
                CustomToast.showInfo(holder.itemView.getContext(), "Please login to continue");
                if (holder.itemView.getContext() instanceof FragmentActivity) {
                    FragmentActivity fa = (FragmentActivity) holder.itemView.getContext();
                    fa.getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragmentContainer, new LoginFragment())
                            .addToBackStack(null)
                            .commit();
                }
                return;
            }

            boolean isSubscribedQuickItem = isQuickReorder
                    && product.getId() != null
                    && quickReorderSubscribedProductIds.contains(product.getId());

            String variantName = "Default";
            Long variantId = null;
            int variantStockLimit = -1;
            if (product.getVariants() != null && !product.getVariants().isEmpty()) {
                for (int i = 0; i < chipGroupVariants.getChildCount(); i++) {
                    Chip c = (Chip) chipGroupVariants.getChildAt(i);
                    if (c.isChecked() && i < product.getVariants().size()) {
                        ProductVariant selectedVariant = product.getVariants().get(i);
                        variantId = selectedVariant.getId();
                        variantName = getReadableVariantName(product, selectedVariant);
                        variantStockLimit = resolveStockLimit(selectedVariant);
                        break;
                    }
                }
            } else {
                variantName = getReadableVariantName(product, null);
            }

            if (variantStockLimit == 0) {
                CustomToast.showWarning(holder.itemView.getContext(), "Out of stock. Cannot add to cart");
                return;
            }
            if (variantStockLimit > 0 && quantity > variantStockLimit) {
                CustomToast.showWarning(holder.itemView.getContext(), "Only " + variantStockLimit + " left in stock");
                return;
            }

            CartEntity entity = new CartEntity(
                    userId, product.getId() != null ? product.getId() : 0L,
                    variantId,
                    product.getName(), variantName, selectedPrice[0], quantity,
                    product.getPrimaryImage() != null ? product.getPrimaryImage() : "",
                    isSubscribedQuickItem,
                    isSubscribedQuickItem ? "WEEKLY" : null
            );

            CartManager.addToCart(holder.itemView.getContext(), entity, () -> {
                CustomToast.showSuccess(holder.itemView.getContext(), "Added to cart");
                bottomSheetDialog.dismiss();
            });
        });

        bottomSheetDialog.setContentView(sheetView);
        bottomSheetDialog.show();
    }

    private void setupWishlist(ViewHolder holder, Product product) {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) {
            updateWishlistUI(holder, false);
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            boolean isFav = AppDatabase.getInstance(holder.itemView.getContext())
                    .wishlistDao().isInWishlist(product.getId(), userId);
            holder.itemView.post(() -> updateWishlistUI(holder, isFav));
        });

        holder.btnWishlist.setOnClickListener(v -> {
            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase db = AppDatabase.getInstance(holder.itemView.getContext());
                WishlistDao dao = db.wishlistDao();
                boolean exists = dao.isInWishlist(product.getId(), userId);

                if (exists) {
                    dao.deleteWishlist(new Wishlist(product.getId(), userId));
                    syncWishlistToFirestore(product.getId(), false);
                } else {
                    dao.insertWishlist(new Wishlist(product.getId(), userId));
                    syncWishlistToFirestore(product.getId(), true);
                }
                holder.itemView.post(() -> updateWishlistUI(holder, !exists));
            });
        });
    }

    private void syncWishlistToFirestore(long productId, boolean add) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        if (add) {
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("productId", productId);
            data.put("addedAt", System.currentTimeMillis());
            FirebaseFirestore.getInstance().collection("wishlists").document(uid)
                    .collection("items").document(String.valueOf(productId))
                    .set(data, SetOptions.merge());
        } else {
            FirebaseFirestore.getInstance().collection("wishlists").document(uid)
                    .collection("items").document(String.valueOf(productId)).delete();
        }
    }

    private void updateWishlistUI(ViewHolder holder, boolean isFav) {
        if (holder.imgWishlist == null) return;
        holder.imgWishlist.setImageResource(isFav ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
        holder.imgWishlist.setColorFilter(holder.itemView.getContext().getColor(
                isFav ? R.color.md_theme_primary : R.color.md_theme_onSurfaceVariant));
    }

    @NonNull
    private String getReadableVariantName(@NonNull Product product, @Nullable ProductVariant variant) {
        if (variant != null && isMeaningfulVariantName(variant.getVariantName())) {
            return variant.getVariantName().trim();
        }

        if (isMeaningfulVariantName(product.getUnit())) {
            return product.getUnit().trim();
        }

        return "1 item";
    }

    private boolean isMeaningfulVariantName(@Nullable String value) {
        if (value == null) {
            return false;
        }

        String normalized = value.trim();
        return !normalized.isEmpty() && !"default".equalsIgnoreCase(normalized);
    }

    private boolean isNewestStatus(@Nullable String status) {
        if (status == null) {
            return false;
        }

        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("new") || normalized.contains("latest") || normalized.contains("recent");
    }

    private boolean hasInStockVariant(@NonNull Product product) {
        List<ProductVariant> variants = product.getVariants();
        if (variants == null || variants.isEmpty()) {
            return true;
        }
        for (ProductVariant variant : variants) {
            if (variant != null && variant.isInStock()) {
                Integer stock = resolveVariantStock(variant);
                if (stock == null || stock > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private ProductVariant getFirstInStockVariant(@NonNull Product product) {
        List<ProductVariant> variants = product.getVariants();
        if (variants == null) {
            return null;
        }
        for (ProductVariant variant : variants) {
            if (variant == null) {
                continue;
            }
            Integer stock = resolveVariantStock(variant);
            if (variant.isInStock() && (stock == null || stock > 0)) {
                return variant;
            }
        }
        return null;
    }

    @Nullable
    private Integer resolveVariantStock(@Nullable ProductVariant variant) {
        if (variant == null) {
            return null;
        }
        Integer stock = variant.getResolvedStockCount();
        if (stock != null) {
            return Math.max(0, stock);
        }
        return variant.isInStock() ? null : 0;
    }

    private int resolveStockLimit(@Nullable ProductVariant variant) {
        Integer stock = resolveVariantStock(variant);
        if (stock == null) {
            return -1;
        }
        return Math.max(0, stock);
    }

    private int getFrequentCount(@Nullable Product product) {
        if (product == null || product.getId() == null || frequentPurchaseCounts == null) {
            return 0;
        }

        Integer count = frequentPurchaseCounts.get(product.getId());
        return count != null ? count : 0;
    }

    @Nullable
    private ResolvedQuickAddChoice resolvePreferredQuickAddChoice(@NonNull Product product) {
        if (product.getId() == null || preferredQuickAddChoices == null) {
            return null;
        }

        PreferredQuickAddChoice preferredChoice = preferredQuickAddChoices.get(product.getId());
        if (preferredChoice == null) {
            return null;
        }

        ProductVariant matchedVariant = null;
        if (product.getVariants() != null) {
            for (ProductVariant variant : product.getVariants()) {
                if (variant == null) {
                    continue;
                }

                if (preferredChoice.variantId != null && preferredChoice.variantId.equals(variant.getId())) {
                    matchedVariant = variant;
                    break;
                }
            }

            if (matchedVariant == null && preferredChoice.variantName != null) {
                for (ProductVariant variant : product.getVariants()) {
                    if (variant == null || variant.getVariantName() == null) {
                        continue;
                    }

                    if (preferredChoice.variantName.trim().equalsIgnoreCase(variant.getVariantName().trim())) {
                        matchedVariant = variant;
                        break;
                    }
                }
            }
        }

        String resolvedVariantName = matchedVariant != null
                ? getReadableVariantName(product, matchedVariant)
                : resolvePreferredVariantLabel(product, preferredChoice);

        return new ResolvedQuickAddChoice(
                matchedVariant,
                resolvedVariantName,
                Math.max(1, preferredChoice.quantity),
                Math.max(0, preferredChoice.purchaseCount)
        );
    }

    @NonNull
    private String resolvePreferredVariantLabel(@NonNull Product product,
                                                @NonNull PreferredQuickAddChoice preferredChoice) {
        if (isMeaningfulVariantName(preferredChoice.variantName)) {
            return preferredChoice.variantName.trim();
        }
        return getReadableVariantName(product, null);
    }

    private boolean matchesPreferredChoice(@NonNull Product product,
                                           @NonNull ProductVariant variant,
                                           @NonNull ResolvedQuickAddChoice preferredChoice) {
        if (preferredChoice.variant != null && preferredChoice.variant.getId() != null && variant.getId() != null) {
            return preferredChoice.variant.getId().equals(variant.getId());
        }

        return preferredChoice.variantName.equalsIgnoreCase(getReadableVariantName(product, variant));
    }

    @Override
    public int getItemCount() { return products != null ? products.size() : 0; }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView productImage, imgWishlist;
        TextView productName, productPrice, itemCount, quickTag, badgeDeal, badgeNewest;
        View btnQuickAdd, btnWishlist;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            productImage = itemView.findViewById(R.id.productImage);
            if (productImage == null) productImage = itemView.findViewById(R.id.imgQuickProduct);
            productName = itemView.findViewById(R.id.productName);
            if (productName == null) productName = itemView.findViewById(R.id.tvQuickProductName);
            productPrice = itemView.findViewById(R.id.productPrice);
            if (productPrice == null) productPrice = itemView.findViewById(R.id.tvQuickProductPrice);
            itemCount = itemView.findViewById(R.id.itemCount);
            if (itemCount == null) itemCount = itemView.findViewById(R.id.tvQuickProductWeight);
            quickTag = itemView.findViewById(R.id.tvQuickTag);
            badgeDeal = itemView.findViewById(R.id.badgeDeal);
            badgeNewest = itemView.findViewById(R.id.badgeNewest);
            btnQuickAdd = itemView.findViewById(R.id.btnQuickAdd);
            btnWishlist = itemView.findViewById(R.id.btnWishlist);
            imgWishlist = itemView.findViewById(R.id.imgWishlist);
        }
    }
}
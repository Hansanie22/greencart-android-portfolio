package com.hansanie.greencart.fragment;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;
import android.util.Log;
import com.hansanie.greencart.R;
import com.hansanie.greencart.model.GrocerySubscriptionItem;
import com.hansanie.greencart.model.ProductStockSummary;
import com.hansanie.greencart.model.ProductVariant;
import com.hansanie.greencart.network.RetrofitClient;
import com.hansanie.greencart.network.ApiService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
public class ReviewItemAdapter extends RecyclerView.Adapter<ReviewItemAdapter.VH> {

    public interface OnQuantityChanged { void onChange(); }

    private final List<GrocerySubscriptionItem> items;
    private final OnQuantityChanged callback;
    private final Map<Long, String> imageCache = new HashMap<>();
    // Cache to hold product metadata (name, image, variant names) to avoid repeated Firestore reads
    private final Map<Long, ProductMeta> productMetaCache = new HashMap<>();
    // Track productIds currently being fetched to avoid duplicate in-flight requests
    private final Set<Long> fetchingProductIds = new HashSet<>();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final ApiService apiService = RetrofitClient.getApiService();

    // Simple container for product metadata
    private static class ProductMeta {
        String name;
        String imageUrl;
        Map<Long, String> variantNames;

        ProductMeta(String name, String imageUrl, Map<Long, String> variantNames) {
            this.name = name;
            this.imageUrl = imageUrl;
            this.variantNames = variantNames;
        }
    }

    public ReviewItemAdapter(List<GrocerySubscriptionItem> items, OnQuantityChanged cb) {
        this.items = items;
        this.callback = cb;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_review_subscription, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        GrocerySubscriptionItem it = items.get(position);
        final int pos = position;

        h.tvName.setText(it.getName() != null ? it.getName() : "Item");
        if (it.getVariantName() != null && !it.getVariantName().isEmpty()) {
            h.tvVariant.setVisibility(View.VISIBLE);
            h.tvVariant.setText(it.getVariantName());
        } else {
            h.tvVariant.setVisibility(View.GONE);
        }

        double price = it.getUnitPrice() != null ? it.getUnitPrice() : 0.0;
        int    qty   = it.getQuantity()  != null ? it.getQuantity()  : 1;
        h.tvQty.setText(String.valueOf(qty));
        h.tvLineTotal.setText(String.format(Locale.getDefault(), "Rs. %,.2f", price * qty));
        h.tvUnitPrice.setText(String.format(Locale.getDefault(), "Rs. %,.2f each", price));

        h.btnMinus.setOnClickListener(v -> {
            int cur = it.getQuantity() != null ? it.getQuantity() : 1;
            if (cur <= 1) return;
            it.setQuantity(cur - 1);
            h.tvQty.setText(String.valueOf(cur - 1));
            h.tvLineTotal.setText(String.format(Locale.getDefault(), "Rs. %,.2f", price * (cur - 1)));
            if (callback != null) callback.onChange();
        });

        h.btnPlus.setOnClickListener(v -> {
            int cur = it.getQuantity() != null ? it.getQuantity() : 1;
            // Validate stock if variantId available
            Long variantId = it.getVariantId();
            Long productId = it.getProductId();
            if (variantId != null && productId != null) {
                // Best-effort check: request stock batches for variant
                apiService.getStockBatchesByProductAndVariant(productId, variantId)
                        .enqueue(new Callback<List<ProductStockSummary>>() {
                            @Override
                            public void onResponse(Call<List<ProductStockSummary>> call, Response<List<ProductStockSummary>> response) {
                                boolean hasStock = true; // default optimistic
                                if (response.isSuccessful() && response.body() != null) {
                                    int total = 0;
                                    for (ProductStockSummary s : response.body()) {
                                        if (s != null && s.quantity != null) total += s.quantity;
                                    }
                                    hasStock = total > 0;
                                }
                                if (!hasStock) {
                                    // show warning using toast if context available
                                    if (h.itemView.getContext() != null) {
                                        android.widget.Toast.makeText(h.itemView.getContext(), "Variant out of stock", android.widget.Toast.LENGTH_SHORT).show();
                                    }
                                    return;
                                }
                                // proceed to increment
                                it.setQuantity(cur + 1);
                                h.tvQty.setText(String.valueOf(cur + 1));
                                h.tvLineTotal.setText(String.format(Locale.getDefault(), "Rs. %,.2f", price * (cur + 1)));
                                if (callback != null) callback.onChange();
                            }

                            @Override
                            public void onFailure(Call<List<ProductStockSummary>> call, Throwable t) {
                                // Network failed — proceed optimistically
                                it.setQuantity(cur + 1);
                                h.tvQty.setText(String.valueOf(cur + 1));
                                h.tvLineTotal.setText(String.format(Locale.getDefault(), "Rs. %,.2f", price * (cur + 1)));
                                if (callback != null) callback.onChange();
                            }
                        });
                return;
            }

            // No variant/product info — optimistic increment
            it.setQuantity(cur + 1);
            h.tvQty.setText(String.valueOf(cur + 1));
            h.tvLineTotal.setText(String.format(Locale.getDefault(), "Rs. %,.2f", price * (cur + 1)));
            if (callback != null) callback.onChange();
        });

        // Load product metadata (name, variant name, image) from cache or Firestore when needed
        ProductMeta meta = null;
        if (it.getProductId() != null) {
            meta = productMetaCache.get(it.getProductId());
        }

        // Prefer explicit item fields, fall back to cached metadata
        String resolvedName = !TextUtils.isEmpty(it.getName()) ? it.getName() : (meta != null ? meta.name : null);
        if (!TextUtils.isEmpty(resolvedName)) {
            h.tvName.setText(resolvedName);
        }

        if (!TextUtils.isEmpty(it.getVariantName())) {
            h.tvVariant.setVisibility(View.VISIBLE);
            h.tvVariant.setText(it.getVariantName());
        } else if (meta != null && it.getVariantId() != null && meta.variantNames != null) {
            String vn = meta.variantNames.get(it.getVariantId());
            if (!TextUtils.isEmpty(vn)) {
                h.tvVariant.setVisibility(View.VISIBLE);
                h.tvVariant.setText(vn);
            } else {
                h.tvVariant.setVisibility(View.GONE);
            }
        }

        // Image: prefer item.imageUrl, then cached product meta image, then imageCache (legacy)
        String imageUrl = it.getImageUrl();
        if (TextUtils.isEmpty(imageUrl) && meta != null) imageUrl = meta.imageUrl;
        if (TextUtils.isEmpty(imageUrl) && it.getProductId() != null) {
            imageUrl = imageCache.get(it.getProductId());
        }

        if (h.ivImage != null) {
            Glide.with(h.itemView.getContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_cart)
                    .error(R.drawable.ic_cart)
                    .into(h.ivImage);
        }

        // If we don't have cached metadata for this product, try Retrofit API first, then fallback to Firestore
        if (it.getProductId() != null && meta == null && !fetchingProductIds.contains(it.getProductId())) {
            long pid = it.getProductId();
            // mark fetching to avoid duplicate concurrent requests
            fetchingProductIds.add(pid);
            // Try REST API to get latest product details (price, images, variants)
            apiService.getProductDetails(pid).enqueue(new retrofit2.Callback<com.hansanie.greencart.model.Product>() {
                @Override
                public void onResponse(retrofit2.Call<com.hansanie.greencart.model.Product> call, retrofit2.Response<com.hansanie.greencart.model.Product> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        com.hansanie.greencart.model.Product p = response.body();
                        String resolvedImage = null;
                        if (p.getImages() != null && !p.getImages().isEmpty()) resolvedImage = p.getImages().get(0);
                        if (resolvedImage == null && p.getImageUrl() != null) resolvedImage = p.getImageUrl();

                        String resolvedName = p.getName();
                        Map<Long, String> variantNames = new HashMap<>();
                        if (p.getVariants() != null) {
                            for (ProductVariant v : p.getVariants()) {
                                if (v != null && v.getId() != null && v.getVariantName() != null) {
                                    variantNames.put(v.getId(), v.getVariantName());
                                }
                            }
                        }

                        // determine unit price from variant if variantId provided, else fall back to product min price
                        Double unit = null;
                        if (it.getVariantId() != null && p.getVariants() != null) {
                            for (com.hansanie.greencart.model.ProductVariant v : p.getVariants()) {
                                if (v != null && it.getVariantId().equals(v.getId())) {
                                    // ProductVariant.getEffectivePrice() returns primitive double; box it
                                    unit = Double.valueOf(v.getEffectivePrice());
                                    break;
                                }
                            }
                        }
                        if (unit == null) {
                            // Product doesn't have a direct price field; use helper to get min variant price
                            Double min = p.getMinPrice();
                            unit = min;
                        }

                        ProductMeta pm = new ProductMeta(resolvedName, resolvedImage, variantNames);
                        productMetaCache.put(pid, pm);
                        if (resolvedImage != null) imageCache.put(pid, resolvedImage);

                        // set item's unit price and image if missing
                        boolean changed = false;
                        if (it.getUnitPrice() == null && unit != null) {
                            it.setUnitPrice(unit);
                            changed = true;
                        }
                        if ((it.getImageUrl() == null || it.getImageUrl().isEmpty()) && resolvedImage != null) {
                            it.setImageUrl(resolvedImage);
                            changed = true;
                        }

                        // update UI using stable position captured above
                        if (changed && pos >= 0 && pos < items.size()) notifyItemChanged(pos);
                        // Also update the current ViewHolder UI directly if it's still bound to this position
                        // Prepare final copies for lambda capture
                        final double newPriceVal = it.getUnitPrice() != null ? it.getUnitPrice() : 0.0;
                        final int qVal = it.getQuantity() != null ? it.getQuantity() : 1;
                        final String imageToLoad = resolvedImage;
                        if (h != null && h.itemView != null) {
                            h.itemView.post(() -> {
                                if (h.getBindingAdapterPosition() == pos) {
                                    h.tvUnitPrice.setText(String.format(Locale.getDefault(), "Rs. %,.2f each", newPriceVal));
                                    h.tvLineTotal.setText(String.format(Locale.getDefault(), "Rs. %,.2f", newPriceVal * qVal));
                                    if (!TextUtils.isEmpty(imageToLoad) && h.ivImage != null) {
                                        Glide.with(h.itemView.getContext())
                                                .load(imageToLoad)
                                                .placeholder(R.drawable.ic_cart)
                                                .error(R.drawable.ic_cart)
                                                .into(h.ivImage);
                                    }
                                }
                            });
                        }
                        fetchingProductIds.remove(pid);
                    } else {
                        // Fallback to Firestore fetch if REST fails or data incomplete
                        fetchingProductIds.remove(pid);
                        fetchProductMetadata(pid, pos, it.getVariantId());
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<com.hansanie.greencart.model.Product> call, Throwable t) {
                    // Network failed — fallback to Firestore
                    fetchingProductIds.remove(pid);
                    fetchProductMetadata(pid, pos, it.getVariantId());
                }
            });
        }
    }

    @Override
    public int getItemCount() { return items != null ? items.size() : 0; }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvVariant, tvQty, tvLineTotal, tvUnitPrice;
        ImageView ivImage;
        View btnMinus, btnPlus;
        VH(@NonNull View v) {
            super(v);
            // Use runtime lookup to avoid static analysis errors referencing generated R symbols
            final String pkg = v.getContext().getPackageName();
            int idName = v.getResources().getIdentifier("tvItemName", "id", pkg);
            int idVariant = v.getResources().getIdentifier("tvItemVariant", "id", pkg);
            int idQty = v.getResources().getIdentifier("tvItemQty", "id", pkg);
            int idLine = v.getResources().getIdentifier("tvItemLineTotal", "id", pkg);
            int idUnit = v.getResources().getIdentifier("tvItemUnitPrice", "id", pkg);
            int idMinus = v.getResources().getIdentifier("btnQtyMinus", "id", pkg);
            int idPlus = v.getResources().getIdentifier("btnQtyPlus", "id", pkg);
            int idImage = v.getResources().getIdentifier("ivItemImage", "id", pkg);

            tvName      = idName != 0 ? v.findViewById(idName) : new TextView(v.getContext());
            tvVariant   = idVariant != 0 ? v.findViewById(idVariant) : new TextView(v.getContext());
            tvQty       = idQty != 0 ? v.findViewById(idQty) : new TextView(v.getContext());
            tvLineTotal = idLine != 0 ? v.findViewById(idLine) : new TextView(v.getContext());
            tvUnitPrice = idUnit != 0 ? v.findViewById(idUnit) : new TextView(v.getContext());
            btnMinus    = idMinus != 0 ? v.findViewById(idMinus) : new View(v.getContext());
            btnPlus     = idPlus != 0 ? v.findViewById(idPlus) : new View(v.getContext());
            ivImage     = idImage != 0 ? v.findViewById(idImage) : null;
        }
    }

    private void fetchProductMetadata(long productId, int position, Long variantId) {
        // mark fetching
        fetchingProductIds.add(productId);
        db.collection("products")
                .whereEqualTo("id", productId)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    try {
                        if (snapshot.isEmpty()) return;
                        Map<String, Object> data = snapshot.getDocuments().get(0).getData();
                        if (data == null) return;

                        String resolvedImage = null;
                        Object imagesObj = data.get("images");
                        if (imagesObj instanceof List && !((List<?>) imagesObj).isEmpty()) {
                            Object first = ((List<?>) imagesObj).get(0);
                            if (first != null) resolvedImage = String.valueOf(first);
                        }
                        if (TextUtils.isEmpty(resolvedImage) && data.get("imageUrl") != null) {
                            resolvedImage = String.valueOf(data.get("imageUrl"));
                        }

                        String resolvedName = null;
                        if (data.get("name") != null) resolvedName = String.valueOf(data.get("name"));
                        else if (data.get("title") != null) resolvedName = String.valueOf(data.get("title"));

                        Map<Long, String> variantNames = new HashMap<>();
                        Object variantsObj = data.get("variants");
                        if (variantsObj instanceof List) {
                            for (Object o : (List<?>) variantsObj) {
                                if (o instanceof Map) {
                                    Map<?, ?> vm = (Map<?, ?>) o;
                                    Object idObj = vm.get("id");
                                    Object nameObj = vm.get("name");
                                    if (idObj != null && nameObj != null) {
                                        try {
                                            Long id = Long.parseLong(String.valueOf(idObj));
                                            variantNames.put(id, String.valueOf(nameObj));
                                        } catch (NumberFormatException ignored) {
                                        }
                                    }
                                }
                            }
                        }

                        ProductMeta meta = new ProductMeta(resolvedName, resolvedImage, variantNames);
                        productMetaCache.put(productId, meta);
                        if (!TextUtils.isEmpty(resolvedImage)) imageCache.put(productId, resolvedImage);

                        // Update item fields if position valid
                        if (position >= 0 && position < items.size()) {
                            GrocerySubscriptionItem it = items.get(position);
                            boolean changed = false;
                            if (!TextUtils.isEmpty(resolvedName) && TextUtils.isEmpty(it.getName())) {
                                it.setName(resolvedName);
                                changed = true;
                            }
                            if (TextUtils.isEmpty(it.getVariantName()) && variantId != null) {
                                String vn = variantNames.get(variantId);
                                if (!TextUtils.isEmpty(vn)) {
                                    it.setVariantName(vn);
                                    changed = true;
                                }
                            }

                            // Try to resolve unit price from Firestore product document:
                            if (it.getUnitPrice() == null) {
                                boolean priceSet = false;
                                // 1) try variant-level price if variants present
                                Object variantsObj2 = data.get("variants");
                                if (variantsObj2 instanceof List && variantId != null) {
                                    for (Object vo : (List<?>) variantsObj2) {
                                        if (!(vo instanceof Map)) continue;
                                        Map<?, ?> vm = (Map<?, ?>) vo;
                                        Object vid = vm.get("id");
                                        Long vidLong = null;
                                        if (vid instanceof Number) vidLong = ((Number) vid).longValue();
                                        else if (vid instanceof String) {
                                            try { vidLong = Long.parseLong(((String) vid).trim()); } catch (Exception ignored) {}
                                        }
                                        if (vidLong != null && vidLong.equals(variantId)) {
                                            Object vPrice = vm.get("effectivePrice");
                                            if (vPrice == null) vPrice = vm.get("price");
                                            if (vPrice == null) vPrice = vm.get("unitPrice");
                                            if (vPrice instanceof Number) { it.setUnitPrice(((Number) vPrice).doubleValue()); priceSet = true; }
                                            else if (vPrice instanceof String) { try { it.setUnitPrice(Double.parseDouble(((String) vPrice).trim())); priceSet = true; } catch (Exception ignored) {} }
                                            if (priceSet) { changed = true; break; }
                                        }
                                    }
                                }

                                // 2) fallback to product-level price fields
                                if (!priceSet) {
                                    Object priceObj = data.get("effectivePrice");
                                    if (priceObj == null) priceObj = data.get("price");
                                    if (priceObj == null) priceObj = data.get("unitPrice");
                                    if (priceObj == null) priceObj = data.get("finalPrice");
                                    if (priceObj instanceof Number) { it.setUnitPrice(((Number) priceObj).doubleValue()); changed = true; }
                                    else if (priceObj instanceof String) { try { it.setUnitPrice(Double.parseDouble(((String) priceObj).trim())); changed = true; } catch (Exception ignored) {} }
                                }
                            }

                            if (TextUtils.isEmpty(it.getImageUrl()) && !TextUtils.isEmpty(resolvedImage)) {
                                it.setImageUrl(resolvedImage);
                                changed = true;
                            }
                            if (changed) {
                                notifyItemChanged(position);
                            } else {
                                // still update image if view exists
                                notifyItemChanged(position);
                            }
                        } else {
                            notifyDataSetChanged();
                        }
                    } finally {
                        fetchingProductIds.remove(productId);
                    }
                }).addOnFailureListener(e -> fetchingProductIds.remove(productId));
    }

    /**
     * Ensure metadata (price/image/variant name) for all items in the adapter. Call after
     * the adapter's data set has been updated (e.g., after Firestore enrichment) to
     * proactively request missing data from the backend or Firestore.
     */
    public void ensureMetadataForAll() {
        if (items == null || items.isEmpty()) return;
        for (int i = 0; i < items.size(); i++) {
            GrocerySubscriptionItem it = items.get(i);
            if (it == null || it.getProductId() == null) continue;
            boolean needsPrice = it.getUnitPrice() == null || it.getUnitPrice() == 0.0;
            boolean needsImage = it.getImageUrl() == null || it.getImageUrl().isEmpty();
            if (!needsPrice && !needsImage) continue;
            long pid = it.getProductId();
            if (fetchingProductIds.contains(pid)) continue;
            fetchingProductIds.add(pid);
            final int pos = i;
            apiService.getProductDetails(pid).enqueue(new retrofit2.Callback<com.hansanie.greencart.model.Product>() {
                @Override
                public void onResponse(retrofit2.Call<com.hansanie.greencart.model.Product> call, retrofit2.Response<com.hansanie.greencart.model.Product> response) {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            com.hansanie.greencart.model.Product p = response.body();
                            String resolvedImage = null;
                            if (p.getImages() != null && !p.getImages().isEmpty()) resolvedImage = p.getImages().get(0);
                            if (resolvedImage == null && p.getImageUrl() != null) resolvedImage = p.getImageUrl();

                            Map<Long, String> variantNames = new HashMap<>();
                            if (p.getVariants() != null) {
                                for (ProductVariant v : p.getVariants()) {
                                    if (v != null && v.getId() != null && v.getVariantName() != null) {
                                        variantNames.put(v.getId(), v.getVariantName());
                                    }
                                }
                            }

                            // Determine unit price
                            Double unit = null;
                            if (it.getVariantId() != null && p.getVariants() != null) {
                                for (com.hansanie.greencart.model.ProductVariant v : p.getVariants()) {
                                    if (v != null && it.getVariantId().equals(v.getId())) {
                                        unit = Double.valueOf(v.getEffectivePrice());
                                        break;
                                    }
                                }
                            }
                            if (unit == null) unit = p.getMinPrice();

                            ProductMeta pm = new ProductMeta(p.getName(), resolvedImage, variantNames);
                            productMetaCache.put(pid, pm);
                            if (resolvedImage != null) imageCache.put(pid, resolvedImage);

                            boolean changed = false;
                            if ((it.getUnitPrice() == null || it.getUnitPrice() == 0.0) && unit != null) { it.setUnitPrice(unit); changed = true; }
                            Log.d("REV_ITEM", "ensureMetadata REST product=" + pid + " unit=" + unit + " image=" + resolvedImage + " pos=" + pos);
                            if ((it.getImageUrl() == null || it.getImageUrl().isEmpty()) && resolvedImage != null) { it.setImageUrl(resolvedImage); changed = true; }
                            if ((it.getVariantName() == null || it.getVariantName().isEmpty()) && it.getVariantId() != null) {
                                String vn = variantNames.get(it.getVariantId());
                                if (!TextUtils.isEmpty(vn)) { it.setVariantName(vn); changed = true; }
                            }
                            if (changed) {
                                // Ensure main-thread update
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> notifyItemChanged(pos));
                            }
                        } else {
                            // fallback to Firestore per-item
                            fetchProductMetadata(pid, pos, it.getVariantId());
                        }
                    } finally {
                        fetchingProductIds.remove(pid);
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<com.hansanie.greencart.model.Product> call, Throwable t) {
                    try { fetchProductMetadata(pid, pos, it.getVariantId()); }
                    finally { fetchingProductIds.remove(pid); }
                }
            });
        }
    }
}


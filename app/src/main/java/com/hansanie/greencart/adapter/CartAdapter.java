package com.hansanie.greencart.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.hansanie.greencart.R;
import com.hansanie.greencart.model.CartItem;
import com.hansanie.greencart.model.ProductVariant;
import com.hansanie.greencart.network.ApiService;
import com.hansanie.greencart.network.CartManager;
import com.hansanie.greencart.network.RetrofitClient;
import com.hansanie.greencart.util.CustomToast;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {

    private final Context context;
    private final List<CartItem> list;
    private final Runnable onDataChanged;
    private final ApiService apiService = RetrofitClient.getApiService();

    public CartAdapter(Context context, List<CartItem> list, Runnable onDataChanged) {
        this.context = context;
        this.list = list;
        this.onDataChanged = onDataChanged;
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CartViewHolder(
                LayoutInflater.from(context).inflate(R.layout.item_cart, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        CartItem item = list.get(position);
        holder.name.setText(item.getName());
        holder.unit.setText(item.getVariantName());
        holder.price.setText(String.format("Rs. %.2f", item.getPrice()));
        holder.quantity.setText(String.valueOf(item.getQuantity()));
        holder.availableStock = null;
        holder.outOfStock = false;
        holder.stockStatus.setVisibility(View.GONE);
        setQuantityControlsEnabled(holder, true);

        // Load image with Glide (URL-based)
        Glide.with(context)
                .load(item.getImageUrl())
                .placeholder(R.drawable.fresh)
                .error(R.drawable.fresh)
                .into(holder.productImg);

        bindStockStatus(holder, item);

        holder.btnPlus.setOnClickListener(v -> {
            if (holder.outOfStock) {
                CustomToast.showWarning(context, "Out of stock. Cannot add more");
                return;
            }
            if (holder.availableStock != null && item.getQuantity() >= holder.availableStock) {
                CustomToast.showWarning(context, "Only " + holder.availableStock + " left in stock");
                return;
            }
            int newQty = item.getQuantity() + 1;
            item.setQuantity(newQty);
            CartManager.updateQuantity(context, item.toEntity(), newQty, null);
            notifyItemChanged(position);
            onDataChanged.run();
        });

        holder.btnMinus.setOnClickListener(v -> {
            if (item.getQuantity() > 1) {
                int newQty = item.getQuantity() - 1;
                item.setQuantity(newQty);
                CartManager.updateQuantity(context, item.toEntity(), newQty, null);
                notifyItemChanged(position);
                onDataChanged.run();
            }
        });

        holder.btnRemove.setOnClickListener(v -> {
            CartManager.removeItem(context, item.toEntity(), null);
            list.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, list.size());
            onDataChanged.run();
        });
    }

    @Override
    public int getItemCount() { return list.size(); }

    private void bindStockStatus(@NonNull CartViewHolder holder, @NonNull CartItem item) {
        apiService.getVariantsByProductId(item.getProductId()).enqueue(new Callback<List<ProductVariant>>() {
            @Override
            public void onResponse(Call<List<ProductVariant>> call, Response<List<ProductVariant>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    return;
                }

                ProductVariant variant = findMatchingVariant(item, response.body());
                if (variant == null) {
                    return;
                }

                Integer stock = variant.getResolvedStockCount();
                if (stock == null) {
                    holder.availableStock = variant.isInStock() ? null : 0;
                } else {
                    holder.availableStock = Math.max(0, stock);
                }
                holder.outOfStock = holder.availableStock != null && holder.availableStock <= 0;

                if (holder.outOfStock) {
                    holder.stockStatus.setText("Out of Stock");
                    holder.stockStatus.setVisibility(View.VISIBLE);
                    setQuantityControlsEnabled(holder, false);
                } else {
                    holder.stockStatus.setVisibility(View.GONE);
                    setQuantityControlsEnabled(holder, true);
                    if (holder.availableStock != null && item.getQuantity() > holder.availableStock) {
                        int adjusted = Math.max(1, holder.availableStock);
                        item.setQuantity(adjusted);
                        holder.quantity.setText(String.valueOf(adjusted));
                        CartManager.updateQuantity(context, item.toEntity(), adjusted, null);
                        onDataChanged.run();
                    }
                }
            }

            @Override
            public void onFailure(Call<List<ProductVariant>> call, Throwable t) {
                // Keep current cart row state if stock API is unavailable.
            }
        });
    }

    private void setQuantityControlsEnabled(@NonNull CartViewHolder holder, boolean enabled) {
        holder.btnPlus.setEnabled(enabled);
        holder.btnMinus.setEnabled(enabled);
        holder.btnPlus.setAlpha(enabled ? 1f : 0.45f);
        holder.btnMinus.setAlpha(enabled ? 1f : 0.45f);
    }

    @Nullable
    private ProductVariant findMatchingVariant(@NonNull CartItem item, @NonNull List<ProductVariant> variants) {
        if (item.getVariantId() != null) {
            for (ProductVariant variant : variants) {
                if (variant != null && item.getVariantId().equals(variant.getId())) {
                    return variant;
                }
            }
        }

        if (!TextUtils.isEmpty(item.getVariantName())) {
            for (ProductVariant variant : variants) {
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

    public static class CartViewHolder extends RecyclerView.ViewHolder {
        TextView name, unit, price, quantity, stockStatus;
        ShapeableImageView productImg;
        ImageButton btnPlus, btnMinus, btnRemove;
        @Nullable Integer availableStock;
        boolean outOfStock;

        public CartViewHolder(@NonNull View itemView) {
            super(itemView);
            name       = itemView.findViewById(R.id.tvCartProductName);
            unit       = itemView.findViewById(R.id.tvCartProductUnit);
            price      = itemView.findViewById(R.id.tvCartPrice);
            quantity   = itemView.findViewById(R.id.tvQuantity);
            stockStatus = itemView.findViewById(R.id.tvCartStockStatus);
            productImg = itemView.findViewById(R.id.ivCartProduct);
            btnPlus    = itemView.findViewById(R.id.btnPlus);
            btnMinus   = itemView.findViewById(R.id.btnMinus);
            btnRemove  = itemView.findViewById(R.id.btnRemoveItem);
        }
    }
}
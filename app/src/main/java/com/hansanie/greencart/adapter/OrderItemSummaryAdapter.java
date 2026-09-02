package com.hansanie.greencart.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hansanie.greencart.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderItemSummaryAdapter extends RecyclerView.Adapter<OrderItemSummaryAdapter.OrderItemViewHolder> {

    private final List<Map<String, Object>> items = new ArrayList<>();
    private final Map<Long, String> productImageCache = new HashMap<>();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private OnOrderItemClickListener onOrderItemClickListener;

    public interface OnOrderItemClickListener {
        void onOrderItemClick(@NonNull Map<String, Object> item);
    }

    public void setOnOrderItemClickListener(@Nullable OnOrderItemClickListener listener) {
        this.onOrderItemClickListener = listener;
    }

    public void submitList(List<Map<String, Object>> orderItems) {
        items.clear();
        if (orderItems != null) {
            items.addAll(orderItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrderItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order_detail_product, parent, false);
        return new OrderItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderItemViewHolder holder, int position) {
        Map<String, Object> item = items.get(position);
        String name = stringValue(item.get("productName"), stringValue(item.get("name"), "Organic item"));
        String variant = stringValue(item.get("variantName"), stringValue(item.get("variant"), "Fresh pack"));
        int quantity = numberValue(item.get("quantity"), 1);
        double unitPrice = decimalValue(
                item.get("unitPrice"),
                decimalValue(item.get("priceAtPurchase"), decimalValue(item.get("price"), 0))
        );
        String imageUrl = stringValue(item.get("imageUrl"), null);
        Long productId = longValue(item.get("productId"));

        holder.title.setText(name);
        holder.subtitle.setText(String.format(Locale.getDefault(), "%s • Qty %d", variant, quantity));
        holder.amount.setText(String.format(Locale.getDefault(), "Rs. %.2f", unitPrice * quantity));

        if (TextUtils.isEmpty(imageUrl) && productId != null) {
            imageUrl = productImageCache.get(productId);
        }

        Glide.with(holder.itemView.getContext())
                .load(imageUrl)
                .placeholder(R.drawable.ic_cart)
                .error(R.drawable.ic_cart)
                .into(holder.itemImage);

        if (TextUtils.isEmpty(imageUrl) && productId != null) {
            fetchProductImage(productId, holder.getBindingAdapterPosition());
        }

        holder.itemView.setOnClickListener(v -> {
            if (onOrderItemClickListener != null) {
                onOrderItemClickListener.onOrderItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String stringValue(Object value, String fallback) {
        return value != null ? String.valueOf(value) : fallback;
    }

    private static int numberValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static double decimalValue(Object value, double fallback) {
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    @Nullable
    private static Long longValue(@Nullable Object value) {
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

    private void fetchProductImage(long productId, int position) {
        db.collection("products")
                .whereEqualTo("id", productId)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        return;
                    }
                    Map<String, Object> data = snapshot.getDocuments().get(0).getData();
                    if (data == null) {
                        return;
                    }

                    String resolved = null;
                    Object imagesObj = data.get("images");
                    if (imagesObj instanceof List && !((List<?>) imagesObj).isEmpty()) {
                        Object first = ((List<?>) imagesObj).get(0);
                        if (first != null) {
                            resolved = String.valueOf(first);
                        }
                    }
                    if (TextUtils.isEmpty(resolved) && data.get("imageUrl") != null) {
                        resolved = String.valueOf(data.get("imageUrl"));
                    }

                    if (!TextUtils.isEmpty(resolved)) {
                        productImageCache.put(productId, resolved);
                        if (position >= 0 && position < items.size()) {
                            notifyItemChanged(position);
                        } else {
                            notifyDataSetChanged();
                        }
                    }
                });
    }

    static class OrderItemViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView subtitle;
        private final TextView amount;
        private final ImageView itemImage;

        OrderItemViewHolder(@NonNull View itemView) {
            super(itemView);
            itemImage = itemView.findViewById(R.id.ivItemImage);
            title = itemView.findViewById(R.id.tvItemTitle);
            subtitle = itemView.findViewById(R.id.tvItemSubtitle);
            amount = itemView.findViewById(R.id.tvItemAmount);
        }
    }
}


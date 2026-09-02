package com.hansanie.greencart.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import androidx.fragment.app.FragmentActivity;
import com.hansanie.greencart.fragment.LoginFragment;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hansanie.greencart.R;
import com.hansanie.greencart.database.AppDatabase;
import com.hansanie.greencart.model.CartEntity;
import com.hansanie.greencart.model.ProductVariant;
import com.hansanie.greencart.model.Wishlist;
import com.hansanie.greencart.model.WishlistItem;
import com.hansanie.greencart.network.ApiService;
import com.hansanie.greencart.network.CartManager;
import com.hansanie.greencart.network.RetrofitClient;
import com.hansanie.greencart.util.CustomToast;

import android.widget.TextView;

import java.util.List;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WishlistAdapter extends RecyclerView.Adapter<WishlistAdapter.WishlistViewHolder> {

    private final Context context;
    private final List<WishlistItem> list;
    private final Runnable onDataChanged;
    private final ApiService apiService = RetrofitClient.getApiService();

    public WishlistAdapter(Context context, List<WishlistItem> list, Runnable onDataChanged) {
        this.context = context;
        this.list = list;
        this.onDataChanged = onDataChanged;
    }

    @NonNull
    @Override
    public WishlistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new WishlistViewHolder(
                LayoutInflater.from(context).inflate(R.layout.item_wishlist, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull WishlistViewHolder holder, int position) {
        WishlistItem item = list.get(holder.getAdapterPosition());
        holder.name.setText(item.getName());
        holder.category.setText(item.getCategory());
        holder.price.setText(String.format("Rs. %.2f", item.getPrice()));

        Glide.with(context)
                .load(item.getImageUrl())
                .placeholder(R.drawable.fresh)
                .error(R.drawable.fresh)
                .into(holder.productImg);

        // Remove from wishlist
        holder.btnRemove.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;

            String userId = FirebaseAuth.getInstance().getUid();
            if (userId == null) return;

            Executors.newSingleThreadExecutor().execute(() -> {
                // Local Room delete
                AppDatabase.getInstance(context)
                        .wishlistDao()
                        .deleteWishlist(new Wishlist(item.getProductId(), userId));

                // Firestore sync delete
                FirebaseFirestore.getInstance()
                        .collection("wishlists")
                        .document(userId)
                        .collection("items")
                        .document(String.valueOf(item.getProductId()))
                        .delete();
            });

            // UI එකෙන් ඉවත් කිරීම
            list.remove(currentPos);
            notifyItemRemoved(currentPos);
            notifyItemRangeChanged(currentPos, list.size());
            onDataChanged.run();
        });

        // Add to cart
        holder.btnAddToCart.setOnClickListener(v -> {
            String userId = FirebaseAuth.getInstance().getUid();
            if (userId == null) {
                CustomToast.showInfo(context, "Please login to continue");
                if (context instanceof FragmentActivity) {
                    FragmentActivity fa = (FragmentActivity) context;
                    fa.getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragmentContainer, new LoginFragment())
                            .addToBackStack(null)
                            .commit();
                }
                return;
            }

            String finalUserId = userId;
            apiService.getVariantsByProductId(item.getProductId()).enqueue(new Callback<List<ProductVariant>>() {
                @Override
                public void onResponse(Call<List<ProductVariant>> call, Response<List<ProductVariant>> response) {
                    if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                        CustomToast.showWarning(context, "Stock check failed. Try again");
                        return;
                    }

                    ProductVariant selected = null;
                    for (ProductVariant variant : response.body()) {
                        if (variant != null && variant.isInStock()) {
                            Integer stock = variant.getResolvedStockCount();
                            if (stock == null || stock > 0) {
                                selected = variant;
                                break;
                            }
                        }
                    }

                    if (selected == null) {
                        CustomToast.showWarning(context, "Out of stock. Cannot add to cart");
                        return;
                    }

                    CartEntity entity = new CartEntity(
                            finalUserId,
                            item.getProductId(),
                            selected.getId(),
                            item.getName(),
                            selected.getVariantName() != null ? selected.getVariantName() : (item.getVariantName() != null ? item.getVariantName() : "1 unit"),
                            selected.getEffectivePrice() > 0 ? selected.getEffectivePrice() : item.getPrice(),
                            1,
                            item.getImageUrl() != null ? item.getImageUrl() : "",
                            false,
                            null
                    );
                    CartManager.addToCart(context, entity, () ->
                            CustomToast.showSuccess(context, item.getName() + " added to cart"));
                }

                @Override
                public void onFailure(Call<List<ProductVariant>> call, Throwable t) {
                    CustomToast.showWarning(context, "Stock check failed. Try again");
                }
            });
        });
    }

    @Override
    public int getItemCount() { return list.size(); }

    public static class WishlistViewHolder extends RecyclerView.ViewHolder {
        TextView name, category, price;
        ShapeableImageView productImg;
        ImageButton btnRemove;
        MaterialButton btnAddToCart;

        public WishlistViewHolder(@NonNull View itemView) {
            super(itemView);
            name        = itemView.findViewById(R.id.tvProductName);
            category    = itemView.findViewById(R.id.tvProductCategory);
            price       = itemView.findViewById(R.id.tvPrice);
            productImg  = itemView.findViewById(R.id.ivProduct);
            btnRemove   = itemView.findViewById(R.id.btnRemove);
            btnAddToCart= itemView.findViewById(R.id.btnAddToCart);
        }
    }
}
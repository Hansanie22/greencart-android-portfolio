package com.hansanie.greencart.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.hansanie.greencart.R;
import com.hansanie.greencart.adapter.WishlistAdapter;
import com.hansanie.greencart.database.AppDatabase;
import com.hansanie.greencart.model.ProductVariant;
import com.hansanie.greencart.model.Wishlist;
import com.hansanie.greencart.model.WishlistItem;
import com.hansanie.greencart.network.ApiService;
import com.hansanie.greencart.network.RetrofitClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WishlistFragment extends Fragment {

    private RecyclerView rvWishlist;
    private TextView tvItemCount;
    private LinearLayout layoutEmpty;
    private List<WishlistItem> wishlistItems = new ArrayList<>();
    private WishlistAdapter adapter;
    private ApiService apiService;
    private FirebaseAuth mAuth;

    public WishlistFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wishlist, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        rvWishlist  = view.findViewById(R.id.rvWishlist);
        tvItemCount = view.findViewById(R.id.tvItemCount);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        MaterialButton btnExplore = view.findViewById(R.id.exploreWish);

        apiService = RetrofitClient.getApiService();

        rvWishlist.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new WishlistAdapter(getContext(), wishlistItems, this::checkEmptyState);
        rvWishlist.setAdapter(adapter);

        btnExplore.setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new HomeFragment()) // සාමාන්‍යයෙන් Home එකට යෑම වඩා සුදුසුයි
                        .addToBackStack(null)
                        .commit());

        loadWishlistFromRoomAndFirestore();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadWishlistFromRoomAndFirestore();
    }

    private void loadWishlistFromRoomAndFirestore() {
        String currentUserId = mAuth.getUid();

        // User ලොග් වී නැත්නම් Empty State පෙන්වන්න
        if (currentUserId == null) {
            wishlistItems.clear();
            adapter.notifyDataSetChanged();
            checkEmptyState();
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            // අදාළ User ID එකට අදාළ Wishlist එක පමණක් ගනියි
            List<Wishlist> savedList = AppDatabase.getInstance(requireContext())
                    .wishlistDao().getWishlistByUser(currentUserId);

            if (savedList.isEmpty()) {
                requireActivity().runOnUiThread(() -> {
                    wishlistItems.clear();
                    adapter.notifyDataSetChanged();
                    checkEmptyState();
                });
                return;
            }

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            List<WishlistItem> results = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger pending = new AtomicInteger(savedList.size());

            for (Wishlist w : savedList) {
                long productId = w.getProductId();

                db.collection("products")
                        .whereEqualTo("id", productId)
                        .get()
                        .addOnSuccessListener(snapshot -> {
                            if (!snapshot.isEmpty()) {
                                QueryDocumentSnapshot doc = (QueryDocumentSnapshot) snapshot.getDocuments().get(0);

                                String name = doc.getString("name");
                                String category = doc.getString("category");

                                List<String> images = (List<String>) doc.get("images");
                                String imageUrl = (images != null && !images.isEmpty()) ? images.get(0) : doc.getString("imageUrl");

                                WishlistItem item = new WishlistItem(
                                        productId,
                                        name != null ? name : "Product",
                                        category != null ? category : "",
                                        0.0,
                                        imageUrl != null ? imageUrl : "",
                                        ""
                                );
                                results.add(item);
                                fetchPriceForItem(item, results, pending);
                            } else {
                                if (pending.decrementAndGet() == 0) postResults(results);
                            }
                        })
                        .addOnFailureListener(e -> {
                            if (pending.decrementAndGet() == 0) postResults(results);
                        });
            }
        });
    }

    private void fetchPriceForItem(WishlistItem item, List<WishlistItem> results, AtomicInteger pending) {
        apiService.getVariantsByProductId(item.getProductId())
                .enqueue(new Callback<List<ProductVariant>>() {
                    @Override
                    public void onResponse(Call<List<ProductVariant>> call, Response<List<ProductVariant>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            ProductVariant first = response.body().get(0);

                            WishlistItem updated = new WishlistItem(
                                    item.getProductId(), item.getName(), item.getCategory(),
                                    first.getPrice() != null ? first.getPrice() : 0.0,
                                    item.getImageUrl(),
                                    first.getVariantName() != null ? first.getVariantName() : "");

                            synchronized (results) {
                                int idx = results.indexOf(item);
                                if (idx >= 0) results.set(idx, updated);
                            }
                        }
                        if (pending.decrementAndGet() == 0) postResults(results);
                    }

                    @Override
                    public void onFailure(Call<List<ProductVariant>> call, Throwable t) {
                        if (pending.decrementAndGet() == 0) postResults(results);
                    }
                });
    }

    private void postResults(List<WishlistItem> results) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            wishlistItems.clear();
            if (results != null) wishlistItems.addAll(results);
            adapter.notifyDataSetChanged();
            checkEmptyState();
        });
    }

    private void checkEmptyState() {
        if (!isAdded()) return;
        boolean isEmpty = wishlistItems.isEmpty();
        rvWishlist.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        layoutEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        tvItemCount.setText(wishlistItems.size() + (wishlistItems.size() == 1 ? " Item" : " Items") + " available");
    }
}
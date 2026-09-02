package com.hansanie.greencart.network;

import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.hansanie.greencart.database.AppDatabase;
import com.hansanie.greencart.model.CartEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class CartManager {

    private static final String TAG = "CartManager";

    // ── Add or increment an item ──────────────────────────────────────────────
    public static void addToCart(Context context, CartEntity entity, Runnable onDone) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);

            // If same product+variant already exists, increment quantity
            CartEntity existing = db.cartDao().getItem(
                    entity.userId, entity.productId, entity.variantName);

            if (existing != null) {
                existing.quantity += entity.quantity;
                existing.isSubscriptionItem = existing.isSubscriptionItem || entity.isSubscriptionItem;
                if (entity.subscriptionFrequency != null && !entity.subscriptionFrequency.trim().isEmpty()) {
                    existing.subscriptionFrequency = entity.subscriptionFrequency;
                }
                db.cartDao().update(existing);
            } else {
                db.cartDao().insert(entity);
            }

            // Mirror to Firestore
            if (entity.userId != null && !entity.userId.equals("guest")) {
                syncItemToFirestore(entity);
            }

            if (onDone != null) {
                // Post to main thread via simple handler trick
                android.os.Handler mainHandler = new android.os.Handler(
                        android.os.Looper.getMainLooper());
                mainHandler.post(onDone);
            }
        });
    }

    // ── Update quantity ───────────────────────────────────────────────────────
    public static void updateQuantity(Context context, CartEntity entity,
                                      int newQty, Runnable onDone) {
        Executors.newSingleThreadExecutor().execute(() -> {
            entity.quantity = newQty;
            AppDatabase.getInstance(context).cartDao().update(entity);

            if (entity.userId != null && !entity.userId.equals("guest")) {
                FirebaseFirestore.getInstance()
                        .collection("carts")
                        .document(entity.userId)
                        .collection("items")
                        .document(firestoreItemId(entity))
                        .update("quantity", newQty);
            }
            if (onDone != null) new android.os.Handler(
                    android.os.Looper.getMainLooper()).post(onDone);
        });
    }

    // ── Remove a single item ──────────────────────────────────────────────────
    public static void removeItem(Context context, CartEntity entity, Runnable onDone) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(context).cartDao().delete(entity);

            if (entity.userId != null && !entity.userId.equals("guest")) {
                FirebaseFirestore.getInstance()
                        .collection("carts")
                        .document(entity.userId)
                        .collection("items")
                        .document(firestoreItemId(entity))
                        .delete();
            }
            if (onDone != null) new android.os.Handler(
                    android.os.Looper.getMainLooper()).post(onDone);
        });
    }

    // ── Load cart items (Room → main thread callback) ─────────────────────────
    public static void getCartItems(Context context, String userId,
                                    Consumer<List<CartEntity>> callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<CartEntity> items = AppDatabase.getInstance(context)
                    .cartDao().getAllByUser(userId);
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> callback.accept(items));
        });
    }

    // ── Clear entire cart ─────────────────────────────────────────────────────
    public static void clearCart(Context context, String userId, Runnable onDone) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(context).cartDao().clearUserCart(userId);
            if (userId != null && !userId.equals("guest")) {
                FirebaseFirestore.getInstance()
                        .collection("carts")
                        .document(userId)
                        .collection("items")
                        .get()
                        .addOnSuccessListener(snap -> {
                            for (var doc : snap) doc.getReference().delete();
                        });
            }
            if (onDone != null) new android.os.Handler(
                    android.os.Looper.getMainLooper()).post(onDone);
        });
    }

    // ── Firestore helpers ─────────────────────────────────────────────────────
    private static void syncItemToFirestore(CartEntity entity) {
        Map<String, Object> data = new HashMap<>();
        data.put("productId", entity.productId);
        data.put("productName", entity.productName);
        data.put("variantName", entity.variantName);
        data.put("price", entity.price);
        data.put("quantity", entity.quantity);
        data.put("imageUrl", entity.imageUrl);
        data.put("isSubscriptionItem", entity.isSubscriptionItem);
        data.put("subscriptionFrequency", entity.subscriptionFrequency);

        FirebaseFirestore.getInstance()
                .collection("carts")
                .document(entity.userId)
                .collection("items")
                .document(firestoreItemId(entity))
                .set(data, SetOptions.merge())
                .addOnFailureListener(e ->
                        Log.e(TAG, "Firestore cart sync failed: " + e.getMessage()));
    }

    private static String firestoreItemId(CartEntity entity) {
        // Unique per product+variant combo
        return entity.productId + "_" + entity.variantName.replaceAll("\\s+", "_");
    }
}


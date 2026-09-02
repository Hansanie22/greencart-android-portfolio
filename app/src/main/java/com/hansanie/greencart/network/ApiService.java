package com.hansanie.greencart.network;

import com.google.gson.JsonObject;
import com.hansanie.greencart.dto.SubscriptionItemUpsertRequest;
import com.hansanie.greencart.dto.SubscriptionSaveRequest;
import com.hansanie.greencart.model.Address;
import com.hansanie.greencart.model.Category;
import com.hansanie.greencart.model.Farm;
import com.hansanie.greencart.model.GrocerySubscription;
import com.hansanie.greencart.model.GrocerySubscriptionItem;
import com.hansanie.greencart.model.SubscriptionOrder;
import com.hansanie.greencart.model.Offer;
import com.hansanie.greencart.model.Order;
import com.hansanie.greencart.model.Payment;
import com.hansanie.greencart.model.PaymentCard;
import com.hansanie.greencart.model.Product;
import com.hansanie.greencart.model.ProductStockRequest;
import com.hansanie.greencart.model.ProductStockSummary;
import com.hansanie.greencart.model.ProductVariant;
import com.hansanie.greencart.model.SupportMessageSyncRequest;
import com.hansanie.greencart.model.User;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    @POST("api/users/register")
    Call<User> registerUser(@Body User user);

    @GET("api/products/{id}/variants")
    Call<List<ProductVariant>> getVariantsByProductId(@Path("id") Long id);

    @GET("api/products/{id}/details")
    Call<Product> getProductDetails(@Path("id") Long id);

    @GET("api/products/category/{categoryId}")
    Call<List<Product>> getProductsByCategoryId(@Path("categoryId") Long categoryId);

    @GET("api/products/category")
    Call<List<Product>> getProductsByCategoryName(@Query("name") String categoryName);

    @GET("api/categories")
    Call<List<Category>> getCategories();

    @GET("api/products/catalog")
    Call<List<JsonObject>> getCatalogProducts(
            @Query("q") String query,
            @Query("categoryId") Long categoryId,
            @Query("sort") String sort,
            @Query("stockOnly") Boolean stockOnly,
            @Query("dealsOnly") Boolean dealsOnly,
            @Query("minPrice") Double minPrice,
            @Query("maxPrice") Double maxPrice
    );

    @POST("api/products/catalog")
    Call<List<JsonObject>> getCatalogProductsPost(
            @Query("q") String query,
            @Query("categoryId") Long categoryId,
            @Query("sort") String sort,
            @Query("stockOnly") Boolean stockOnly,
            @Query("dealsOnly") Boolean dealsOnly,
            @Query("minPrice") Double minPrice,
            @Query("maxPrice") Double maxPrice
    );

    @GET("api/offers/available/{firebaseUid}")
    Call<List<Offer>> getAvailableOffersForUser(@Path("firebaseUid") String firebaseUid);

    @POST("api/offers/claim")
    Call<Object> claimOffer(
            @Query("firebaseUid") String firebaseUid,
            @Query("offerId") Long offerId
    );

    @POST("api/addresses/save")
    Call<Address> saveAddress(@Body Address address);

    @GET("api/addresses/user/{firebaseUid}")
    Call<List<Address>> getUserAddresses(@Path("firebaseUid") String firebaseUid);

    @POST("api/addresses/delete/{id}")
    Call<Void> deleteAddress(@Path("id") String addressId);

    @GET("api/farms/{id}")
    Call<Farm> getFarmById(@Path("id") Long id);

    @POST("api/orders/save")
    Call<Order> saveOrderToMySql(@Body Order orderData);

    @POST("api/payments/save")
    Call<Payment> savePayment(@Body Payment payment);

    @POST("api/reviews/save")
    Call<Void> saveReviewToMySql(@Body Map<String, Object> review);

    @GET("api/cards/user/{firebaseUid}")
    Call<List<PaymentCard>> getSavedCards(@Path("firebaseUid") String firebaseUid);

    @POST("api/cards/save")
    Call<PaymentCard> saveCard(@Body PaymentCard card);

    @DELETE("api/cards/{id}")
    Call<Void> deleteCard(@Path("id") Long id);

    @GET("api/stocks/product/{productId}/variant/{variantId}")
    Call<List<ProductStockSummary>> getStockBatchesByProductAndVariant(
            @Path("productId") Long productId,
            @Path("variantId") Long variantId
    );

    @POST("api/stocks")
    Call<ProductStockSummary> createStockBatch(@Body ProductStockRequest request);

    @POST("api/stocks/reduce")
    Call<Void> reduceVariantStock(@Body ProductStockRequest request);

    @POST("api/subscriptions/save")
    Call<GrocerySubscription> saveGrocerySubscription(@Body SubscriptionSaveRequest subscription);

    @POST("api/subscriptions/orders")
    Call<Void> saveSubscriptionOrder(@Body SubscriptionOrder subscriptionOrder);

    // Items endpoint ත් update
    @POST("api/subscriptions/{id}/items")
    Call<Void> updateSubscriptionItems(
            @Path("id") Long subscriptionId,
            @Body List<SubscriptionItemUpsertRequest> items  // Android DTO
    );
    @POST("api/subscriptions/{id}/status")
    Call<Void> updateSubscriptionStatus(
            @Path("id") Long subscriptionId,
            @Query("status") String status
    );


    @POST("api/subscriptions/{id}/skip-next")
    Call<Void> updateSkipNext(
            @Path("id") Long subscriptionId,
            @Query("skip") boolean skip
    );

    @POST("api/support/messages/save")
    Call<Void> saveSupportMessage(@Body SupportMessageSyncRequest request);

    @POST("api/users/fcm-token")
    Call<Void> updateFcmToken(
            @Query("firebaseUid") String firebaseUid,
            @Query("fcmToken") String fcmToken
    );

    @GET("api/subscriptions/user/{firebaseUid}/products")
    Call<List<Product>> getSubscribedProductsWithVariants(@Path("firebaseUid") String firebaseUid);

    @POST("api/orders/{id}/status")
    Call<Void> updateOrderStatus(
        @Path("id") Long id,
        @Query("status") String status
    );

    @POST("api/users/update")
    Call<User> updateUser(@Body User user);

    @PATCH("subscriptions/{id}/next-delivery-date")
    Call<Void> updateNextDeliveryDate(
            @Path("id") long subscriptionId,
            @Query("date") String newDate
    );
}

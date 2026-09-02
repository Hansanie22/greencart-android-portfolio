package com.hansanie.greencart.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.hansanie.greencart.model.GrocerySubscription;

import java.util.List;

@Dao
public interface SubscriptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<GrocerySubscription> subscriptions);

    @Query("SELECT * FROM grocery_subscriptions ORDER BY next_delivery_date ASC")
    List<GrocerySubscription> getAll();

    @Query("DELETE FROM grocery_subscriptions")
    void clearAll();
}


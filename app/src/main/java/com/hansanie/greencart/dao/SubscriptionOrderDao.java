package com.hansanie.greencart.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.OnConflictStrategy;

import com.hansanie.greencart.model.SubscriptionOrder;

import java.util.List;

@Dao
public interface SubscriptionOrderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SubscriptionOrder subscriptionOrder);

    @Query("SELECT * FROM subscription_orders ORDER BY id DESC")
    List<SubscriptionOrder> getAll();

    @Query("DELETE FROM subscription_orders")
    void clearAll();
}


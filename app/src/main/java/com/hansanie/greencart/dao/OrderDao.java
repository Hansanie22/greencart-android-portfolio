package com.hansanie.greencart.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.hansanie.greencart.model.Order;

import java.util.List;

@Dao
public interface OrderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Order> orders);

    @Query("SELECT * FROM orders ORDER BY COALESCE(order_date, created_at) DESC")
    List<Order> getAll();

    @Query("SELECT * FROM orders WHERE UPPER(COALESCE(status, order_status)) NOT IN ('DELIVERED', 'CANCELLED', 'CANCELED') ORDER BY COALESCE(order_date, created_at) DESC")
    List<Order> getOngoing();

    @Query("SELECT * FROM orders WHERE UPPER(COALESCE(status, order_status)) IN ('DELIVERED', 'CANCELLED', 'CANCELED') ORDER BY COALESCE(order_date, created_at) DESC")
    List<Order> getHistory();

    @Query("DELETE FROM orders")
    void clearAll();
}


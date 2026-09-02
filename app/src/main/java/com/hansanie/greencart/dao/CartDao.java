package com.hansanie.greencart.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.hansanie.greencart.model.CartEntity;

import java.util.List;

@Dao
public interface CartDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CartEntity item);

    @Update
    void update(CartEntity item);

    @Delete
    void delete(CartEntity item);

    @Query("SELECT * FROM cart WHERE userId = :userId")
    List<CartEntity> getAllByUser(String userId);

    @Query("SELECT * FROM cart WHERE userId = :userId AND productId = :productId AND variantName = :variantName LIMIT 1")
    CartEntity getItem(String userId, long productId, String variantName);

    @Query("UPDATE cart SET quantity = :qty WHERE id = :id")
    void updateQuantity(int id, int qty);

    @Query("DELETE FROM cart WHERE userId = :userId")
    void clearUserCart(String userId);

    @Query("SELECT COUNT(*) FROM cart WHERE userId = :userId")
    int getCartCount(String userId);
}


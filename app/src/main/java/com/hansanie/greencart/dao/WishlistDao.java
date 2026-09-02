package com.hansanie.greencart.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.hansanie.greencart.model.Wishlist;
import java.util.List;

@Dao
public interface WishlistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertWishlist(Wishlist wishlist);

    // ලොග් වී සිටින User ගේ Wishlist එක පමණක් ලබා ගැනීමට
    @Query("SELECT * FROM wishlist WHERE userId = :userId")
    List<Wishlist> getWishlistByUser(String userId);

    // අදාළ User ගේ Wishlist එකේ මේ Product එක තිබේදැයි බැලීමට
    @Query("SELECT EXISTS(SELECT 1 FROM wishlist WHERE productId = :id AND userId = :userId)")
    boolean isInWishlist(Long id, String userId);

    @Delete
    void deleteWishlist(Wishlist wishlist);
}
package com.hansanie.greencart.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.hansanie.greencart.model.UserOffer;
import java.util.List;

@Dao
public interface UserOfferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(UserOffer userOffer);

    @Query("SELECT * FROM user_offers WHERE user_id = :userId AND offer_id = :offerId LIMIT 1")
    UserOffer getUserOffer(long userId, long offerId);

    @Query("SELECT * FROM user_offers")
    List<UserOffer> getAllUserOffers();
}



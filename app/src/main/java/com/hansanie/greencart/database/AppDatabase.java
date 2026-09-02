package com.hansanie.greencart.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.hansanie.greencart.dao.CartDao;
import com.hansanie.greencart.dao.NotificationDao;
import com.hansanie.greencart.dao.OrderDao;
import com.hansanie.greencart.dao.SubscriptionDao;
import com.hansanie.greencart.dao.SubscriptionOrderDao;
import com.hansanie.greencart.dao.UserOfferDao;
import com.hansanie.greencart.dao.WishlistDao;
import com.hansanie.greencart.model.CartEntity;
import com.hansanie.greencart.model.GrocerySubscription;
import com.hansanie.greencart.model.NotificationItem;
import com.hansanie.greencart.model.Order;
import com.hansanie.greencart.model.UserOffer;
import com.hansanie.greencart.model.Wishlist;
import com.hansanie.greencart.model.SubscriptionOrder;

@Database(entities = {Wishlist.class, CartEntity.class, NotificationItem.class, Order.class, GrocerySubscription.class, UserOffer.class, SubscriptionOrder.class}, version = 20, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
        }
    };

    private static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add new columns introduced in app schema changes. Use nullable columns to avoid breaking existing rows.
            try {
                database.execSQL("ALTER TABLE orders ADD COLUMN offer_id INTEGER");
            } catch (Exception ignored) {}
            try {
                database.execSQL("ALTER TABLE orders ADD COLUMN offer_percentage REAL");
            } catch (Exception ignored) {}
            try {
                database.execSQL("ALTER TABLE orders ADD COLUMN promo_offer_id INTEGER");
            } catch (Exception ignored) {}
            try {
                database.execSQL("ALTER TABLE orders ADD COLUMN promo_discount_percent REAL");
            } catch (Exception ignored) {}
            try {
                database.execSQL("ALTER TABLE orders ADD COLUMN promo_discount REAL");
            } catch (Exception ignored) {}
            try {
                database.execSQL("ALTER TABLE orders ADD COLUMN subscription_discount REAL");
            } catch (Exception ignored) {}
        }
    };

    private static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add destination and payload columns to notifications table to support click actions
            try {
                database.execSQL("ALTER TABLE notifications ADD COLUMN destination TEXT");
            } catch (Exception ignored) {}
            try {
                database.execSQL("ALTER TABLE notifications ADD COLUMN payload TEXT");
            } catch (Exception ignored) {}
        }
    };

    private static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE orders ADD COLUMN is_subscription INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE grocery_subscriptions ADD COLUMN skip_next INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {

            database.execSQL("DROP TABLE IF EXISTS `orders`");
            database.execSQL("CREATE TABLE IF NOT EXISTS `orders` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "`user_id` INTEGER, "
                    + "`address_id` TEXT, "
                    + "`total_amount` REAL, "
                    + "`order_status` TEXT, "
                    + "`payment_status` TEXT, "
                    + "`order_date` TEXT, "
                    + "`created_at` TEXT, "
                    + "`discount_amount` REAL, "
                    + "`subtotal` REAL, "
                    + "`shipping` REAL, "
                    + "`green_points_redeemed` INTEGER, "
                    + "`green_points_redeem_value` REAL, "
                    + "`notes` TEXT, "
                    + "`promo_code` TEXT, "
                    + "`status` TEXT, "
                    + "`order_code` TEXT, "
                    + "`subscription_id` INTEGER, "
                    + "`firebase_uid` TEXT, "
                    + "`delivery_address` TEXT, "
                    + "`delivery_latitude` REAL, "
                    + "`delivery_longitude` REAL, "
                    + "`hub_name` TEXT, "
                    + "`hub_latitude` REAL, "
                    + "`hub_longitude` REAL, "
                    + "`farm_name` TEXT, "
                    + "`farm_address` TEXT, "
                    + "`farm_latitude` REAL, "
                    + "`farm_longitude` REAL, "
                    + "`rider_name` TEXT, "
                    + "`rider_phone` TEXT, "
                    + "`support_phone` TEXT, "
                    + "`estimated_arrival` TEXT, "
                    + "`pending_at` TEXT, "
                    + "`confirmed_at` TEXT, "
                    + "`out_for_delivery_at` TEXT, "
                    + "`delivered_at` TEXT, "
                    + "`arrival_notified` INTEGER NOT NULL DEFAULT 0, "
                    + "`green_points_earned` INTEGER)");
        }
    };

    private static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS user_offers");
            database.execSQL("CREATE TABLE user_offers (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id INTEGER, " +
                    "offer_id INTEGER, " +
                    "claimed_at INTEGER, " +
                    "firebaseUid TEXT, " +
                    "promoCode TEXT, " +
                    "status TEXT, " +
                    "used INTEGER"
                    + ")");
        }
    };

    public abstract WishlistDao wishlistDao();
    public abstract CartDao cartDao();
    public abstract NotificationDao notificationDao();
    public abstract OrderDao orderDao();
    public abstract SubscriptionDao subscriptionDao();
    public abstract SubscriptionOrderDao subscriptionOrderDao();
    public abstract UserOfferDao userOfferDao();

    private static volatile AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "greencart_db"
                    )
                    .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_17_18, MIGRATION_18_19)
                    .fallbackToDestructiveMigration(true)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build();
        }
        return instance;
    }
}
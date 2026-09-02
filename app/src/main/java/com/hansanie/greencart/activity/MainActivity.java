package com.hansanie.greencart.activity;

import android.content.BroadcastReceiver;
import android.util.Log;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.WorkManager;

import com.bumptech.glide.Glide;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.hansanie.greencart.R;
import com.hansanie.greencart.database.AppDatabase;
import com.hansanie.greencart.fragment.CartFragment;
import com.hansanie.greencart.fragment.GreenPointsFragment;
import com.hansanie.greencart.fragment.HomeFragment;
import com.hansanie.greencart.fragment.NotificationsFragment;
import com.hansanie.greencart.fragment.OrdersFragment;
import com.hansanie.greencart.fragment.ProfileFragment;
import com.hansanie.greencart.fragment.ProductFragment;
import com.hansanie.greencart.fragment.SettingFragment;
import com.hansanie.greencart.fragment.SubscriptionFragment;
import com.hansanie.greencart.fragment.SupportHelpFragment;
import com.hansanie.greencart.fragment.WishlistFragment;
import com.hansanie.greencart.network.FcmTokenRegistrar;
import com.hansanie.greencart.network.MyFirebaseMessagingService;
import com.hansanie.greencart.util.CustomToast;
import com.hansanie.greencart.util.NotificationHelper;
import com.hansanie.greencart.util.ThemePreferenceManager;
import com.hansanie.greencart.worker.SubscriptionReminderWorker;

import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private BottomNavigationView bottomNavigationView;

    private ImageView menuIcon;
    private FrameLayout notificationIcon;
    private TextView notificationBadge;            // ← was View, now TextView
    private ShapeableImageView userIconToolbar;

    private ImageView profileImage;
    private TextView userName, userEmail;
    private MaterialButton editProfileButton;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
     private ListenerRegistration cartBadgeListener;
     private ListenerRegistration userProfileListener;
     private ListenerRegistration supportMessagesListener;

    // ── BroadcastReceiver for notification count ──────────────────────────────
    private final BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                runOnUiThread(() -> refreshNotificationBadge());
            } catch (Exception e) {
                Log.w("MainActivity", "Failed to refresh notification badge", e);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemePreferenceManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        );

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser signedInUser = mAuth.getCurrentUser();
        if (signedInUser != null) {
            FcmTokenRegistrar.syncToken(getApplicationContext(), signedInUser.getUid());
            scheduleSubscriptionReminderChecks();
        } else {
            cancelSubscriptionReminderChecks();
        }

        initViews();
        setupDrawer();
        setupBottomNavigation();
        setupToolbarClicks();
        loadUserData();

        // Receiver will be registered in onStart()/onStop() to ensure single registration

        boolean handledNotificationRoute = handleNotificationNavigation(getIntent());
        if (savedInstanceState == null && !handledNotificationRoute) {
            loadFragment(new HomeFragment());
            syncMenus(R.id.nav_home);
        }

        refreshNotificationBadge();  // Show any existing unread count on startup

        // Handle back press: close drawer first if open
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationNavigation(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNotificationBadge();
        startCartBadgeListener();
        startSupportMessagesListener();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCartBadgeListener();
        stopSupportMessagesListener();
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            LocalBroadcastManager.getInstance(this).registerReceiver(
                    notificationReceiver,
                    new IntentFilter(MyFirebaseMessagingService.ACTION_NOTIFICATION_COUNT_UPDATED));
        } catch (Exception ignored) {}
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationReceiver);
        } catch (Exception ignored) {}
    }

    private void startSupportMessagesListener() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();
        if (supportMessagesListener != null) return;

        // ── Track already-notified message IDs to prevent duplicates ─────
        java.util.Set<String> notifiedMessageIds = new java.util.HashSet<>();
        // ─────────────────────────────────────────────────────────────────

        supportMessagesListener = db.collectionGroup("messages")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    for (com.google.firebase.firestore.DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() != com.google.firebase.firestore.DocumentChange.Type.ADDED) continue;
                        com.google.firebase.firestore.DocumentSnapshot doc = dc.getDocument();

                        // ── Duplicate guard ───────────────────────────────────────
                        String msgId = doc.getId();
                        if (notifiedMessageIds.contains(msgId)) continue;
                        notifiedMessageIds.add(msgId);
                        // ─────────────────────────────────────────────────────────

                        String path = doc.getReference().getPath();
                        String userPrefix = "users/" + uid + "/support_chats/";
                        if (!path.contains(userPrefix)) continue;

                        try {
                            com.hansanie.greencart.model.ChatMessage msg =
                                    doc.toObject(com.hansanie.greencart.model.ChatMessage.class);
                            if (msg == null) continue;
                            if (!msg.isSupportAgent()) continue;
                            if (uid.equals(msg.getSenderId())) continue;

                            String[] parts = path.split("/");
                            String conversationId = null;
                            for (int i = 0; i < parts.length - 1; i++) {
                                if ("support_chats".equals(parts[i]) && i + 1 < parts.length) {
                                    conversationId = parts[i + 1];
                                    break;
                                }
                            }

                            NotificationHelper.showSupportChatNotification(
                                    MainActivity.this,
                                    msg.getSenderName() != null ? msg.getSenderName() : "GreenCart Support",
                                    msg.getMessage(),
                                    conversationId,
                                    null,
                                    null,
                                    msg.getId()
                            );
                        } catch (Exception ignored) {}
                    }
                });
    }

    private void stopSupportMessagesListener() {
        if (supportMessagesListener != null) {
            try { supportMessagesListener.remove(); } catch (Exception ignored) {}
            supportMessagesListener = null;
        }
    }

    /** Read unread count from Room on a background thread, then update badge UI. */
    public void refreshNotificationBadge() {
        com.hansanie.greencart.util.AppExecutors.DB.execute(() -> {
            int count = AppDatabase.getInstance(getApplicationContext())
                    .notificationDao().getUnreadCount();
            runOnUiThread(() -> {
                if (notificationBadge == null) return;
                if (count > 0) {
                    notificationBadge.setVisibility(View.VISIBLE);
                    notificationBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                } else {
                    notificationBadge.setVisibility(View.GONE);
                }
            });
        });
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        bottomNavigationView = findViewById(R.id.bottomNavigation);

        View toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            menuIcon = toolbar.findViewById(R.id.menuIcon);
            notificationIcon = toolbar.findViewById(R.id.notification);
            notificationBadge = toolbar.findViewById(R.id.notificationBadge);
            userIconToolbar = toolbar.findViewById(R.id.userIcon);
        }

        View headerView = navigationView.getHeaderView(0);
        profileImage = headerView.findViewById(R.id.profileImage);
        userName = headerView.findViewById(R.id.userName);
        userEmail = headerView.findViewById(R.id.userEmail);
        editProfileButton = headerView.findViewById(R.id.editProfileButton);
    }

     private void loadUserData() {
         FirebaseUser currentUser = mAuth.getCurrentUser();
         updateDrawerMenuForUser(currentUser);

         // Remove previous listener if any
         if (userProfileListener != null) {
             userProfileListener.remove();
             userProfileListener = null;
         }

         if (currentUser != null) {
             scheduleSubscriptionReminderChecks();
             userProfileListener = db.collection("users").document(currentUser.getUid())
                 .addSnapshotListener((doc, error) -> {
                     if (error != null || doc == null || !doc.exists()) {
                         // fallback to default
                         userName.setText("User");
                         userEmail.setText("N/A");
                         loadProfileImages(null);
                         return;
                     }
                     String fName = doc.getString("first_name");
                     String lName = doc.getString("last_name");
                     String email = doc.getString("email");
                     String phone = doc.getString("phone");
                     String profileImageUrl = doc.getString("profile_image");

                     userName.setText((fName != null && lName != null) ? fName + " " + lName : "User");
                     userEmail.setText((email != null && !email.isEmpty()) ? email : (phone != null ? phone : "N/A"));
                     loadProfileImages(profileImageUrl);
                 });
             startCartBadgeListener();

             editProfileButton.setText("Edit Profile");
             editProfileButton.setOnClickListener(v -> {
                 loadFragment(new ProfileFragment());
                 syncMenus(R.id.nav_profile);
                 drawerLayout.closeDrawer(GravityCompat.START);
             });

         } else {
             cancelSubscriptionReminderChecks();
             // Guest user
             userName.setText("Guest");
             userEmail.setText("Login to sync your account");
             loadProfileImages((String) null);
             updateCartBadge(0);
             stopCartBadgeListener();
             editProfileButton.setText("Login Now");
             editProfileButton.setOnClickListener(v -> redirectToAuth());
         }
     }

    private void updateImageUI(ImageView imageView, String url, int paddingDp) {
        if (imageView == null) return;

        imageView.setPadding(paddingDp, paddingDp, paddingDp, paddingDp);
        imageView.setImageTintList(null);
        imageView.setBackgroundTintList(null);

        Glide.with(this)
                .load(url)
                .circleCrop()
                .placeholder(R.drawable.ic_user)
                .error(R.drawable.ic_user)
                .into(imageView);
    }

    private void loadProfileImages(String profileImageUrl) {
        int drawerPadding = (int) (8 * getResources().getDisplayMetrics().density);
        int toolbarPadding = (int) (6 * getResources().getDisplayMetrics().density);

        // Drawer profile icon (unchanged)
        if (profileImage != null) {
            if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                Glide.with(this)
                        .load(profileImageUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_user)
                        .error(R.drawable.ic_user)
                        .into(profileImage);
                profileImage.setBackground(null);
                profileImage.setImageTintList(null);
                profileImage.setPadding(drawerPadding, drawerPadding, drawerPadding, drawerPadding);
            } else {
                profileImage.setImageResource(R.drawable.ic_user);
                profileImage.setImageTintList(ColorStateList.valueOf(
                        getResources().getColor(R.color.md_theme_primary, getTheme())));
                profileImage.setBackground(null);
                profileImage.setPadding(drawerPadding, drawerPadding, drawerPadding, drawerPadding);
            }
        }

        // Toolbar user icon — FIX: remove manual setAllCornerSizes(), let XML style handle it
        if (userIconToolbar != null) {
            // Force circle via ShapeAppearance — don't rely on height at runtime
            userIconToolbar.setShapeAppearanceModel(
                    com.google.android.material.shape.ShapeAppearanceModel.builder()
                            .setAllCornerSizes(new com.google.android.material.shape.RelativeCornerSize(0.5f))
                            .build()
            );

            if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                Glide.with(this)
                        .load(profileImageUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_user)
                        .error(R.drawable.ic_user)
                        .into(userIconToolbar);
                userIconToolbar.setBackground(null);
                userIconToolbar.setImageTintList(null);
                userIconToolbar.setPadding(toolbarPadding, toolbarPadding, toolbarPadding, toolbarPadding);
            } else {
                userIconToolbar.setImageResource(R.drawable.ic_user);
                userIconToolbar.setImageTintList(ColorStateList.valueOf(
                        getResources().getColor(R.color.md_theme_primary, getTheme())));
                userIconToolbar.setBackground(null);
                userIconToolbar.setPadding(toolbarPadding, toolbarPadding, toolbarPadding, toolbarPadding);
            }
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userProfileListener != null) {
            userProfileListener.remove();
            userProfileListener = null;
        }
    }

    private void updateDrawerMenuForUser(FirebaseUser user) {
        boolean loggedIn = (user != null);
        Menu menu = navigationView.getMenu();

        // nav_subscription intentionally not included here so the Subscription menu is visible
        // even for users who haven't placed an order yet (minimal change requested).
        int[] authItems = {R.id.nav_orders, R.id.nav_favorites, R.id.nav_cart, R.id.nav_logout, R.id.nav_profile, R.id.nav_green_points, R.id.nav_my_rewards, R.id.nav_support_help};
        for (int id : authItems) {
            MenuItem item = menu.findItem(id);
            if (item != null) item.setVisible(loggedIn);
        }

        // Ensure subscription menu is visible (even for users without prior orders).
        MenuItem subItem = menu.findItem(R.id.nav_subscription);
        if (subItem != null) subItem.setVisible(true);

        MenuItem loginItem = menu.findItem(R.id.nav_login);
        if (loginItem != null) loginItem.setVisible(!loggedIn);
    }

    private void setupToolbarClicks() {
        if (notificationIcon != null) {
            notificationIcon.setOnClickListener(v -> {
                try {
                    Log.d("MainActivity", "Notification icon clicked");
                    loadFragment(new NotificationsFragment());
                    syncMenus(-1);
                } catch (Exception e) {
                    Log.w("MainActivity", "Failed to open NotificationsFragment", e);
                }
            });
        }

        if (userIconToolbar != null) {
            userIconToolbar.setOnClickListener(v -> {
                if (mAuth.getCurrentUser() == null) redirectToAuth();
                else {
                    loadFragment(new ProfileFragment());
                    syncMenus(R.id.nav_profile);
                }
            });
        }
    }

    private void setupDrawer() {
        if (menuIcon != null) {
            menuIcon.setOnClickListener(v -> {
                if (drawerLayout.isDrawerOpen(GravityCompat.START))
                    drawerLayout.closeDrawer(GravityCompat.START);
                else drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        navigationView.setNavigationItemSelectedListener(item -> {
            handleNavigation(item.getItemId());
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            handleNavigation(item.getItemId());
            return true;
        });
    }

    private boolean handleNotificationNavigation(Intent intent) {
        if (intent == null) {
            return false;
        }

        String destination = intent.getStringExtra(NotificationHelper.EXTRA_OPEN_DESTINATION);
        if (TextUtils.equals(NotificationHelper.DEST_SUPPORT_CHAT, destination)) {
            SupportHelpFragment fragment = new SupportHelpFragment();
            Bundle args = new Bundle();
            String conversationId = intent.getStringExtra(NotificationHelper.EXTRA_CONVERSATION_ID);
            String orderId = intent.getStringExtra(NotificationHelper.EXTRA_ORDER_ID);
            String supportPhone = intent.getStringExtra(NotificationHelper.EXTRA_SUPPORT_PHONE);

            if (!TextUtils.isEmpty(conversationId)) {
                args.putString("conversationId", conversationId);
            }
            if (!TextUtils.isEmpty(orderId)) {
                args.putString("orderId", orderId);
            }
            if (!TextUtils.isEmpty(supportPhone)) {
                args.putString("supportPhone", supportPhone);
            }
            fragment.setArguments(args);

            loadFragment(fragment);
            syncMenus(R.id.nav_support_help);

            intent.removeExtra(NotificationHelper.EXTRA_OPEN_DESTINATION);
            intent.removeExtra(NotificationHelper.EXTRA_CONVERSATION_ID);
            intent.removeExtra(NotificationHelper.EXTRA_ORDER_ID);
            intent.removeExtra(NotificationHelper.EXTRA_SUPPORT_PHONE);
            return true;
        }

        if (TextUtils.equals(NotificationHelper.DEST_SUBSCRIPTIONS, destination)) {
            loadFragment(new SubscriptionFragment());
            syncMenus(R.id.nav_subscription);

            intent.removeExtra(NotificationHelper.EXTRA_OPEN_DESTINATION);
            return true;
        }

        if (!TextUtils.equals(NotificationHelper.DEST_ORDERS, destination)) {
            return false;
        }

        loadFragment(new OrdersFragment());
        syncMenus(R.id.nav_orders);

        intent.removeExtra(NotificationHelper.EXTRA_OPEN_DESTINATION);
        intent.removeExtra(NotificationHelper.EXTRA_ORDER_ID);
        return true;
    }

    private void startCartBadgeListener() {
        stopCartBadgeListener();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            updateCartBadge(0);
            return;
        }

        cartBadgeListener = db.collection("carts")
                .document(user.getUid())
                .collection("items")
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) {
                        return;
                    }

                    int count = 0;
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snap) {
                        Long qty = doc.getLong("quantity");
                        count += (qty != null && qty > 0) ? qty.intValue() : 1;
                    }
                    updateCartBadge(count);
                });
    }

    private void stopCartBadgeListener() {
        if (cartBadgeListener != null) {
            cartBadgeListener.remove();
            cartBadgeListener = null;
        }
    }

    private void updateCartBadge(int count) {
        if (bottomNavigationView == null) return;

        if (count > 0) {
            BadgeDrawable badge = bottomNavigationView.getOrCreateBadge(R.id.nav_cart);
            badge.setVisible(true);
            badge.setMaxCharacterCount(3);
            badge.setNumber(Math.min(count, 999));
        } else {
            bottomNavigationView.removeBadge(R.id.nav_cart);
        }
    }

    private void handleNavigation(int itemId) {
        FirebaseUser user = mAuth.getCurrentUser();

        // Restrict access for guest users
        if (user == null && (itemId == R.id.nav_cart || itemId == R.id.nav_orders ||
                itemId == R.id.nav_profile || itemId == R.id.nav_favorites || itemId == R.id.nav_subscription
                || itemId == R.id.nav_green_points)) {
            CustomToast.showInfo(this, "Please login to access this feature");
            redirectToAuth();
            return;
        }

        Fragment fragment = null;

        if (itemId == R.id.nav_home) {
            fragment = new HomeFragment();
        } else if (itemId == R.id.nav_products) {
            fragment = new ProductFragment();
        } else if (itemId == R.id.nav_cart) {
            fragment = new CartFragment();
        } else if (itemId == R.id.nav_orders && navigationView.getMenu().findItem(R.id.nav_orders) != null) {
            fragment = new OrdersFragment();
        } else if (itemId == R.id.nav_profile) {
            fragment = new ProfileFragment();
        } else if (itemId == R.id.nav_favorites && navigationView.getMenu().findItem(R.id.nav_favorites) != null) {
            fragment = new WishlistFragment();
        } else if (itemId == R.id.nav_subscription && navigationView.getMenu().findItem(R.id.nav_subscription) != null) {
            fragment = new SubscriptionFragment();
        } else if (itemId == R.id.nav_settings && navigationView.getMenu().findItem(R.id.nav_settings) != null) {
            fragment = new SettingFragment();
        } else if (itemId == R.id.nav_support_help && navigationView.getMenu().findItem(R.id.nav_support_help) != null) {
            fragment = new SupportHelpFragment();
        } else if (itemId == R.id.nav_green_points && navigationView.getMenu().findItem(R.id.nav_green_points) != null) {
            fragment = new GreenPointsFragment();
        } else if (itemId == R.id.nav_logout && navigationView.getMenu().findItem(R.id.nav_logout) != null) {
            handleLogout();
            return;
        } else if (itemId == R.id.nav_login && navigationView.getMenu().findItem(R.id.nav_login) != null) {
            redirectToAuth();
            return;
        } else if (itemId == R.id.nav_my_rewards && navigationView.getMenu().findItem(R.id.nav_my_rewards) != null) {
            fragment = new com.hansanie.greencart.fragment.MyRewardsFragment();
        }

        if (fragment != null) {
            loadFragment(fragment);
            syncMenus(itemId);
        }
    }

    private void syncMenus(int itemId) {
        // Reset BottomNavigation selection
        for (int i = 0; i < bottomNavigationView.getMenu().size(); i++) {
            MenuItem mi = bottomNavigationView.getMenu().getItem(i);
            mi.setCheckable(true);
            mi.setChecked(false);
        }

        // Reset Drawer selection
        for (int i = 0; i < navigationView.getMenu().size(); i++) {
            MenuItem mi = navigationView.getMenu().getItem(i);
            mi.setCheckable(true);
            mi.setChecked(false);
        }

        // Set selected
        MenuItem drawerItem = navigationView.getMenu().findItem(itemId);
        if (drawerItem != null) drawerItem.setChecked(true);

        MenuItem bottomItem = bottomNavigationView.getMenu().findItem(itemId);
        if (bottomItem != null) bottomItem.setChecked(true);
    }

    public void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.commit();
    }

    private void scheduleSubscriptionReminderChecks() {
        WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(
                SubscriptionReminderWorker.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                SubscriptionReminderWorker.newPeriodicRequest()
        );
    }

    private void cancelSubscriptionReminderChecks() {
        WorkManager.getInstance(getApplicationContext())
                .cancelUniqueWork(SubscriptionReminderWorker.UNIQUE_WORK_NAME);
    }

    private void handleLogout() {
        stopCartBadgeListener();
        updateCartBadge(0);
        cancelSubscriptionReminderChecks();
        mAuth.signOut();
        CustomToast.showSuccess(this, "Logged out successfully");
        loadFragment(new HomeFragment());
        syncMenus(R.id.nav_home);
        loadUserData();
    }

    private void redirectToAuth() {
        startActivity(new Intent(this, AuthActivity.class));
    }
}
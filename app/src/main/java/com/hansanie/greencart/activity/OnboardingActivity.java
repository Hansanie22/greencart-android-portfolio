package com.hansanie.greencart.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.hansanie.greencart.R;
import com.hansanie.greencart.adapter.OnboardingAdapter;
import com.hansanie.greencart.model.OnboardingItem;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private OnboardingAdapter onboardingAdapter;
    private MaterialButton btnNext;
    private TextView btnSkip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_onboarding);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupOnboardingItems();
        setupListeners();
    }

    private void initViews() {
        viewPager = findViewById(R.id.viewPager);
        btnNext = findViewById(R.id.btnNext);
        btnSkip = findViewById(R.id.btnSkip);
    }

    private void setupOnboardingItems() {
        List<OnboardingItem> onboardingItems = new ArrayList<>();

        // 🍎 Screen 1
        onboardingItems.add(new OnboardingItem(
                R.drawable.fresh, // පින්තූරය පරීක්ෂා කරන්න
                "Freshly Picked",
                "We deliver organic vegetables and fruits directly from our farm to your kitchen."
        ));

        // 🍎 Screen 2
        onboardingItems.add(new OnboardingItem(
                R.drawable.fast, // දෙවන පින්තූරය මෙතනට දාන්න
                "Fast Delivery",
                "Experience lighting fast delivery within 2 hours at your doorstep."
        ));

        // 🍎 Screen 3
        onboardingItems.add(new OnboardingItem(
                R.drawable.payment, // තෙවන පින්තූරය මෙතනට දාන්න
                "Secure Payments",
                "Safe and secure payment methods for a seamless shopping experience."
        ));

        onboardingAdapter = new OnboardingAdapter(onboardingItems);
        viewPager.setAdapter(onboardingAdapter);

        // 🍎 Dots Indicator Setup
        TabLayout tabLayout = findViewById(R.id.tabIndicator);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            // Dots වැඩ කරන්නේ මෙතනින්
        }).attach();
    }

    private void setupListeners() {
        // Next Button Click logic
        btnNext.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() + 1 < onboardingAdapter.getItemCount()) {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            } else {
                startHomeActivity();
            }
        });

        // Skip Button Click
        btnSkip.setOnClickListener(v -> startHomeActivity());

        // ViewPager change listener - බොත්තමේ නම මාරු කිරීමට
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (position == onboardingAdapter.getItemCount() - 1) {
                    btnNext.setText("Get Started");
                    btnSkip.setVisibility(View.GONE);
                } else {
                    btnNext.setText("Next");
                    btnSkip.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void startHomeActivity() {
         Intent intent = new Intent(OnboardingActivity.this, AuthActivity.class);
         startActivity(intent);
         finish();
    }
}
package com.hansanie.greencart.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hansanie.greencart.R;
import com.hansanie.greencart.model.OnboardingItem;

import java.util.List;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder> {

    private List<OnboardingItem> onboardingItems;

    public OnboardingAdapter(List<OnboardingItem> onboardingItems) {
        this.onboardingItems = onboardingItems;
    }

    @NonNull
    @Override
    public OnboardingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.item_onboarding, parent, false
        );
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        view.setLayoutParams(params);
        return new OnboardingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OnboardingViewHolder holder, int position) {
        holder.setOnboardingData(onboardingItems.get(position));
    }

    @Override
    public int getItemCount() {
        return onboardingItems.size();
    }

    class OnboardingViewHolder extends RecyclerView.ViewHolder {
        private ImageView imageOnboarding;
        private TextView textTitle;
        private TextView textDescription;

        OnboardingViewHolder(@NonNull View view) {
            super(view);
            imageOnboarding = view.findViewById(R.id.onboardingImage);
            textTitle = view.findViewById(R.id.onboardingTitle);
            textDescription = view.findViewById(R.id.onboardingDesc);
        }

        void setOnboardingData(OnboardingItem item) {
            imageOnboarding.setImageResource(item.getImage());
            textTitle.setText(item.getTitle());
            textDescription.setText(item.getDescription());
        }
    }
}

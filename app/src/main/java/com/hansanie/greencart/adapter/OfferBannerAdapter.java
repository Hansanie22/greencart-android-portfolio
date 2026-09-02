package com.hansanie.greencart.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.hansanie.greencart.R;
import com.hansanie.greencart.model.Offer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OfferBannerAdapter extends RecyclerView.Adapter<OfferBannerAdapter.BannerViewHolder> {

    public interface OnClaimClickListener {
        void onClaimClick(Offer offer);
    }

    private final List<Offer> offers = new ArrayList<>();
    private final OnClaimClickListener onClaimClickListener;

    public OfferBannerAdapter(OnClaimClickListener onClaimClickListener) {
        this.onClaimClickListener = onClaimClickListener;
    }

    public void submitOffers(List<Offer> items) {
        offers.clear();
        if (items != null) {
            offers.addAll(items);
        }
        notifyDataSetChanged();
    }

    public void updateOffers(List<Offer> items) {
        submitOffers(items);
    }

    @NonNull
    @Override
    public BannerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.banner_promo, parent, false);
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        view.setLayoutParams(params);
        return new BannerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BannerViewHolder holder, int position) {
        Offer offer = offers.get(position);
        String title = offer.getTitle() != null ? offer.getTitle() : "Shake & Save Big!";

        holder.tvTitle.setText(title);
        String description = offer.getDescription();
        if (description == null || description.trim().isEmpty()) {
            description = "A special surprise is waiting. Shake to reveal your reward.";
        }
        holder.tvSubtitle.setText(description);

        if (offer.getImageUrl() != null && !offer.getImageUrl().isEmpty()) {
            Glide.with(holder.itemView)
                    .load(offer.getImageUrl())
                    .placeholder(R.drawable.fresh)
                    .into(holder.ivProduct);
        } else {
            holder.ivProduct.setImageResource(R.drawable.fresh);
        }

        holder.btnClaimCoupon.setOnClickListener(v -> {
            if (onClaimClickListener != null) {
                onClaimClickListener.onClaimClick(offer);
            }
        });
    }

    @Override
    public int getItemCount() {
        return offers.size();
    }

    static class BannerViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvSubtitle;
        ShapeableImageView ivProduct;
        MaterialButton btnClaimCoupon;

        BannerViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            ivProduct = itemView.findViewById(R.id.ivProduct);
            btnClaimCoupon = itemView.findViewById(R.id.btnClaimCoupon);
        }
    }
}

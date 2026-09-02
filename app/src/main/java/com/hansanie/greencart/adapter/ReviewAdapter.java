package com.hansanie.greencart.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.hansanie.greencart.R;
import com.hansanie.greencart.model.Review;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder> {

    private final List<Review> reviews = new ArrayList<>();

    public void submitList(List<Review> newReviews) {
        reviews.clear();
        if (newReviews != null) {
            reviews.addAll(newReviews);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_review, parent, false);
        return new ReviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReviewViewHolder holder, int position) {
        Review review = reviews.get(position);
        holder.txtReviewerInitial.setText(review.getReviewerInitial());
        holder.txtReviewerName.setText(review.getReviewerName());
        holder.txtReviewDate.setText(review.getReviewDateLabel());
        holder.txtReviewRating.setText(String.format(Locale.getDefault(), "%.1f", review.getRating()));
        holder.txtReviewText.setText(review.getComment());
        holder.btnHelpful.setText(String.format(Locale.getDefault(), "👍 %d", review.getHelpfulCount()));
        holder.verifiedBadge.setVisibility(review.isVerifiedPurchase() ? View.VISIBLE : View.GONE);
        holder.btnHelpful.setOnClickListener(v -> {
            review.setHelpfulCount(review.getHelpfulCount() + 1);
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return reviews.size();
    }

    static class ReviewViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtReviewerInitial;
        private final TextView txtReviewerName;
        private final TextView txtReviewDate;
        private final TextView txtReviewRating;
        private final TextView txtReviewText;
        private final MaterialButton btnHelpful;
        private final View verifiedBadge;

        ReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            txtReviewerInitial = itemView.findViewById(R.id.txtReviewerInitial);
            txtReviewerName = itemView.findViewById(R.id.txtReviewerName);
            txtReviewDate = itemView.findViewById(R.id.txtReviewDate);
            txtReviewRating = itemView.findViewById(R.id.txtReviewRating);
            txtReviewText = itemView.findViewById(R.id.txtReviewText);
            btnHelpful = itemView.findViewById(R.id.btnHelpful);
            verifiedBadge = itemView.findViewById(R.id.verifiedBadge);
        }
    }
}


package com.hansanie.greencart.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.hansanie.greencart.R;
import com.hansanie.greencart.model.PaymentCard;

import java.util.ArrayList;
import java.util.List;

public class PaymentCardAdapter extends RecyclerView.Adapter<PaymentCardAdapter.ViewHolder> {

    public interface OnCardDeleteListener {
        void onDelete(PaymentCard card);
    }

    public interface OnCardSelectListener {
        void onSelect(PaymentCard card);
    }

    private final List<PaymentCard> cardList;
    private final OnCardDeleteListener deleteListener;
    private OnCardSelectListener selectListener;
    private int selectedPosition = -1;

    public PaymentCardAdapter(List<PaymentCard> cardList, OnCardDeleteListener deleteListener) {
        this.cardList = cardList != null ? cardList : new ArrayList<>();
        this.deleteListener = deleteListener;
    }

    public void setOnCardSelectListener(@Nullable OnCardSelectListener listener) {
        this.selectListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_payment_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PaymentCard card = cardList.get(position);

        holder.tvNumber.setText(card.getMaskedNumber());
        holder.tvExpiry.setText(holder.itemView.getContext().getString(
                R.string.payment_card_expires,
                card.getExpiryDate() != null ? card.getExpiryDate() : "--/--"
        ));

        String cardType = card.getCardType() != null ? card.getCardType().trim() : "";
        if ("VISA".equalsIgnoreCase(cardType)) {
            holder.ivType.setImageResource(R.drawable.ic_visa);
        } else {
            holder.ivType.setImageResource(R.drawable.ic_card);
        }

        // Selection indicator
        if (holder.rbSelect != null) {
            holder.rbSelect.setChecked(position == selectedPosition);
            holder.rbSelect.setVisibility(selectListener != null ? View.VISIBLE : View.GONE);
        }

        // Delete button
        if (deleteListener != null) {
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(card));
        } else {
            holder.btnDelete.setVisibility(View.GONE);
            holder.btnDelete.setOnClickListener(null);
        }

        // Tap whole row to select
        if (selectListener != null) {
            holder.itemView.setOnClickListener(v -> {
                int prev = selectedPosition;
                selectedPosition = holder.getBindingAdapterPosition();
                if (prev >= 0) {
                    notifyItemChanged(prev);
                }
                if (selectedPosition >= 0) {
                    notifyItemChanged(selectedPosition);
                    selectListener.onSelect(card);
                }
            });
        } else {
            holder.itemView.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return cardList.size();
    }

    public void replaceData(List<PaymentCard> updatedCards) {
        if (updatedCards == cardList) {
            if (selectedPosition >= cardList.size()) {
                selectedPosition = -1;
            }
            notifyDataSetChanged();
            return;
        }
        cardList.clear();
        if (updatedCards != null) {
            cardList.addAll(updatedCards);
        }
        if (selectedPosition >= cardList.size()) {
            selectedPosition = -1;
        }
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNumber;
        TextView tvExpiry;
        ImageView ivType;
        View btnDelete;
        RadioButton rbSelect;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNumber  = itemView.findViewById(R.id.tvCardMaskedNumber);
            tvExpiry  = itemView.findViewById(R.id.tvCardExpiry);
            ivType    = itemView.findViewById(R.id.ivCardLogo);
            btnDelete = itemView.findViewById(R.id.btnDeleteCard);
            rbSelect  = itemView.findViewById(R.id.rbSelectCard);
        }
    }
}

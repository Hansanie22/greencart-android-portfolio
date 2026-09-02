package com.hansanie.greencart.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.hansanie.greencart.R;
import com.hansanie.greencart.model.Order;
import java.util.List;
import java.util.Locale;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.OrderViewHolder> {

    private final Context context;
    private final List<Order> orderList;
    private final OnOrderClickListener listener;

    public interface OnOrderClickListener {
        void onDetailsClick(Order order);
        void onCancelClick(Order order);
    }

    public OrderAdapter(Context context, List<Order> orderList, OnOrderClickListener listener) {
        this.context = context;
        this.orderList = orderList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        Order order = orderList.get(position);

        String status = order.getStatus();
        if (status == null || status.trim().isEmpty()) {
            status = order.getOrderStatus();
        }
        if (status == null || status.trim().isEmpty()) {
            status = "Pending";
        }

        holder.tvOrderId.setText(order.getOrderId());
        holder.tvOrderDate.setText("Placed on " + (order.getOrderDate() != null ? order.getOrderDate() : "Date unavailable"));

        // Use totalAmount directly — already correctly computed at checkout
        double finalTotal = order.getTotalAmount() != null ? order.getTotalAmount() : 0.0;
        if (finalTotal < 0) finalTotal = 0.0;
        holder.tvOrderAmount.setText(String.format(Locale.getDefault(), "Rs. %.2f", finalTotal));

        holder.tvOrderDiscountSummary.setVisibility(View.GONE);

        holder.tvOrderStatus.setText(status);

        if (status.equalsIgnoreCase("Delivered")) {
            holder.tvOrderStatus.setBackgroundResource(R.drawable.bg_status_delivered);
            holder.tvOrderStatus.setTextColor(Color.parseColor("#2E7D32"));
        } else if (status.equalsIgnoreCase("Cancelled") || status.equalsIgnoreCase("Canceled")) {
            holder.tvOrderStatus.setBackgroundResource(R.drawable.bg_status_canceled);
            holder.tvOrderStatus.setTextColor(Color.parseColor("#C62828"));
        } else {
            holder.tvOrderStatus.setBackgroundResource(R.drawable.bg_status_processing);
            holder.tvOrderStatus.setTextColor(Color.parseColor("#EF6C00"));
        }

        if (status.equalsIgnoreCase("Cancelled") || status.equalsIgnoreCase("Canceled")) {
            holder.btnDetails.setVisibility(View.GONE);
        } else {
            holder.btnDetails.setVisibility(View.VISIBLE);
            holder.btnDetails.setOnClickListener(v -> listener.onDetailsClick(order));
        }
    }

    @Override
    public int getItemCount() { return orderList.size(); }

    public static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvOrderDate, tvOrderAmount, tvOrderStatus, tvOrderDiscountSummary;
        MaterialButton btnDetails;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tvOrderID);
            tvOrderDate = itemView.findViewById(R.id.tvOrderDate);
            tvOrderAmount = itemView.findViewById(R.id.tvOrderAmount);
            tvOrderStatus = itemView.findViewById(R.id.tvOrderStatus);
            tvOrderDiscountSummary = itemView.findViewById(R.id.tvOrderDiscountSummary);
            btnDetails = itemView.findViewById(R.id.btnDetails);
        }
    }
}
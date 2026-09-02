package com.hansanie.greencart.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hansanie.greencart.R;
import com.hansanie.greencart.model.Address;

import java.util.List;

public class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.ViewHolder> {

    private List<Address> addressList;
    private OnAddressActionListener listener;
    private OnAddressSelectListener selectListener;

    public interface OnAddressActionListener {
        void onEdit(Address address);
        void onDelete(Address address);
    }

    public interface OnAddressSelectListener {
        void onSelect(Address address);
    }

    public AddressAdapter(List<Address> addressList, OnAddressActionListener listener) {
        this.addressList = addressList;
        this.listener = listener;
    }

    public void setOnAddressSelectListener(OnAddressSelectListener selectListener) {
        this.selectListener = selectListener;
    }

    @NonNull
    @Override
    public AddressAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_address, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AddressAdapter.ViewHolder holder, int position) {

        Address address = addressList.get(position);

        if (address != null) {
            String title = address.getTitle();
            if (title == null || title.trim().isEmpty()) {
                title = address.getAddressType();
            }
            holder.tvTitle.setText(title != null && !title.trim().isEmpty() ? title : "Address");

            String fullAddress = address.getFullAddress();
            holder.tvDetails.setText(fullAddress != null ? fullAddress : "");

            holder.btnEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEdit(address);
                }
            });

            holder.itemView.setOnClickListener(v -> {
                if (selectListener != null) {
                    selectListener.onSelect(address);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return addressList == null ? 0 : addressList.size();
    }

    public Address getAddressAt(int position) {
        return addressList.get(position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        TextView tvTitle, tvDetails;
        ImageView btnEdit;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            tvTitle = itemView.findViewById(R.id.tvAddressTitle);
            tvDetails = itemView.findViewById(R.id.tvFullAddress);
            btnEdit = itemView.findViewById(R.id.btnEditAddress);
        }
    }
}
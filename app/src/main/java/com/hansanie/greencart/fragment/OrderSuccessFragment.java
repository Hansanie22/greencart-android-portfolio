package com.hansanie.greencart.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.hansanie.greencart.R;

import java.util.Locale;

public class OrderSuccessFragment extends Fragment {

    private static final String ARG_ORDER_CODE = "arg_order_code";
    private static final String ARG_TOTAL = "arg_total";

    public static OrderSuccessFragment newInstance(@NonNull String orderCode, double total) {
        OrderSuccessFragment fragment = new OrderSuccessFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORDER_CODE, orderCode);
        args.putDouble(ARG_TOTAL, total);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_order_success, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String orderCode = getArguments() != null ? getArguments().getString(ARG_ORDER_CODE, "") : "";
        double total = getArguments() != null ? getArguments().getDouble(ARG_TOTAL, 0.0) : 0.0;

        TextView tvOrderCode = view.findViewById(R.id.tvSuccessOrderCode);
        TextView tvTotal = view.findViewById(R.id.tvSuccessTotal);
        MaterialButton btnViewOrders = view.findViewById(R.id.btnViewOrders);
        MaterialButton btnContinueShopping = view.findViewById(R.id.btnContinueShopping);

        tvOrderCode.setText(orderCode);
        tvTotal.setText(String.format(Locale.getDefault(), "Rs. %.2f", Math.max(0.0, total)));

        btnViewOrders.setOnClickListener(v -> getParentFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new OrdersFragment())
                .addToBackStack(null)
                .commit());

        btnContinueShopping.setOnClickListener(v -> getParentFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new HomeFragment())
                .commit());
    }
}
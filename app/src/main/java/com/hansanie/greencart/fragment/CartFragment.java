package com.hansanie.greencart.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.hansanie.greencart.R;
import com.hansanie.greencart.adapter.CartAdapter;
import com.hansanie.greencart.model.CartItem;
import com.hansanie.greencart.network.CartManager;

import java.util.ArrayList;
import java.util.List;

public class CartFragment extends Fragment {

    private RecyclerView rvCart;
    private TextView tvCartItemCount, tvTotalAmount;
    private MaterialButton btnCheckout, btnGoShopping;
    private View emptyCartLayout, summaryCard; // අලුතින් එකතු කළ Views
    private CartAdapter cartAdapter;
    private List<CartItem> cartItemList = new ArrayList<>();

    public CartFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // View Initialization
        rvCart           = view.findViewById(R.id.rvCart);
        tvCartItemCount  = view.findViewById(R.id.tvCartItemCount);
        tvTotalAmount    = view.findViewById(R.id.tvTotalAmount);
        btnCheckout      = view.findViewById(R.id.btnCheckout);

        // Empty State Views
        emptyCartLayout  = view.findViewById(R.id.emptyCartLayout);
        btnGoShopping    = view.findViewById(R.id.btnGoShopping);
        summaryCard      = view.findViewById(R.id.summaryCard);

        rvCart.setLayoutManager(new LinearLayoutManager(getContext()));
        cartAdapter = new CartAdapter(getContext(), cartItemList, this::calculateTotal);
        rvCart.setAdapter(cartAdapter);

        loadCartFromRoom();

        // Button Listeners
        btnCheckout.setOnClickListener(v -> navigateToCheckout());

        btnGoShopping.setOnClickListener(v -> {
            // Home එකට ආපසු යාම
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new HomeFragment())
                    .commit();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCartFromRoom();
    }

    private void loadCartFromRoom() {
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "guest";

        CartManager.getCartItems(requireContext(), userId, entities -> {
            cartItemList.clear();
            for (var e : entities) cartItemList.add(CartItem.from(e));

            // UI එක Update කිරීම (Empty ද නැද්ද යන්න පරීක්ෂාව)
            toggleEmptyState(cartItemList.isEmpty());

            cartAdapter.notifyDataSetChanged();
            calculateTotal();
        });
    }

    // Cart එක හිස් නම් Empty Layout එක පෙන්වීමට
    private void toggleEmptyState(boolean isEmpty) {
        if (isEmpty) {
            emptyCartLayout.setVisibility(View.VISIBLE);
            rvCart.setVisibility(View.GONE);
            summaryCard.setVisibility(View.GONE);
        } else {
            emptyCartLayout.setVisibility(View.GONE);
            rvCart.setVisibility(View.VISIBLE);
            summaryCard.setVisibility(View.VISIBLE);
        }
    }

    private void calculateTotal() {
        double total = 0;
        int count = 0;
        for (CartItem item : cartItemList) {
            total += item.getPrice() * item.getQuantity();
            count += item.getQuantity();
        }

        // යම් හෙයකින් අයිතම සියල්ල ඉවත් කළහොත් (Cart empty වුවහොත්)
        if (count == 0) {
            toggleEmptyState(true);
        }

        if (tvTotalAmount != null)
            tvTotalAmount.setText(String.format("Rs. %.2f", total));
        if (tvCartItemCount != null)
            tvCartItemCount.setText(count + " item" + (count != 1 ? "s" : ""));
    }

    private void navigateToCheckout() {
        FragmentTransaction tx = getParentFragmentManager().beginTransaction();
        tx.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
        tx.replace(R.id.fragmentContainer, new CheckoutFragment());
        tx.addToBackStack(null);
        tx.commit();
    }
}
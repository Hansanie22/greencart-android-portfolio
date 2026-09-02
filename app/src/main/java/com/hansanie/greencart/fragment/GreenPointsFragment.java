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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hansanie.greencart.R;
import com.hansanie.greencart.util.CustomToast;

import java.util.Locale;

public class GreenPointsFragment extends Fragment {

    private TextView tvPointsTotal;
    private TextView tvPointsSpent;
    private TextView tvPointsOrders;
    private MaterialButton btnRedeemPoints;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_green_points, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvPointsTotal = view.findViewById(R.id.tvPointsTotal);
        tvPointsSpent = view.findViewById(R.id.tvPointsSpent);
        tvPointsOrders = view.findViewById(R.id.tvPointsOrders);
        btnRedeemPoints = view.findViewById(R.id.btnRedeemPoints);
        if (btnRedeemPoints != null) {
            btnRedeemPoints.setOnClickListener(v ->
                    CustomToast.showInfo(getContext(), "Redeem your points from checkout."));
        }
        loadPoints();
    }

    private void loadPoints() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            tvPointsTotal.setText("0");
            tvPointsSpent.setText("Log in to view your spending rewards.");
            tvPointsOrders.setText("Eligible orders: 0");
            return;
        }

        com.google.firebase.firestore.DocumentReference walletRef = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("wallet")
                .document("green_points");

        walletRef.get()
                .addOnSuccessListener(wallet -> {
                    long balance = readLong(wallet.get("pointsBalance"));
                    long totalEarned = readLong(wallet.get("totalEarned"));
                    long totalRedeemed = readLong(wallet.get("totalRedeemed"));

                    tvPointsTotal.setText(String.valueOf(Math.max(0L, balance)));
                    tvPointsSpent.setText(String.format(Locale.getDefault(),
                            "Earned: %d pts\nRedeemed: %d pts",
                            Math.max(0L, totalEarned),
                            Math.max(0L, totalRedeemed)));

                    walletRef.collection("transactions")
                            .get()
                            .addOnSuccessListener(txSnapshot -> tvPointsOrders.setText(String.format(Locale.getDefault(),
                                    "Eligible orders: %d",
                                    txSnapshot.size())))
                            .addOnFailureListener(e -> tvPointsOrders.setText("Eligible orders: 0"));
                })
                .addOnFailureListener(e -> {
                    tvPointsTotal.setText("0");
                    tvPointsSpent.setText("Unable to load points right now.");
                    tvPointsOrders.setText("Eligible orders: 0");
                });
    }

    private long readLong(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }
}


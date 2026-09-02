package com.hansanie.greencart.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.hansanie.greencart.R;
import com.hansanie.greencart.model.UserOffer;
import com.hansanie.greencart.model.Offer;

import java.util.ArrayList;
import java.util.List;

public class MyRewardsFragment extends Fragment {
    private RecyclerView recyclerView;
    private RewardsAdapter adapter;
    private final List<UserOffer> userOffers = new ArrayList<>();
    private final List<Offer> offerDetails = new ArrayList<>();
    private LinearLayout layoutEmptyRewards;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_rewards, container, false);
        recyclerView = view.findViewById(R.id.recyclerRewards);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new RewardsAdapter();
        recyclerView.setAdapter(adapter);
        layoutEmptyRewards = view.findViewById(R.id.layoutEmptyRewards);
        loadUserOffers();
        return view;
    }

    private void updateEmptyState() {
        if (adapter != null && layoutEmptyRewards != null && recyclerView != null) {
            if (adapter.getItemCount() == 0) {
                layoutEmptyRewards.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                layoutEmptyRewards.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void loadUserOffers() {
        userOffers.clear();
        offerDetails.clear();
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) {
            adapter.setData(userOffers, offerDetails);
            updateEmptyState();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("user_offers")
                .whereEqualTo("firebaseUid", uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    // BUG FIX: මෙතන String වෙනුවට Long පාවිච්චි කළ යුතුයි
                    List<Long> offerIdList = new ArrayList<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        UserOffer userOffer = new UserOffer();
                        userOffer.setOffer_id(doc.getLong("offerId"));
                        userOffer.setFirebaseUid(doc.getString("firebaseUid"));
                        userOffer.setPromoCode(doc.getString("promoCode"));
                        userOffer.setStatus(doc.getString("status"));
                        userOffer.setUsed(doc.contains("used") && Boolean.TRUE.equals(doc.getBoolean("used")));

                        Object claimedAtObj = doc.get("claimedAt");
                        if (claimedAtObj instanceof com.google.firebase.Timestamp) {
                            userOffer.setClaimedAt(((com.google.firebase.Timestamp) claimedAtObj).toDate().getTime());
                        } else if (claimedAtObj instanceof Long) {
                            userOffer.setClaimedAt((Long) claimedAtObj);
                        } else if (claimedAtObj instanceof String) {
                            try {
                                userOffer.setClaimedAt(Long.parseLong((String) claimedAtObj));
                            } catch (Exception ignore) {}
                        }

                        if (userOffer.getOffer_id() != null) {
                            userOffers.add(userOffer);
                            // String එකක් වෙනුවට Long එකක්ම ඇතුලත් කරන්න
                            if (!offerIdList.contains(userOffer.getOffer_id())) {
                                offerIdList.add(userOffer.getOffer_id());
                            }
                        }
                    }

                    if (offerIdList.isEmpty()) {
                        adapter.setData(userOffers, offerDetails);
                        updateEmptyState();
                        return;
                    }

                    // BUG FIX: Firestore 'whereIn' limits to 10 items. Crash වීම වැලැක්වීම සඳහා.
                    if (offerIdList.size() > 10) {
                        offerIdList = offerIdList.subList(0, 10);
                    }

                    FirebaseFirestore.getInstance()
                            .collection("offers")
                            .whereIn("id", offerIdList) // දැන් Type එක හරියට ගැලපෙනවා
                            .get()
                            .addOnSuccessListener(offerSnap -> {
                                offerDetails.clear();
                                for (DocumentSnapshot offerDoc : offerSnap.getDocuments()) {
                                    Offer offer = offerDoc.toObject(Offer.class);
                                    if (offer != null) offerDetails.add(offer);
                                }
                                adapter.setData(userOffers, offerDetails);
                                updateEmptyState();
                            })
                            .addOnFailureListener(e -> adapter.setData(userOffers, offerDetails));
                })
                .addOnFailureListener(e -> adapter.setData(userOffers, offerDetails));
    }

    interface OnCouponUsedListener {
        void onCouponUsed(UserOffer userOffer);
    }

    private class RewardsAdapter extends RecyclerView.Adapter<RewardsAdapter.RewardViewHolder> {
        private List<UserOffer> userOfferItems = new ArrayList<>();
        private List<Offer> offerItems = new ArrayList<>();

        private OnCouponUsedListener couponUsedListener;
        void setOnCouponUsedListener(OnCouponUsedListener listener) {
            this.couponUsedListener = listener;
        }

        void setData(List<UserOffer> userOffers, List<Offer> offers) {
            long now = System.currentTimeMillis();
            List<UserOffer> filtered = new ArrayList<>();
            for (UserOffer uo : userOffers) {
                if (uo.getUsed() != null && uo.getUsed()) continue;
                if (uo.getClaimedAt() == null) continue;
                if (now > uo.getClaimedAt() + 24 * 60 * 60 * 1000L) continue;
                filtered.add(uo);
            }
            this.userOfferItems = filtered;
            this.offerItems = new ArrayList<>(offers);
            notifyDataSetChanged();
        }

        void removeCoupon(UserOffer userOffer) {
            int idx = userOfferItems.indexOf(userOffer);
            if (idx >= 0) {
                userOfferItems.remove(idx);
                notifyItemRemoved(idx);
            }
        }

        @NonNull
        @Override
        public RewardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reward_coupon, parent, false);
            return new RewardViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RewardViewHolder holder, int position) {
            UserOffer userOffer = userOfferItems.get(position);
            Offer offer = null;

            for (Offer o : offerItems) {
                if (o.getId() != null && o.getId().equals(userOffer.getOffer_id())) {
                    offer = o;
                    break;
                }
            }

            // Set Promo Code
            String promoCode = (offer != null && offer.getPromoCode() != null && !offer.getPromoCode().isEmpty())
                    ? offer.getPromoCode()
                    : userOffer.getPromoCode();
            holder.tvCode.setText(promoCode != null && !promoCode.isEmpty() ? promoCode : "No Code");

            // Set Offer Title
            if (offer != null && offer.getTitle() != null && !offer.getTitle().isEmpty()) {
                holder.tvOfferName.setText(offer.getTitle());
            } else {
                holder.tvOfferName.setText("Exclusive Offer");
            }

            // Set Offer Image using Glide (Apple Premium Style: Rounded corners)
            if (offer != null && offer.getImageUrl() != null && !offer.getImageUrl().isEmpty()) {
                Glide.with(holder.imgOffer.getContext())
                        .load(offer.getImageUrl())
                        .apply(new RequestOptions().transform(new CenterCrop(), new RoundedCorners(16)))
                        .placeholder(R.drawable.ic_cart)
                        .error(R.drawable.ic_cart)
                        .into(holder.imgOffer);
            } else {
                holder.imgOffer.setImageResource(R.drawable.ic_cart);
            }

            // Status Text
            final String statusText;
            long now = System.currentTimeMillis();
            if (userOffer.getUsed() != null && userOffer.getUsed()) {
                statusText = "Used";
            } else if (userOffer.getClaimedAt() == null || now > userOffer.getClaimedAt() + 24 * 60 * 60 * 1000L) {
                statusText = "Expired";
            } else {
                statusText = "Active";
            }
            holder.tvStatus.setText(statusText);

            // Timer
            long millisLeft = userOffer.getClaimedAt() != null ? (userOffer.getClaimedAt() + 24 * 60 * 60 * 1000L - now) : 0;
            if (holder.activeTimer != null) {
                holder.activeTimer.cancel();
            }

            if (millisLeft > 0 && statusText.equals("Active")) {
                holder.activeTimer = new CountDownTimer(millisLeft, 1000) {
                    public void onTick(long millisUntilFinished) {
                        long hours = millisUntilFinished / (1000 * 60 * 60);
                        long minutes = (millisUntilFinished / (1000 * 60)) % 60;

                        holder.tvTimer.setText(String.format(java.util.Locale.getDefault(), "Ends in %dh %02dm", hours, minutes));
                    }
                    public void onFinish() {
                        int idx = holder.getBindingAdapterPosition();
                        if (idx != RecyclerView.NO_POSITION) {
                            removeCoupon(userOfferItems.get(idx));
                        }
                    }
                };
                holder.activeTimer.start();
            } else {
                holder.tvTimer.setText("Expired");
                holder.activeTimer = null;
            }

            // Apple Style Copy Button Interaction
            LinearLayout btnCopy = holder.itemView.findViewById(R.id.btnCopyCode);
            TextView tvCopyStatus = holder.itemView.findViewById(R.id.tvCopyStatus);

            btnCopy.setOnClickListener(v -> {
                String code = holder.tvCode.getText().toString();
                ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Promocode", code);
                clipboard.setPrimaryClip(clip);

                // Visual Feedback (Apple Style)
                tvCopyStatus.setText("Copied!");
                btnCopy.setAlpha(0.7f);
                v.postDelayed(() -> {
                    tvCopyStatus.setText("Copy");
                    btnCopy.setAlpha(1.0f);
                }, 2000);

                Toast.makeText(v.getContext(), "Copied to clipboard!", Toast.LENGTH_SHORT).show();
            });

            holder.itemView.setOnClickListener(v -> {
                if (couponUsedListener != null && statusText.equals("Active")) {
                    couponUsedListener.onCouponUsed(userOffer);
                }
            });
        }

        @Override
        public int getItemCount() {
            return userOfferItems.size();
        }

        class RewardViewHolder extends RecyclerView.ViewHolder {
            TextView tvCode, tvStatus, tvTimer, tvOfferName;
            ImageView imgOffer;
            CountDownTimer activeTimer;

            RewardViewHolder(@NonNull View itemView) {
                super(itemView);
                tvCode = itemView.findViewById(R.id.tvCouponCode);
                tvStatus = itemView.findViewById(R.id.tvCouponStatus);
                tvTimer = itemView.findViewById(R.id.tvCouponTimer);
                tvOfferName = itemView.findViewById(R.id.tvOfferName);
                imgOffer = itemView.findViewById(R.id.imgOffer);
            }
        }
    }

    public void removeCouponAfterCheckout(UserOffer userOffer) {
        if (adapter != null) {
            adapter.removeCoupon(userOffer);
        }
    }
}
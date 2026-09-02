package com.hansanie.greencart.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.storage.FirebaseStorage;
import com.hansanie.greencart.R;
import com.hansanie.greencart.activity.AuthActivity;
import com.hansanie.greencart.adapter.AddressAdapter;
import com.hansanie.greencart.adapter.PaymentCardAdapter;
import com.hansanie.greencart.model.Address;
import com.hansanie.greencart.model.AddressType;
import com.hansanie.greencart.model.PaymentCard;
import com.hansanie.greencart.model.User;
import com.hansanie.greencart.network.RetrofitClient;
import com.hansanie.greencart.util.CustomToast;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    private EditText etFirstName, etLastName, etEmail, etMobile;
    private TextView tvProfileFullName, tvProfileEmailDisplay;
    private ImageView ivProfileLarge;
    private MaterialButton btnUpdateProfile, btnSavedAddresses, btnPaymentMethods, btnSignOut, btnChangeImage;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private FusedLocationProviderClient fusedLocationClient;

    private List<Address> addressList = new ArrayList<>();
    private AddressAdapter addressAdapter;
    private final List<PaymentCard> paymentCardList = new ArrayList<>();
    private PaymentCardAdapter paymentCardAdapter;
    private Uri imageUri;

    private String uploadedProfileImageUrl; // Holds the latest uploaded image URL

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        initViews(view);
        loadUserData();

        btnUpdateProfile.setOnClickListener(v -> updateUserData());
        btnSavedAddresses.setOnClickListener(v -> showAddressBottomSheet());
        btnPaymentMethods.setOnClickListener(v -> showPaymentBottomSheet());
        btnChangeImage.setOnClickListener(v -> openFileChooser());
        btnSignOut.setOnClickListener(v -> signOutUser());
    }

    private void initViews(View view) {
        etFirstName = view.findViewById(R.id.etFirstName);
        etLastName = view.findViewById(R.id.etLastName);
        etEmail = view.findViewById(R.id.etEmail);
        etMobile = view.findViewById(R.id.etMobile);
        tvProfileFullName = view.findViewById(R.id.tvProfileFullName);
        tvProfileEmailDisplay = view.findViewById(R.id.tvProfileEmailDisplay);
        ivProfileLarge = view.findViewById(R.id.ivProfileLarge);
        btnUpdateProfile = view.findViewById(R.id.btnUpdateProfile);
        btnSavedAddresses = view.findViewById(R.id.btnSavedAddresses);
        btnPaymentMethods = view.findViewById(R.id.btnPaymentMethods);
        btnSignOut = view.findViewById(R.id.btnSignOutProfile);
        btnChangeImage = view.findViewById(R.id.btnChangeImage);
    }

    private void showPaymentBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_payments, null);
        dialog.setContentView(view);

        View cardCOD = view.findViewById(R.id.cardCOD);
        if (cardCOD != null) {
            cardCOD.setVisibility(View.VISIBLE);
        }

        RecyclerView rvSavedCards = view.findViewById(R.id.rvSavedCards);
        MaterialButton btnAddNewCard = view.findViewById(R.id.btnAddNewCard);

        rvSavedCards.setLayoutManager(new LinearLayoutManager(getContext()));
        paymentCardAdapter = new PaymentCardAdapter(new ArrayList<>(paymentCardList), this::confirmDeletePaymentCard);
        rvSavedCards.setAdapter(paymentCardAdapter);

        final ListenerRegistration[] registration = new ListenerRegistration[1];
        registration[0] = observePaymentMethods();

        btnAddNewCard.setOnClickListener(v -> showAddCardDialog());
        dialog.setOnDismissListener(d -> {
            if (registration[0] != null) {
                registration[0].remove();
            }
        });
        dialog.show();
    }

    @Nullable
    private ListenerRegistration observePaymentMethods() {
        String uid = mAuth.getUid();
        if (uid == null) {
            CustomToast.showWarning(getContext(), "Please sign in first");
            return null;
        }

        return db.collection("users").document(uid).collection("payment_cards")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        CustomToast.showError(getContext(), "Failed to load payment methods");
                        if (paymentCardAdapter != null) {
                            paymentCardAdapter.replaceData(new ArrayList<>(paymentCardList));
                        }
                        return;
                    }
                    List<PaymentCard> loadedCards = new ArrayList<>();
                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            PaymentCard card = mapFirestorePaymentCard(doc, uid);
                            if (card != null) {
                                loadedCards.add(card);
                            }
                        }
                    }
                    paymentCardList.clear();
                    paymentCardList.addAll(loadedCards);
                    if (paymentCardAdapter != null) {
                        paymentCardAdapter.replaceData(loadedCards);
                    }
                });
    }

    @Nullable
    private PaymentCard mapFirestorePaymentCard(@NonNull DocumentSnapshot doc, @NonNull String uid) {
        PaymentCard card;
        try {
            card = doc.toObject(PaymentCard.class);
        } catch (RuntimeException ignored) {
            card = null;
        }
        if (card == null) {
            card = new PaymentCard();
        }

        String masked = !TextUtils.isEmpty(card.getCardMasked())
                ? card.getCardMasked()
                : firstNonEmptyString(doc,
                "cardMasked",
                "maskedNumber",
                "masked_number",
                "card_number_masked",
                "cardNumberMasked");
        if (TextUtils.isEmpty(masked)) {
            String rawCardNumber = firstNonEmptyString(doc, "cardNumber", "number", "card_number");
            if (!TextUtils.isEmpty(rawCardNumber)) {
                String digits = rawCardNumber.replaceAll("\\D", "");
                if (digits.length() >= 4) {
                    masked = "**** **** **** " + digits.substring(digits.length() - 4);
                }
            }
        }
        if (TextUtils.isEmpty(masked)) {
            return null;
        }

        card.setCardMasked(masked);
        if (TextUtils.isEmpty(card.getCardBrand())) {
            card.setCardBrand(firstNonEmptyString(doc, "cardBrand", "cardType", "type", "brand"));
        }
        if (TextUtils.isEmpty(card.getCardHolderName())) {
            card.setCardHolderName(firstNonEmptyString(doc, "cardHolderName", "holderName", "name"));
        }
        if (TextUtils.isEmpty(card.getExpiryDate())) {
            card.setExpiryDate(firstNonEmptyString(doc, "expiryDate", "expiry", "expDate"));
        }
        card.setDefault(card.isDefault() || safeBoolean(doc.get("default")) || safeBoolean(doc.get("isDefault")));
        card.setFirebaseUid(uid);
        card.setFirestoreDocId(doc.getId());
        return card;
    }

    @Nullable
    private String firstNonEmptyString(@NonNull DocumentSnapshot doc, @NonNull String... keys) {
        for (String key : keys) {
            String value = safeReadString(doc.get(key));
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return null;
    }

    @Nullable
    private String safeReadString(@Nullable Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private boolean safeBoolean(@Nullable Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean(((String) value).trim());
        }
        return false;
    }

    private void showAddCardDialog() {
        ViewGroup parent = requireActivity().findViewById(android.R.id.content);
        View formView = getLayoutInflater().inflate(R.layout.dialog_add_payment_card, parent, false);
        EditText etCardNumber = formView.findViewById(R.id.etCardNumber);
        EditText etExpiry = formView.findViewById(R.id.etCardExpiry);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add New Card")
                .setView(formView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String cardNumber = etCardNumber.getText() != null
                            ? etCardNumber.getText().toString().trim().replace(" ", "")
                            : "";
                    String expiry = etExpiry.getText() != null
                            ? etExpiry.getText().toString().trim()
                            : "";

                    if (cardNumber.length() < 12 || cardNumber.length() > 19) {
                        CustomToast.showWarning(getContext(), "Enter a valid card number");
                        return;
                    }
                    if (!expiry.matches("^(0[1-9]|1[0-2])/[0-9]{2}$")) {
                        CustomToast.showWarning(getContext(), "Expiry format should be MM/YY");
                        return;
                    }

                    saveNewPaymentCard(cardNumber, expiry);
                })
                .show();
    }

    private void saveNewPaymentCard(@NonNull String cardNumber, @NonNull String expiry) {
        String uid = mAuth.getUid();
        if (uid == null) {
            CustomToast.showWarning(getContext(), "Please sign in first");
            return;
        }

        PaymentCard card = PaymentCard.builder()
                .firebaseUid(uid)
                .cardHolderName("GreenCart User")
                .cardMasked(maskCardNumber(cardNumber))
                .cardBrand(detectCardType(cardNumber))
                .expiryDate(expiry)
                .isDefault(paymentCardList.isEmpty())
                .build();

        db.collection("users").document(uid).collection("payment_cards")
                .add(card)
                .addOnSuccessListener(ref -> CustomToast.showSuccess(getContext(), "Card added"))
                .addOnFailureListener(e -> CustomToast.showError(getContext(), "Failed to add card"));
    }

    private String maskCardNumber(@NonNull String cardNumber) {
        String digits = cardNumber.replace(" ", "");
        if (digits.length() < 4) {
            return "****";
        }
        String last4 = digits.substring(digits.length() - 4);
        return "**** **** **** " + last4;
    }

    private String detectCardType(@NonNull String cardNumber) {
        if (cardNumber.startsWith("4")) {
            return "VISA";
        }
        if (cardNumber.startsWith("5")) {
            return "MASTERCARD";
        }
        return "CARD";
    }

    private void confirmDeletePaymentCard(@NonNull PaymentCard card) {
        if (TextUtils.isEmpty(card.getFirestoreDocId())) {
            return;
        }

        String uid = mAuth.getUid();
        if (uid == null) {
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Card")
                .setMessage("Do you want to remove this card?")
                .setPositiveButton("Delete", (d, i) ->
                        db.collection("users").document(uid).collection("payment_cards")
                                .document(card.getFirestoreDocId())
                                .delete()
                                .addOnFailureListener(e -> CustomToast.showError(getContext(), "Failed to delete card")))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- ADDRESS LOGIC ---
    private void showAddressBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_addresses, null);
        dialog.setContentView(view);

        RecyclerView rvAddresses = view.findViewById(R.id.rvAddresses);
        LinearLayout layoutForm = view.findViewById(R.id.layoutAddressForm);
        TextView sheetTitle = view.findViewById(R.id.sheetTitle);
        MaterialButton btnAddNew = view.findViewById(R.id.btnAddNewAddress);

        rvAddresses.setLayoutManager(new LinearLayoutManager(getContext()));
        addressAdapter = new AddressAdapter(addressList, new AddressAdapter.OnAddressActionListener() {
            @Override
            public void onEdit(Address address) {
                showAddressForm(layoutForm, rvAddresses, btnAddNew, sheetTitle, address, dialog);
            }

            @Override
            public void onDelete(Address address) {
                confirmDelete(address);
            }
        });
        rvAddresses.setAdapter(addressAdapter);

        loadAddresses();

        new ItemTouchHelper(createSwipeHelper()).attachToRecyclerView(rvAddresses);

        btnAddNew.setOnClickListener(v -> showAddressForm(layoutForm, rvAddresses, btnAddNew, sheetTitle, null, dialog));
        view.findViewById(R.id.btnCancelAddress).setOnClickListener(v -> hideAddressForm(layoutForm, rvAddresses, btnAddNew, sheetTitle));

        dialog.show();
    }

    private void showAddressForm(View form, View rv, View btnAdd, TextView title, Address address, BottomSheetDialog dialog) {
        title.setText(address == null ? "New Address" : "Edit Address");
        rv.setVisibility(View.GONE);
        btnAdd.setVisibility(View.GONE);
        form.setVisibility(View.VISIBLE);

        // TextInputLayouts for validation animation
        TextInputLayout tilTitle = form.findViewById(R.id.tilAddressTitle); // අලුතින් එක් කළ Title TextInputLayout එක
        TextInputLayout tilLine1 = form.findViewById(R.id.tilAddressLine1);
        TextInputLayout tilLine2 = form.findViewById(R.id.tilAddressLine2);
        TextInputLayout tilCity = form.findViewById(R.id.tilCity);
        TextInputLayout tilPost = form.findViewById(R.id.tilPostalCode);

        // EditTexts
        EditText etAddrTitle = form.findViewById(R.id.etAddressTitle); // අලුතින් එක් කළ Title EditText එක
        EditText etLine1 = form.findViewById(R.id.etAddressLine1);
        EditText etLine2 = form.findViewById(R.id.etAddressLine2);
        EditText etCity = form.findViewById(R.id.etCity);
        EditText etPostal = form.findViewById(R.id.etPostalCode);
        RadioGroup rgAddressType = form.findViewById(R.id.rgAddressType);

        // ටයිප් කරන විට Error එක ඉවත් කිරීම
        setupErrorClearer(etAddrTitle, tilTitle);
        setupErrorClearer(etLine1, tilLine1);
        setupErrorClearer(etLine2, tilLine2);
        setupErrorClearer(etCity, tilCity);
        setupErrorClearer(etPostal, tilPost);

        // Edit කරනවා නම් පරණ දත්ත පෙන්වීම
        if (address != null) {
            etAddrTitle.setText(address.getTitle());
            etLine1.setText(address.getAddressLine1());
            etLine2.setText(address.getAddressLine2());
            etCity.setText(address.getCity());
            etPostal.setText(address.getPostalCode());

            AddressType existingType = AddressType.fromValue(address.getAddressType());
            switch (existingType) {
                case BILLING:
                    rgAddressType.check(R.id.rbTypeBilling);
                    break;
                case SHIPPING:
                    rgAddressType.check(R.id.rbTypeShipping);
                    break;
                case BOTH:
                default:
                    rgAddressType.check(R.id.rbTypeBoth);
                    break;
            }
        } else {
            rgAddressType.check(R.id.rbTypeBoth);
        }

        form.findViewById(R.id.btnUseCurrentLocation).setOnClickListener(v -> fetchLocation(etLine1, etLine2, etCity, etPostal));

        form.findViewById(R.id.btnSaveAddress).setOnClickListener(v -> {
            // --- VALIDATION ---
            boolean isValid = true;

            // Title (Label) එක හිස්දැයි බැලීම
            if (TextUtils.isEmpty(etAddrTitle.getText().toString().trim())) {
                showShakeError(tilTitle, "Give a name to this address (e.g. Home, Office)");
                isValid = false;
            } else { tilTitle.setError(null); }

            if (TextUtils.isEmpty(etLine1.getText().toString().trim())) {
                showShakeError(tilLine1, "Enter house number or name");
                isValid = false;
            } else { tilLine1.setError(null); }

            if (TextUtils.isEmpty(etLine2.getText().toString().trim())) {
                showShakeError(tilLine2, "Enter street or area");
                isValid = false;
            } else { tilLine2.setError(null); }

            if (TextUtils.isEmpty(etCity.getText().toString().trim())) {
                showShakeError(tilCity, "Enter city");
                isValid = false;
            } else { tilCity.setError(null); }

            if (TextUtils.isEmpty(etPostal.getText().toString().trim())) {
                showShakeError(tilPost, "Enter postal code");
                isValid = false;
            } else { tilPost.setError(null); }

            if (!isValid) return;

            // Data එකතු කර සුරැකීම
            Address newAddr = (address == null) ? new Address() : address;
            newAddr.setFirebaseUid(mAuth.getUid());

            // "Home" වෙනුවට User ලබාදුන් නම මෙහිදී භාවිතා වේ
            newAddr.setTitle(etAddrTitle.getText().toString().trim());

            newAddr.setAddressLine1(etLine1.getText().toString().trim());
            newAddr.setAddressLine2(etLine2.getText().toString().trim());
            newAddr.setCity(etCity.getText().toString().trim());
            newAddr.setPostalCode(etPostal.getText().toString().trim());

            AddressType selectedType = getSelectedAddressType(rgAddressType);
            newAddr.setAddressType(selectedType.name());

            if (newAddr.getCreatedAt() == null) {
                newAddr.setCreatedAt(LocalDateTime.now());
            }
            newAddr.setUpdatedAt(LocalDateTime.now());

            askDefaultAndSaveAddress(newAddr, dialog);
        });
    }

    private AddressType getSelectedAddressType(RadioGroup rgAddressType) {
        int selectedId = rgAddressType.getCheckedRadioButtonId();
        if (selectedId == R.id.rbTypeBilling) {
            return AddressType.BILLING;
        }
        if (selectedId == R.id.rbTypeShipping) {
            return AddressType.SHIPPING;
        }
        return AddressType.BOTH;
    }

    private void askDefaultAndSaveAddress(Address address, BottomSheetDialog dialog) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Default Address")
                .setMessage("Do you want to set this as your default address?")
                .setPositiveButton("Yes", (d, i) -> {
                    address.setDefault(true);
                    saveAddressWithDefaultHandling(address, dialog);
                })
                .setNegativeButton("No", (d, i) -> {
                    address.setDefault(false);
                    saveAddressWithDefaultHandling(address, dialog);
                })
                .show();
    }

    private void saveAddressWithDefaultHandling(Address address, BottomSheetDialog dialog) {
        if (address.isDefault()) {
            unsetOtherDefaultAddresses(address, () -> saveAddress(address, dialog));
            return;
        }
        saveAddress(address, dialog);
    }

    private void unsetOtherDefaultAddresses(Address selectedAddress, Runnable onComplete) {
        String uid = mAuth.getUid();
        if (uid == null) {
            onComplete.run();
            return;
        }

        List<Address> defaultsToClear = new ArrayList<>();
        for (Address existing : addressList) {
            if (existing == null || !existing.isDefault()) {
                continue;
            }
            boolean isSameAddress = selectedAddress.getId() != null && selectedAddress.getId().equals(existing.getId());
            if (!isSameAddress) {
                defaultsToClear.add(existing);
            }
        }

        if (defaultsToClear.isEmpty()) {
            onComplete.run();
            return;
        }

        final int total = defaultsToClear.size();
        final int[] completed = {0};
        for (Address existing : defaultsToClear) {
            existing.setDefault(false);
            if (existing.getCreatedAt() == null) {
                existing.setCreatedAt(LocalDateTime.now());
            }
            existing.setUpdatedAt(LocalDateTime.now());

            if (existing.getId() == null) {
                completed[0]++;
                if (completed[0] == total) {
                    onComplete.run();
                }
                continue;
            }

            db.collection("users").document(uid).collection("addresses")
                    .document(existing.getId())
                    .set(existing)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            syncToMySQL(existing, null, false);
                        }
                        completed[0]++;
                        if (completed[0] == total) {
                            onComplete.run();
                        }
                    });
        }
    }
    // Text එකක් ටයිප් කළ සැනින් Error එක අයින් කරන Helper Method එක
    private void setupErrorClearer(EditText editText, TextInputLayout inputLayout) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    inputLayout.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void showShakeError(TextInputLayout til, String message) {
        til.setError(message);
        Animation shake = AnimationUtils.loadAnimation(getContext(), R.anim.shake);
        til.startAnimation(shake);
        if (til.getEditText() != null) {
            til.getEditText().requestFocus();
        }
    }

    private void saveAddress(Address address, BottomSheetDialog dialog) {
        String uid = mAuth.getUid();
        if (uid == null) return;

        if (address.getId() == null) {
            db.collection("users").document(uid).collection("addresses")
                    .add(address).addOnSuccessListener(ref -> {
                        address.setId(ref.getId());
                        syncToMySQL(address, dialog, true);
                    })
                    .addOnFailureListener(e -> CustomToast.showError(getContext(), "Failed to save address"));
        } else {
            db.collection("users").document(uid).collection("addresses")
                    .document(address.getId()).set(address)
                    .addOnSuccessListener(a -> syncToMySQL(address, dialog, true))
                    .addOnFailureListener(e -> CustomToast.showError(getContext(), "Failed to update address"));
        }
    }

    private void syncToMySQL(Address address, @Nullable BottomSheetDialog dialog, boolean showSuccessToast) {
        RetrofitClient.getApiService().saveAddress(address).enqueue(new Callback<Address>() {
            @Override
            public void onResponse(Call<Address> call, Response<Address> response) {
                if (response.isSuccessful()) {
                    if (showSuccessToast) {
                        CustomToast.showSuccess(getContext(), "Address saved successfully");
                    }
                } else if (showSuccessToast) {
                    CustomToast.showWarning(getContext(), "Saved to app, but MySQL sync failed");
                }
                if (dialog != null) {
                    dialog.dismiss();
                }
            }

            @Override
            public void onFailure(Call<Address> call, Throwable t) {
                if (showSuccessToast) {
                    CustomToast.showWarning(getContext(), "Saved to app, but MySQL sync failed");
                }
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });
    }

    private void fetchLocation(EditText et1, EditText et2, EditText city, EditText post) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                try {
                    List<android.location.Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    if (!addresses.isEmpty()) {
                        android.location.Address addr = addresses.get(0);
                        et1.setText(addr.getFeatureName());
                        et2.setText(addr.getThoroughfare());
                        city.setText(addr.getLocality());
                        post.setText(addr.getPostalCode());
                    }
                } catch (IOException e) {
                    CustomToast.showError(getContext(), "Geocoder error");
                }
            }
        });
    }

    private void loadAddresses() {
        if (mAuth.getUid() == null) return;
        db.collection("users").document(mAuth.getUid()).collection("addresses")
                .addSnapshotListener((value, error) -> {
                    if (value != null) {
                        addressList.clear();
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            try {
                                Address a = doc.toObject(Address.class);
                                if (a != null) {
                                    a.setId(doc.getId());
                                    addressList.add(a);
                                }
                            } catch (RuntimeException ignored) {
                                // Skip legacy malformed address records instead of crashing the screen.
                            }
                        }
                        addressAdapter.notifyDataSetChanged();
                    }
                });
    }

    private void confirmDelete(Address address) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Address")
                .setMessage("Delete from all records?")
                .setPositiveButton("Delete", (d, i) -> {
                    db.collection("users").document(mAuth.getUid()).collection("addresses").document(address.getId()).delete();
                    RetrofitClient.getApiService().deleteAddress(address.getId()).enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {}
                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {}
                    });
                }).setNegativeButton("Cancel", (d, i) -> addressAdapter.notifyDataSetChanged()).show();
    }

    private ItemTouchHelper.SimpleCallback createSwipeHelper() {
        return new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder v, @NonNull RecyclerView.ViewHolder t) { return false; }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                confirmDelete(addressList.get(vh.getAdapterPosition()));
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, float dX, float dY, int action, boolean active) {
                super.onChildDraw(c, rv, vh, dX, dY, action, active);
            }
        };
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid()).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    etFirstName.setText(doc.getString("first_name"));
                    etLastName.setText(doc.getString("last_name"));
                    etEmail.setText(doc.getString("email"));
                    etMobile.setText(doc.getString("phone"));
                    tvProfileFullName.setText(doc.getString("first_name") + " " + doc.getString("last_name"));
                    tvProfileEmailDisplay.setText(doc.getString("email"));
                    Glide.with(this).load(doc.getString("profile_image")).placeholder(R.drawable.ic_user).into(ivProfileLarge);
                }
            });
        }
    }

    private void updateUserData() {
        Map<String, Object> map = new HashMap<>();
        map.put("first_name", etFirstName.getText().toString());
        map.put("last_name", etLastName.getText().toString());
        map.put("email", etEmail.getText().toString());
        map.put("phone", etMobile.getText().toString());
        db.collection("users").document(mAuth.getUid()).update(map)
                .addOnSuccessListener(a -> {
                    CustomToast.showSuccess(getContext(), "Updated!");
                    syncProfileToMySQL();
                });
    }

    private void syncProfileToMySQL() {
        String uid = mAuth.getUid();
        if (uid == null) return;
        User user = new User();
        user.setFirebaseUid(uid);
        user.setFirstName(etFirstName.getText().toString());
        user.setLastName(etLastName.getText().toString());
        user.setEmail(etEmail.getText().toString());
        user.setPhone(etMobile.getText().toString());
        user.setStatus("active");
        // Set profile image if available
        String profileImageUrl = uploadedProfileImageUrl;
        if (profileImageUrl == null || profileImageUrl.isEmpty()) {
            // Try to get from Firestore field if not just uploaded
            profileImageUrl = null;
            if (mAuth.getCurrentUser() != null) {
                db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
                    String url = doc.getString("profile_image");
                    if (url != null && !url.isEmpty()) {
                        user.setProfileImage(url);
                    }
                    RetrofitClient.getApiService().updateUser(user).enqueue(new Callback<User>() {
                        @Override
                        public void onResponse(Call<User> call, Response<User> response) {
                            if (!response.isSuccessful()) {
                                CustomToast.showWarning(getContext(), "Profile updated in app, but MySQL sync failed");
                            }
                        }
                        @Override
                        public void onFailure(Call<User> call, Throwable t) {
                            CustomToast.showWarning(getContext(), "Profile updated in app, but MySQL sync failed");
                        }
                    });
                });
                return;
            }
        } else {
            user.setProfileImage(profileImageUrl);
        }
        RetrofitClient.getApiService().updateUser(user).enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (!response.isSuccessful()) {
                    CustomToast.showWarning(getContext(), "Profile updated in app, but MySQL sync failed");
                }
            }
            @Override
            public void onFailure(Call<User> call, Throwable t) {
                CustomToast.showWarning(getContext(), "Profile updated in app, but MySQL sync failed");
            }
        });
    }

    private void signOutUser() {
        mAuth.signOut();
        startActivity(new Intent(getActivity(), AuthActivity.class));
        getActivity().finish();
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            imageUri = data.getData();
            uploadProfileImage(imageUri);
        }
    }

    private void uploadProfileImage(Uri imageUri) {
        if (imageUri == null || mAuth.getUid() == null) return;
        String uid = mAuth.getUid();
        String storagePath = "profile_images/" + uid;
        storage.getReference().child(storagePath).putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> storage.getReference().child(storagePath).getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            uploadedProfileImageUrl = uri.toString();
                            // Update Firestore with new image URL
                            db.collection("users").document(uid).update("profile_image", uploadedProfileImageUrl)
                                    .addOnSuccessListener(aVoid -> {
                                        // Update UI
                                        Glide.with(this).load(uploadedProfileImageUrl).placeholder(R.drawable.ic_user).into(ivProfileLarge);
                                        CustomToast.showSuccess(getContext(), "Profile image updated!");
                                        // Sync to MySQL
                                        syncProfileToMySQL();
                                    })
                                    .addOnFailureListener(e -> CustomToast.showError(getContext(), "Failed to update Firestore with image URL"));
                        })
                        .addOnFailureListener(e -> CustomToast.showError(getContext(), "Failed to get image URL")))
                .addOnFailureListener(e -> CustomToast.showError(getContext(), "Image upload failed"));
    }

    private void hideAddressForm(View form, View rv, View btnAdd, TextView title) {
        title.setText("My Addresses");
        form.setVisibility(View.GONE);
        rv.setVisibility(View.VISIBLE);
        btnAdd.setVisibility(View.VISIBLE);
    }
}

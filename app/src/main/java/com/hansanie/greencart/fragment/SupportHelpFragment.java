package com.hansanie.greencart.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.android.material.button.MaterialButton;
import com.hansanie.greencart.R;
import com.hansanie.greencart.adapter.SupportChatAdapter;
import com.hansanie.greencart.model.ChatMessage;
import com.hansanie.greencart.model.SupportMessageSyncRequest;
import com.hansanie.greencart.network.RetrofitClient;
import com.hansanie.greencart.util.CustomToast;
import com.hansanie.greencart.util.NotificationHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SupportHelpFragment extends Fragment {

    private static final String DEFAULT_SUPPORT_PHONE = "+94 75 8497065";
    private static final String DEFAULT_CONVERSATION = "general_support";

    private TextView tvSupportOrder;
    private TextView tvSupportPhone;
    private RecyclerView rvSupportChat;
    private EditText inputSupportMessage;
    private View cardMessageComposer;
    private MaterialButton btnCallSupport;
    private MaterialButton btnSendMessage;

    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private final Set<String> mysqlSyncedMessageIds = new HashSet<>();

    // ── FIX: notifiedSupportMessageIds is now persistent across re-entries ──
    // Clear කරන්නේ නෑ — fragment recreate වුණත් duplicate notifications නෑ
    private final Set<String> notifiedSupportMessageIds = new HashSet<>();

    // ── FIX: Track the server timestamp of the latest message seen at load time ──
    // Initial snapshot fire වෙනකොට existing messages timestamp store කරනවා
    // ඊට පස්සේ ආ messages පමණයි notification trigger කරන්නේ
    private long initialLoadMaxTimestamp = 0L;
    private boolean hasLoadedInitialSnapshot = false;

    private SupportChatAdapter chatAdapter;
    private FirebaseFirestore firestore;
    private ListenerRegistration chatListener;
    private CollectionReference messagesRef;
    private DocumentReference conversationRef;
    private String currentUserId;
    private String conversationId = DEFAULT_CONVERSATION;
    private String currentOrderId;
    private int baseComposerBottomMargin;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_support_help, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupKeyboardInsets(view);
        setupList();
        bindHeader();
        setupActions();
        startRealtimeChat();
    }

    @Override
    public void onResume() {
        super.onResume();
        // ── FIX: Fragment background ගිහින් ආවාම listener dead නම් restart ──
        if (chatListener == null && messagesRef != null) {
            hasLoadedInitialSnapshot = false;
            initialLoadMaxTimestamp = 0L;
            attachChatListener();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (chatListener != null) {
            chatListener.remove();
            chatListener = null;
        }
    }

    private void bindViews(@NonNull View view) {
        tvSupportOrder    = view.findViewById(R.id.tvSupportOrder);
        tvSupportPhone    = view.findViewById(R.id.tvSupportPhone);
        rvSupportChat     = view.findViewById(R.id.rvSupportChat);
        inputSupportMessage = view.findViewById(R.id.inputSupportMessage);
        cardMessageComposer = view.findViewById(R.id.cardMessageComposer);
        btnCallSupport    = view.findViewById(R.id.btnCallSupport);
        btnSendMessage    = view.findViewById(R.id.btnSendMessage);

        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) cardMessageComposer.getLayoutParams();
        baseComposerBottomMargin = params.bottomMargin;
    }

    private void setupKeyboardInsets(@NonNull View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets imeInsets    = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottomInset = Math.max(imeInsets.bottom, systemInsets.bottom);

            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) cardMessageComposer.getLayoutParams();
            params.bottomMargin = baseComposerBottomMargin + bottomInset;
            cardMessageComposer.setLayoutParams(params);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void setupList() {
        chatAdapter = new SupportChatAdapter();
        rvSupportChat.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSupportChat.setAdapter(chatAdapter);
    }

    private void bindHeader() {
        firestore     = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();

        Bundle args = getArguments();
        String orderId               = args != null ? args.getString("orderId")         : null;
        String providedConversationId = args != null ? args.getString("conversationId") : null;
        String supportPhone          = args != null ? args.getString("supportPhone")    : null;

        currentOrderId = TextUtils.isEmpty(orderId) ? null : orderId;
        conversationId = !TextUtils.isEmpty(providedConversationId)
                ? providedConversationId
                : (currentOrderId != null ? ("order_" + currentOrderId) : DEFAULT_CONVERSATION);

        tvSupportOrder.setText(!TextUtils.isEmpty(orderId) ? ("Order: " + orderId) : "General support");
        tvSupportPhone.setText(!TextUtils.isEmpty(supportPhone) ? supportPhone : DEFAULT_SUPPORT_PHONE);
    }

    private void setupActions() {
        btnCallSupport.setOnClickListener(v -> dialSupport());
        btnSendMessage.setOnClickListener(v -> sendMessage());
        inputSupportMessage.setOnEditorActionListener((v, actionId, event) -> {
            boolean isImeSend = actionId == EditorInfo.IME_ACTION_SEND;
            boolean isEnter   = event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (isImeSend || isEnter) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    private void dialSupport() {
        String phone = tvSupportPhone.getText() != null
                ? tvSupportPhone.getText().toString().trim() : "";
        if (TextUtils.isEmpty(phone)) phone = DEFAULT_SUPPORT_PHONE;
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
    }

    // ── Setup refs + listener ────────────────────────────────────────────────

    private void startRealtimeChat() {
        if (TextUtils.isEmpty(currentUserId)) {
            btnSendMessage.setEnabled(false);
            CustomToast.showError(getContext(), "Login required to start support chat");
            return;
        }

        // ── FIX: notifiedSupportMessageIds clear කරන්නේ නෑ ──
        // Clear කළොත් fragment re-enter වෙනකොට existing messages
        // නැවත notification trigger කරනවා
        hasLoadedInitialSnapshot = false;
        initialLoadMaxTimestamp  = 0L;

        messagesRef = firestore
                .collection("users")
                .document(currentUserId)
                .collection("support_chats")
                .document(conversationId)
                .collection("messages");

        conversationRef = firestore
                .collection("users")
                .document(currentUserId)
                .collection("support_chats")
                .document(conversationId);

        ensureConversationInitialized();
        attachChatListener();
    }

    private void attachChatListener() {
        if (chatListener != null) {
            chatListener.remove();
        }

        chatListener = messagesRef
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        CustomToast.showError(getContext(), "Unable to load support chat");
                        return;
                    }
                    if (snapshots == null) return;

                    if (!hasLoadedInitialSnapshot) {
                        // ── FIX: Initial snapshot — existing messages වල max timestamp store කරනවා ──
                        // ඊට වඩා timestamp වැඩි messages පමණයි notification trigger කරන්නේ
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshots) {
                            ChatMessage msg = doc.toObject(ChatMessage.class);
                            if (msg != null && msg.getTimestamp() > initialLoadMaxTimestamp) {
                                initialLoadMaxTimestamp = msg.getTimestamp();
                            }
                        }
                        hasLoadedInitialSnapshot = true;

                    } else {
                        // ── FIX: New messages only — timestamp > initialLoadMaxTimestamp ──
                        for (DocumentChange change : snapshots.getDocumentChanges()) {
                            if (change.getType() != DocumentChange.Type.ADDED) continue;

                            ChatMessage incoming = change.getDocument().toObject(ChatMessage.class);
                            if (TextUtils.isEmpty(incoming.getId())) {
                                incoming.setId(change.getDocument().getId());
                            }

                            // ── KEY FIX: timestamp check — initial load වලදී ආ messages skip ──
                            if (incoming.getTimestamp() <= initialLoadMaxTimestamp) continue;

                            if (shouldNotifyForSupportMessage(incoming)) {
                                sendSupportNotification(incoming);
                            }
                        }
                    }

                    // UI update — always
                    chatMessages.clear();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : snapshots) {
                        ChatMessage chatMessage = doc.toObject(ChatMessage.class);
                        if (chatMessage == null) continue;
                        if (TextUtils.isEmpty(chatMessage.getId())) {
                            chatMessage.setId(doc.getId());
                        }
                        if (TextUtils.isEmpty(chatMessage.getSenderName())) {
                            chatMessage.setSenderName(
                                    chatMessage.isSupportAgent() ? "GreenCart Support" : "You");
                        }
                        chatMessages.add(chatMessage);
                        mirrorMessageToMySql(chatMessage);
                    }

                    chatAdapter.submitList(new ArrayList<>(chatMessages));
                    if (!chatMessages.isEmpty()) {
                        rvSupportChat.scrollToPosition(chatMessages.size() - 1);
                    }
                });
    }

    // ── Notification logic ───────────────────────────────────────────────────

    private boolean shouldNotifyForSupportMessage(@NonNull ChatMessage chatMessage) {
        if (!chatMessage.isSupportAgent()) return false;
        if (TextUtils.equals(currentUserId, chatMessage.getSenderId())) return false;
        String messageId = chatMessage.getId();
        return !TextUtils.isEmpty(messageId)
                && !notifiedSupportMessageIds.contains(messageId)
                && !NotificationHelper.isSupportMessageDismissed(requireContext().getApplicationContext(), messageId)
                && !TextUtils.isEmpty(chatMessage.getMessage());
    }

    private void sendSupportNotification(@NonNull ChatMessage chatMessage) {
        String messageId = chatMessage.getId();
        if (!TextUtils.isEmpty(messageId)) {
            notifiedSupportMessageIds.add(messageId);
        }
        NotificationHelper.showSupportChatNotification(
                getContext(),
                !TextUtils.isEmpty(chatMessage.getSenderName())
                        ? chatMessage.getSenderName() : "GreenCart Support",
                chatMessage.getMessage(),
                conversationId,
                currentOrderId,
                tvSupportPhone.getText() != null
                        ? tvSupportPhone.getText().toString().trim() : DEFAULT_SUPPORT_PHONE,
                messageId
        );
    }

    // ── Send message ─────────────────────────────────────────────────────────

    private void sendMessage() {
        String message = inputSupportMessage.getText() != null
                ? inputSupportMessage.getText().toString().trim() : "";
        if (message.isEmpty()) return;

        if (TextUtils.isEmpty(currentUserId) || messagesRef == null) {
            CustomToast.showError(getContext(), "Login required to send message");
            return;
        }

        ChatMessage outgoing = ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .senderId(currentUserId)
                .senderName("You")
                .message(message)
                .timestamp(System.currentTimeMillis())
                .supportAgent(false)
                .build();

        btnSendMessage.setEnabled(false);
        messagesRef.document(outgoing.getId())
                .set(outgoing)
                .addOnSuccessListener(unused -> {
                    btnSendMessage.setEnabled(true);
                    inputSupportMessage.setText("");
                    updateConversationSummary(outgoing);
                    mirrorMessageToMySql(outgoing);
                    CustomToast.showSuccess(getContext(), "Message sent to support");
                })
                .addOnFailureListener(error -> {
                    btnSendMessage.setEnabled(true);
                    CustomToast.showError(getContext(), "Failed to send message");
                });
    }

    // ── MySQL mirror ─────────────────────────────────────────────────────────

    private void mirrorMessageToMySql(@NonNull ChatMessage chatMessage) {
        if (TextUtils.isEmpty(currentUserId) || TextUtils.isEmpty(chatMessage.getId())) return;
        if (mysqlSyncedMessageIds.contains(chatMessage.getId())) return;

        // ── FIX: Support agent messages backend already save කරලා ──
        // Android නැවත save කළොත් unnecessary — backend existsByMessageId() block කරනවා
        // නමුත් extra network call waste කරනවා — skip කරමු
        if (chatMessage.isSupportAgent()) return;

        SupportMessageSyncRequest request = new SupportMessageSyncRequest();
        request.setMessageId(chatMessage.getId());
        request.setFirebaseUid(currentUserId);
        request.setConversationId(conversationId);
        request.setOrderId(currentOrderId);
        request.setSenderId(chatMessage.getSenderId());
        request.setSenderName(chatMessage.getSenderName());
        request.setMessage(chatMessage.getMessage());
        request.setTimestamp(chatMessage.getTimestamp());
        request.setSupportAgent(chatMessage.isSupportAgent());

        RetrofitClient.getApiService().saveSupportMessage(request).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    mysqlSyncedMessageIds.add(chatMessage.getId());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                // Silent fail — Firestore is primary source
            }
        });
    }

    // ── Firestore helpers ────────────────────────────────────────────────────

    private void ensureConversationInitialized() {
        if (conversationRef == null || messagesRef == null) return;

        Map<String, Object> conversation = new HashMap<>();
        conversation.put("conversationId", conversationId);
        conversation.put("firebaseUid", currentUserId);
        conversation.put("orderId", currentOrderId);
        conversation.put("supportPhone",
                tvSupportPhone.getText() != null
                        ? tvSupportPhone.getText().toString().trim() : DEFAULT_SUPPORT_PHONE);
        conversation.put("title",
                tvSupportOrder.getText() != null
                        ? tvSupportOrder.getText().toString() : "General support");
        conversation.put("updatedAt", System.currentTimeMillis());
        conversationRef.set(conversation);

        messagesRef.limit(1).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.isEmpty()) return;

            ChatMessage welcome = ChatMessage.builder()
                    .id("welcome_1")
                    .senderId("support")
                    .senderName("GreenCart Support")
                    .message("Hi, we are here to help you with delivery, payments, and returns.")
                    .timestamp(System.currentTimeMillis() - 2 * 60_000L)
                    .supportAgent(true)
                    .build();

            ChatMessage prompt = ChatMessage.builder()
                    .id("welcome_2")
                    .senderId("support")
                    .senderName("GreenCart Support")
                    .message("Share your concern and we will respond as soon as possible.")
                    .timestamp(System.currentTimeMillis() - 60_000L)
                    .supportAgent(true)
                    .build();

            messagesRef.document(welcome.getId()).set(welcome);
            messagesRef.document(prompt.getId()).set(prompt);
            updateConversationSummary(prompt);
        });
    }

    private void updateConversationSummary(@NonNull ChatMessage chatMessage) {
        if (conversationRef == null) return;

        Map<String, Object> summary = new HashMap<>();
        summary.put("conversationId", conversationId);
        summary.put("firebaseUid", currentUserId);
        summary.put("orderId", currentOrderId);
        summary.put("supportPhone",
                tvSupportPhone.getText() != null
                        ? tvSupportPhone.getText().toString().trim() : DEFAULT_SUPPORT_PHONE);
        summary.put("title",
                tvSupportOrder.getText() != null
                        ? tvSupportOrder.getText().toString() : "General support");
        summary.put("lastMessage", chatMessage.getMessage());
        summary.put("lastMessageAt", chatMessage.getTimestamp());
        summary.put("lastSenderId", chatMessage.getSenderId());
        summary.put("lastSenderName", chatMessage.getSenderName());
        summary.put("lastSenderSupportAgent", chatMessage.isSupportAgent());
        conversationRef.set(summary);
    }
}
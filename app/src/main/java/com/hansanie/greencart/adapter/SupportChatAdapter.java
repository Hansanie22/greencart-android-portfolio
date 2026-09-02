package com.hansanie.greencart.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hansanie.greencart.R;
import com.hansanie.greencart.model.ChatMessage;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SupportChatAdapter extends RecyclerView.Adapter<SupportChatAdapter.ChatViewHolder> {

    private final List<ChatMessage> messages = new ArrayList<>();

    public void submitList(List<ChatMessage> items) {
        messages.clear();
        if (items != null) {
            messages.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_support_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        boolean fromSupport = message.isSupportAgent();
        holder.leftBubble.setVisibility(fromSupport ? View.VISIBLE : View.GONE);
        holder.rightBubble.setVisibility(fromSupport ? View.GONE : View.VISIBLE);

        String formattedTime = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(message.getTimestamp()));
        if (fromSupport) {
            holder.leftSender.setText(message.getSenderName());
            holder.leftText.setText(message.getMessage());
            holder.leftTime.setText(formattedTime);
        } else {
            holder.rightSender.setText(message.getSenderName());
            holder.rightText.setText(message.getMessage());
            holder.rightTime.setText(formattedTime);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private final View leftBubble;
        private final View rightBubble;
        private final TextView leftSender;
        private final TextView leftText;
        private final TextView leftTime;
        private final TextView rightSender;
        private final TextView rightText;
        private final TextView rightTime;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            leftBubble = itemView.findViewById(R.id.leftBubble);
            rightBubble = itemView.findViewById(R.id.rightBubble);
            leftSender = itemView.findViewById(R.id.leftSender);
            leftText = itemView.findViewById(R.id.leftMessage);
            leftTime = itemView.findViewById(R.id.leftTime);
            rightSender = itemView.findViewById(R.id.rightSender);
            rightText = itemView.findViewById(R.id.rightMessage);
            rightTime = itemView.findViewById(R.id.rightTime);
        }
    }
}


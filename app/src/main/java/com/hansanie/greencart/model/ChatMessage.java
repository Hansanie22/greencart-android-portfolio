package com.hansanie.greencart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    private String id;
    private String senderId;
    private String senderName;
    private String message;
    private long timestamp;
    private boolean supportAgent;
}


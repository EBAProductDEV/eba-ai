package com.qctv1.ai.chat.dto;

public record ChatConversationCreateRequest(
        String provider,
        String model
) {
}

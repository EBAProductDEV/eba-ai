package com.qctv1.ai.chat.vo;

public record ChatConversationCreateVo(
        Long conversationId,
        String title,
        String provider,
        String model
) {
}

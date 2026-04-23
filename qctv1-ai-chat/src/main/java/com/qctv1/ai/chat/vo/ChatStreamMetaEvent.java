package com.qctv1.ai.chat.vo;

public record ChatStreamMetaEvent(
        String type,
        Long conversationId,
        String title
) {
}

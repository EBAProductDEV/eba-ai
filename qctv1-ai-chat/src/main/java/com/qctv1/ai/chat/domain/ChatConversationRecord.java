package com.qctv1.ai.chat.domain;

import java.time.LocalDateTime;

public record ChatConversationRecord(
        Long id,
        Long userId,
        String title,
        String summary,
        String provider,
        String model,
        int messageCount,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean deleted
) {
}

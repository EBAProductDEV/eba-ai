package com.qctv1.ai.chat.domain;

import java.time.LocalDateTime;

public record ChatMessageRecord(
        Long id,
        Long conversationId,
        Long userId,
        Integer seqNo,
        String role,
        String content,
        String status,
        LocalDateTime createdAt
) {
}

package com.qctv1.ai.chat.vo;

import java.time.LocalDateTime;

public record ChatConversationSummaryVo(
        Long id,
        String title,
        String preview,
        String provider,
        String model,
        LocalDateTime lastMessageAt
) {
}

package com.qctv1.ai.chat.vo;

import java.util.List;

public record ChatConversationDetailVo(
        Long conversationId,
        String title,
        String summary,
        String provider,
        String model,
        List<ChatMessageVo> messages
) {
}

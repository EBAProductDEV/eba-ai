package com.qctv1.ai.chat.domain;

import java.util.List;

public record ConversationContext(
        String summary,
        List<ChatMessageRecord> recentMessages
) {
}

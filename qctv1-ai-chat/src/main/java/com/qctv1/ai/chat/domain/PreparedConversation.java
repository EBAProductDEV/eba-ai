package com.qctv1.ai.chat.domain;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

public record PreparedConversation(
        ChatConversationRecord conversation,
        ChatMessageRecord userMessage,
        String provider,
        String model,
        List<Message> promptMessages
) {
}

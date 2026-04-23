package com.qctv1.ai.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatStreamRequest(
        Long conversationId,
        @NotBlank(message = "message can not be blank") String message,
        String provider,
        String model
) {
}

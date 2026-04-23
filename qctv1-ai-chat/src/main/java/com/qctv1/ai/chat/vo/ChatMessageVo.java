package com.qctv1.ai.chat.vo;

import java.time.LocalDateTime;

public record ChatMessageVo(
        Long id,
        Integer seqNo,
        String role,
        String content,
        String status,
        LocalDateTime createdAt
) {
}

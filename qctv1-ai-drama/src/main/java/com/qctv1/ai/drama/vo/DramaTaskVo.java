package com.qctv1.ai.drama.vo;

import java.time.LocalDateTime;

public record DramaTaskVo(
        Long id,
        String taskType,
        String providerTaskId,
        String status,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

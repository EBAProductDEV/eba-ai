package com.qctv1.ai.drama.domain;

import java.time.LocalDateTime;

public record DramaTaskRecord(
        Long id,
        Long seriesId,
        Long episodeId,
        Long shotId,
        String taskType,
        String providerTaskId,
        String status,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

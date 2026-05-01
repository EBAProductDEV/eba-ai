package com.qctv1.ai.drama.vo;

import java.time.LocalDateTime;

public record DramaTaskVo(
        Long id,
        Long seriesId,
        Long episodeId,
        Long shotId,
        Long characterId,
        Long assetId,
        String targetType,
        Long targetId,
        String assetType,
        String assetSubType,
        String taskType,
        String providerTaskId,
        String status,
        Integer progress,
        String stage,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

package com.qctv1.ai.drama.vo;

import java.time.LocalDateTime;
import java.util.List;

public record DramaTaskCenterItemVo(
        Long id,
        Long seriesId,
        Long episodeId,
        Long shotId,
        Long characterId,
        Long assetId,
        String seriesName,
        String characterName,
        String taskType,
        String targetType,
        Long targetId,
        String assetType,
        String assetSubType,
        String title,
        String description,
        String status,
        Integer progress,
        String stage,
        String stageText,
        Integer currentStep,
        List<String> steps,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

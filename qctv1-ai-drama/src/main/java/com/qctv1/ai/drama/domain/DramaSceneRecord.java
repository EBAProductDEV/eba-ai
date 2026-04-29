package com.qctv1.ai.drama.domain;

import java.time.LocalDateTime;

public record DramaSceneRecord(
        Long id,
        Long seriesId,
        Long episodeId,
        String name,
        String location,
        String timeOfDay,
        String atmosphere,
        String plotPurpose,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
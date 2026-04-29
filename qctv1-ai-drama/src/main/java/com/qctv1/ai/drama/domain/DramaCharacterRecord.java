package com.qctv1.ai.drama.domain;

import java.time.LocalDateTime;

public record DramaCharacterRecord(
        Long id,
        Long seriesId,
        String name,
        String profile,
        String appearance,
        String costume,
        String personality,
        String relationship,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
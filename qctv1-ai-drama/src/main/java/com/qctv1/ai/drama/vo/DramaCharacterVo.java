package com.qctv1.ai.drama.vo;

import java.time.LocalDateTime;

public record DramaCharacterVo(
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
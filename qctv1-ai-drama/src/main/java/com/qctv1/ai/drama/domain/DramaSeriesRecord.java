package com.qctv1.ai.drama.domain;

import java.time.LocalDateTime;

public record DramaSeriesRecord(
        Long id,
        Long userId,
        String name,
        String aspectRatio,
        String type,
        String intro,
        String theme,
        String style,
        String originalStory,
        String storySummary,
        String fullStory,
        String storyStatus,
        Integer totalEpisodes,
        Integer episodeDurationMinutes,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Boolean deleted
) {
}

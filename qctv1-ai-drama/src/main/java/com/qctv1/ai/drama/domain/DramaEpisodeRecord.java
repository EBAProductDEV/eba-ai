package com.qctv1.ai.drama.domain;

import java.time.LocalDateTime;

public record DramaEpisodeRecord(
        Long id,
        Long seriesId,
        Integer episodeNo,
        String title,
        String summary,
        String hook,
        String cliffhanger,
        String script,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

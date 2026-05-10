package com.qctv1.ai.drama.vo;

import java.time.LocalDateTime;

public record DramaSeriesSummaryVo(
        Long id,
        String name,
        String aspectRatio,
        String type,
        String intro,
        String theme,
        String style,
        Integer totalEpisodes,
        Integer episodeDurationMinutes,
        String status,
        LocalDateTime createdAt
) {
}

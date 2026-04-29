package com.qctv1.ai.drama.vo;

import java.time.LocalDateTime;

public record DramaSeriesSummaryVo(
        Long id,
        String name,
        String type,
        String intro,
        String style,
        Integer totalEpisodes,
        Integer episodeDurationMinutes,
        String status,
        LocalDateTime createdAt
) {
}

package com.qctv1.ai.drama.vo;

import java.time.LocalDateTime;
import java.util.List;

public record DramaSeriesDetailVo(
        Long id,
        String name,
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
        List<DramaCharacterVo> characters,
        List<DramaEpisodeVo> episodes,
        List<DramaAssetVo> recentAssets,
        List<DramaTaskVo> recentTasks
) {
}

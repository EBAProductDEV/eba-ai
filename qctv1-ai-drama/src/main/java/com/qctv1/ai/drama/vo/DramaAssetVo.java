package com.qctv1.ai.drama.vo;

import java.time.LocalDateTime;

public record DramaAssetVo(
        Long id,
        Long episodeId,
        Long sceneId,
        Long shotId,
        String assetType,
        String assetSubType,
        Long characterId,
        String fileName,
        String contentType,
        String accessUrl,
        String prompt,
        String status,
        LocalDateTime createdAt
) {
}

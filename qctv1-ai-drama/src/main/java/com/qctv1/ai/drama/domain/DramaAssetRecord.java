package com.qctv1.ai.drama.domain;

import java.time.LocalDateTime;

public record DramaAssetRecord(
        Long id,
        Long seriesId,
        Long episodeId,
        Long sceneId,
        Long shotId,
        Long characterId,
        String assetType,
        String assetSubType,
        Long referenceAssetId,
        String fileName,
        String contentType,
        String localPath,
        String accessUrl,
        String prompt,
        String seed,
        String status,
        LocalDateTime createdAt
) {
}

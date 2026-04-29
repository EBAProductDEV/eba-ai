package com.qctv1.ai.drama.domain;

import java.time.LocalDateTime;

public record DramaAssetRecord(
        Long id,
        Long seriesId,
        Long episodeId,
        Long shotId,
        String assetType,
        String fileName,
        String contentType,
        String localPath,
        String accessUrl,
        LocalDateTime createdAt
) {
}

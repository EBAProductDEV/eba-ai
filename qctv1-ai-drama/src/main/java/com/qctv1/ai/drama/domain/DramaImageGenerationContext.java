package com.qctv1.ai.drama.domain;

import java.nio.file.Path;
import java.util.List;

public record DramaImageGenerationContext(
        Long seriesId,
        Long episodeId,
        Long sceneId,
        Long shotId,
        Long characterId,
        String targetType,
        Long targetId,
        String assetType,
        String assetSubType,
        Long referenceAssetId,
        List<Long> referenceAssetIds,
        String prompt,
        String seed,
        String fileNamePrefix,
        String contentType,
        String imageSize,
        String imageQuality,
        String imageFormat,
        Path saveDirectory
) {
}

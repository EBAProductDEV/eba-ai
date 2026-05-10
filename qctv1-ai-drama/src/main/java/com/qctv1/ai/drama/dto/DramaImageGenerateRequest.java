package com.qctv1.ai.drama.dto;

import java.util.List;

public record DramaImageGenerateRequest(
        String prompt,
        List<Long> referenceAssetIds,
        String imageSize,
        String imageQuality,
        String imageFormat
) {
}

package com.qctv1.ai.drama.dto;

import java.util.List;

public record DramaVideoGenerateRequest(
        String prompt,
        List<Long> referenceAssetIds,
        Integer durationSeconds,
        String resolution,
        Integer fps,
        String ratio
) {
}

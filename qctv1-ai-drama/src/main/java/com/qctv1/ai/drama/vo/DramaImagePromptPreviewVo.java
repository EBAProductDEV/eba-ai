package com.qctv1.ai.drama.vo;

import java.util.Map;
import java.util.List;

public record DramaImagePromptPreviewVo(
        String targetType,
        Long targetId,
        String assetType,
        String assetSubType,
        String title,
        String prompt,
        Map<String, Object> parameters,
        List<DramaAssetVo> referenceImages
) {
}

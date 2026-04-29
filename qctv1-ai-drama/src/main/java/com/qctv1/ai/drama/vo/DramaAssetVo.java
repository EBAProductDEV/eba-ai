package com.qctv1.ai.drama.vo;

import java.time.LocalDateTime;

public record DramaAssetVo(
        Long id,
        String assetType,
        String fileName,
        String contentType,
        String accessUrl,
        LocalDateTime createdAt
) {
}

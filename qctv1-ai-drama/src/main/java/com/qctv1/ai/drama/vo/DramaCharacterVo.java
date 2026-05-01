package com.qctv1.ai.drama.vo;

import java.time.LocalDateTime;

public record DramaCharacterVo(
        Long id,
        Long seriesId,
        String name,
        String profile,
        String appearance,
        String costume,
        String personality,
        String relationship,
        String visualProfile,
        Long primaryReferenceAssetId,
        Long avatarAssetId,
        String primaryReferenceAccessUrl,
        String avatarAccessUrl,
        String imageSeed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

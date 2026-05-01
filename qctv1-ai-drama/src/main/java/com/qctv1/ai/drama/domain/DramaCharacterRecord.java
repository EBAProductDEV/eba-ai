package com.qctv1.ai.drama.domain;

import java.time.LocalDateTime;

public record DramaCharacterRecord(
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
        String imageSeed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

package com.qctv1.ai.drama.domain;

import java.time.LocalDateTime;

public record DramaShotRecord(
        Long id,
        Long episodeId,
        Long sceneId,
        Integer shotNo,
        String shotSize,
        String action,
        String dialogue,
        String imagePrompt,
        String videoPrompt,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
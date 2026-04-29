package com.qctv1.ai.drama.vo;

public record DramaShotVo(
        Long id,
        Long episodeId,
        Long sceneId,
        Integer shotNo,
        String shotSize,
        String action,
        String dialogue,
        String imagePrompt,
        String videoPrompt,
        String status
) {
}
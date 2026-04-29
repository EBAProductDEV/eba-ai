package com.qctv1.ai.drama.vo;

public record DramaSceneVo(
        Long id,
        String name,
        String location,
        String timeOfDay,
        String atmosphere,
        String plotPurpose
) {
}
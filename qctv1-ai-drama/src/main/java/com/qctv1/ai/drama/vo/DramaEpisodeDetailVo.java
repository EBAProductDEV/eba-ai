package com.qctv1.ai.drama.vo;

import java.util.List;

public record DramaEpisodeDetailVo(
        Long seriesId,
        DramaEpisodeVo episode,
        List<DramaSceneVo> scenes,
        List<DramaShotVo> shots,
        List<DramaAssetVo> assets,
        List<DramaTaskVo> tasks
) {
}
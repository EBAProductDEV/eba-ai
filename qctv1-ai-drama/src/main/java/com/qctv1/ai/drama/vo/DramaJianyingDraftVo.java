package com.qctv1.ai.drama.vo;

import java.time.LocalDateTime;

public record DramaJianyingDraftVo(
        Long id,
        Long editTaskId,
        Long episodeId,
        String referenceVideoPath,
        String referenceVideoUrl,
        String cleanVideoPath,
        String voiceMixPath,
        String voiceClipsDir,
        String bgmSfxPath,
        String subtitleSrtPath,
        String danmakuCsvPath,
        String packageZipPath,
        String packageDownloadUrl,
        String manifestPath,
        LocalDateTime createdAt
) {
}

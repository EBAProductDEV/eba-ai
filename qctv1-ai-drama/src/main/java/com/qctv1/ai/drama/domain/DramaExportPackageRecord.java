package com.qctv1.ai.drama.domain;

import java.time.LocalDateTime;

public record DramaExportPackageRecord(
        Long id,
        Long editTaskId,
        Long episodeId,
        String referenceVideoPath,
        String cleanVideoPath,
        String voiceMixPath,
        String voiceClipsDir,
        String bgmSfxPath,
        String subtitleSrtPath,
        String danmakuCsvPath,
        String packageZipPath,
        String manifestPath,
        LocalDateTime createdAt
) {
}

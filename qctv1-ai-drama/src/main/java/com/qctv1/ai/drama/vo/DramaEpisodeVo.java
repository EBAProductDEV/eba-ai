package com.qctv1.ai.drama.vo;

public record DramaEpisodeVo(
        Long id,
        Integer episodeNo,
        String title,
        String summary,
        String novelContent,
        String hook,
        String cliffhanger,
        String script,
        String status
) {
}

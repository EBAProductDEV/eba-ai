package com.qctv1.ai.drama.dto;

/**
 * 故事总纲保存请求。
 *
 * <p>故事总纲属于短剧项目级内容，用于承接“先定整部故事，再拆分分集”的生产流程。</p>
 */
public record DramaStorySaveRequest(
        String originalStory,
        String storySummary,
        String fullStory
) {
}

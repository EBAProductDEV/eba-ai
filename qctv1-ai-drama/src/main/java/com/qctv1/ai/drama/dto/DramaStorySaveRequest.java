package com.qctv1.ai.drama.dto;

/**
 * 故事创作页保存请求。
 *
 * <p>第一阶段只保存用户可见的故事原文和故事摘要；完整故事大纲在生成分集时作为内部生产蓝图自动生成。</p>
 */
public record DramaStorySaveRequest(
        String originalStory,
        String storySummary,
        String fullStory
) {
}
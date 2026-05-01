package com.qctv1.ai.drama.dto;

/**
 * 根据用户确认后的故事雏形生成故事内容。
 *
 * @param storyBrief 用户确认或二次编辑后的故事策划简案
 * @param requirement 用户补充要求
 */
public record DramaStoryGenerateRequest(
        String storyBrief,
        String requirement
) {
}
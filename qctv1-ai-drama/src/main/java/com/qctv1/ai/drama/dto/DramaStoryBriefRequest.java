package com.qctv1.ai.drama.dto;

/**
 * 故事雏形生成请求。
 *
 * @param requirement 用户补充要求，例如题材偏好、角色关系、爽点方向、禁用设定等
 */
public record DramaStoryBriefRequest(
        String requirement
) {
}
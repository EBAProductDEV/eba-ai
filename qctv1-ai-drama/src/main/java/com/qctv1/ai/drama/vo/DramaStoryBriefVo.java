package com.qctv1.ai.drama.vo;

/**
 * 返回给前端的故事雏形。
 *
 * @param storyBrief AI 基于项目资料生成的故事策划简案
 * @param modelReady 当前文本模型配置是否已就绪
 */
public record DramaStoryBriefVo(
        String storyBrief,
        Boolean modelReady
) {
}
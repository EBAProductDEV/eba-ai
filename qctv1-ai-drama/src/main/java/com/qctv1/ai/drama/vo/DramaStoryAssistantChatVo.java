package com.qctv1.ai.drama.vo;

/**
 * 故事原文 AI 修改结果。
 *
 * @param answer 简要说明本次修改了什么
 * @param accepted 本轮问题是否属于短剧故事/小说创作范围
 * @param modelReady 当前文本模型配置是否已就绪
 * @param originalStory AI 修改后的故事原文；前端收到后直接刷新故事原文编辑框
 */
public record DramaStoryAssistantChatVo(
        String answer,
        Boolean accepted,
        Boolean modelReady,
        String originalStory,
        String operation
) {
}

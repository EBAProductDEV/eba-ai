package com.qctv1.ai.drama.dto;

import java.util.List;

/**
 * 故事原文 AI 助手对话请求。
 *
 * @param question 用户本轮问题
 * @param storySummary 前端当前编辑中的故事摘要，用于让 AI 读取尚未保存的上下文
 * @param originalStory 前端当前编辑中的故事原文，用于让 AI 读取尚未保存的上下文
 * @param history 本轮之前的短对话历史
 */
public record DramaStoryAssistantChatRequest(
        String question,
        String storySummary,
        String originalStory,
        List<Message> history,
        String mode,
        Boolean planConfirmed,
        String planContent
) {
    public record Message(
            String role,
            String content
    ) {
    }
}

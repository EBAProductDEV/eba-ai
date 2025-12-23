package com.eba.agent.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: 粪豆儿
 * @CreateTime: 2025-12-23 20:51:43
 * @Desc:
 */
@Configuration
public class ChatModelConfig {

    @Bean(name = "ollamaQwenModel")
    public ChatModel ollamaQwenModel() {
        return OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl("http://127.0.0.1:11434").build())
                .defaultOptions(OllamaChatOptions.builder().model("qwen3-vl:2b").build())
                .build();
    }

    @Bean(name = "dashScopeQwenModel")
    public ChatModel dashScopeQwenModel() {
        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder().apiKey(System.getenv("EBA_AI_API_KEY")).build())
                .defaultOptions(DashScopeChatOptions.builder().model("qwen3-max").build())
                .build();
    }
}

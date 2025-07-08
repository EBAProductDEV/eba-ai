package com.eba.ai.hello.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: 粪豆儿
 * @CreateTime: 2025-07-08 19:05:02
 * @Desc:
 */
@Configuration
public class ChatClientConfig {

    private static final String DEFAULT_PROMPT = "你是一个博学的智能聊天助手，请根据用户提问回答！";

    @Bean
    public ChatClient dashScopeChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
                .defaultSystem(DEFAULT_PROMPT)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor()
                )
                .defaultOptions(
                        DashScopeChatOptions.builder()
                                .withTopP(0.7)
                                .build()
                )
                .build();
    }
}

package com.eba.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: 粪豆儿
 * @CreateTime: 2025-12-23 20:32:44
 * @Desc:
 */
@Configuration
public class ChatClientConfig {

    @Bean(name = "ollamaQwenClient")
    public ChatClient ollamaQwenClient(@Qualifier("ollamaQwenModel") ChatModel ollamaQwenModel) {
        return ChatClient.builder(ollamaQwenModel).build();
    }

    @Bean(name = "dashScopeQwenClient")
    public ChatClient dashScopeQwenClient(@Qualifier("dashScopeQwenModel") ChatModel dashScopeQwenModel) {
        return ChatClient.builder(dashScopeQwenModel).build();
    }

}

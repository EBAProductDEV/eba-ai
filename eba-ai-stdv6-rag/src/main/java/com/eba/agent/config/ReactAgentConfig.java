package com.eba.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: 粪豆儿
 * @CreateTime: 2025-12-23 20:35:04
 * @Desc:
 */
@Configuration
public class ReactAgentConfig {

    @Bean(name = "ollamaQwenAgent")
    public ReactAgent reactAgent(@Qualifier("ollamaQwenModel") ChatModel ollamaQwenModel) {
        return ReactAgent.builder()
                .name("ollamaQwenAgent")
                .model(ollamaQwenModel)
                .build();
    }

    @Bean(name = "dashScopeQwenAgent")
    public ReactAgent dashScopeQwenAgent(@Qualifier("dashScopeQwenModel") ChatModel dashScopeQwenModel) {
        return ReactAgent.builder()
                .name("dashScopeQwenAgent")
                .model(dashScopeQwenModel)
                .build();
    }
}

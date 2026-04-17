package com.qctv1.ai.chat.model;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DashScopeModel {

    @Bean(name = "dash3_5-35b-ScopeQwenModel")
    public ChatModel dashScopeQwenModel(
            @Value("${spring.ai.dashscope.api-key}") String apiKey,
            @Value("${spring.ai.dashscope.chat.options.model:qwen3.5-35b-a3b}") String model
    ) {
        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder()
                        .apiKey(apiKey)
                        .build())
                .defaultOptions(DashScopeChatOptions.builder()
                        .model(model)
                        .multiModel(true)
                        .build())
                .build();
    }
}

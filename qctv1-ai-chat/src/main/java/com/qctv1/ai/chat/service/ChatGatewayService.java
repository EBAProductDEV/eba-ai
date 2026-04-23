package com.qctv1.ai.chat.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class ChatGatewayService {

    private final ChatClient openAiChatClient;
    private final ChatClient dashScopeChatClient;

    public ChatGatewayService(
            @Qualifier("gpt5_3_codexModel") ChatModel openAiChatModel,
            @Qualifier("dash3_5-35b-ScopeQwenModel") ChatModel dashScopeChatModel
    ) {
        this.openAiChatClient = ChatClient.builder(openAiChatModel).build();
        this.dashScopeChatClient = ChatClient.builder(dashScopeChatModel).build();
    }

    public Flux<String> stream(List<Message> messages, String provider, String model) {
        Prompt prompt = buildPrompt(messages, provider, model);
        return resolveChatClient(provider, model)
                .prompt(prompt)
                .stream()
                .content();
    }

    public String call(List<Message> messages, String provider, String model) {
        Prompt prompt = buildPrompt(messages, provider, model);
        return resolveChatClient(provider, model)
                .prompt(prompt)
                .call()
                .content();
    }

    private Prompt buildPrompt(List<Message> messages, String provider, String model) {
        if ("dashscope".equalsIgnoreCase(provider) || (model != null && model.toLowerCase().contains("qwen"))) {
            return new Prompt(messages, DashScopeChatOptions.builder()
                    .model(model == null || model.isBlank() ? "qwen3.5-35b-a3b" : model)
                    .multiModel(true)
                    .build());
        }
        return new Prompt(messages, OpenAiChatOptions.builder()
                .model(model == null || model.isBlank() ? "gpt-5.3-codex-spark" : model)
                .build());
    }

    private ChatClient resolveChatClient(String provider, String model) {
        if ("dashscope".equalsIgnoreCase(provider)) {
            return dashScopeChatClient;
        }
        if (model != null && model.toLowerCase().contains("qwen")) {
            return dashScopeChatClient;
        }
        return openAiChatClient;
    }
}

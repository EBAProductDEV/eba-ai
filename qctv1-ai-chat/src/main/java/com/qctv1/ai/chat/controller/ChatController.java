package com.qctv1.ai.chat.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * @Author: 粪豆�?
 * @CreateTime: 2026-03-23 17:41:26
 * @Desc:
 */
@RestController
@RequestMapping("/chat")


public class ChatController {

    private final ChatClient chatClient;

    public ChatController(@Qualifier("gpt5_3_codexModel") ChatModel gpt5_3_codexModel) {
        this.chatClient = ChatClient.builder(gpt5_3_codexModel).build();
    }

    // 提示词模�?
    @GetMapping("/prompt/template")
    public Flux<String> promptTemplate(@RequestParam String topic, @RequestParam String count, @RequestParam String format) {
        PromptTemplate promptTemplate = new PromptTemplate(
                "请你将一个关于{topic}的故事，要求字数在{count}左右，最后以{format}格式输出"
        );
        Message message = promptTemplate.createMessage(Map.of(
                "topic", topic,
                "count", count,
                "format", format
        ));
        Prompt prompt = new Prompt(List.of( message));
        // 模板调用：正常文本返�?
        // return chatClient.prompt(prompt).stream().content();
        // 结构化返�?.entity(T.class)
        return chatClient.prompt(prompt).stream().content();
    }

    // 流式调用
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam String query) {
        return chatClient.prompt()
                .user(query)
                .stream()
                .content()
                .timeout(Duration.ofSeconds(60));
    }
    // 单次用于排错的非流式
    @GetMapping("/simple")
    public String simple(@RequestParam String query) {
        return chatClient.prompt().user(query).call().content();
    }

}

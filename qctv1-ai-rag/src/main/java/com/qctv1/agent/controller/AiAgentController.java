package com.qctv1.agent.controller;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.fastjson.JSON;
import com.qctv1.agent.vo.MessageVO;
import com.qctv1.tool.StreamR;
import jakarta.annotation.Resource;
import org.reactivestreams.Publisher;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @Author: 粪豆儿
 * @CreateTime: 2025-12-23 20:37:58
 * @Desc:
 */
@RestController
@RequestMapping("/ai/agent")
public class AiAgentController {

    @Resource(name = "dashScopeQwenModel")
    private ChatModel chatModel;
    @Resource(name = "ollamaQwenClient")
    private ChatClient chatClient;
    @Resource
    @Qualifier("dashScopeQwenAgent")
    private ReactAgent reactAgent;

    @GetMapping("/client/chat")
    public String chat(@RequestParam String question) {
        // 简单调用
        return chatClient.prompt().messages().user( question).call().content();
    }
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> react(@RequestBody MessageVO vo) {

        String prompt = vo.getPrompt();
        String messageId = UUID.randomUUID().toString();

        return chatClient
                .prompt(prompt)
                .stream()
                .chatResponse()
                .map(resp -> {
                    // 直接返回序列化后的JSON字符串
                    return JSON.toJSONString(StreamR.deltaText(resp.getResult().getOutput().getText(), messageId));
                });
    }
    // 提示词模板
    @GetMapping("/prompt/template")
    public Flux<String> promptTemplate(@RequestParam String topic,@RequestParam String count,@RequestParam String format) {
        PromptTemplate promptTemplate = new PromptTemplate(
                "请你将一个关于{topic}的故事，要求字数在{count}左右，最后以{format}格式输出"
        );
        Message message = promptTemplate.createMessage(Map.of(
                "topic", topic,
                "count", count,
                "format", format
        ));
        Prompt prompt = new Prompt(List.of( message));
        // 模板调用：正常文本返回
        // return chatClient.prompt(prompt).stream().content();
        // 结构化返回 .entity(T.class)
        return chatClient.prompt(prompt).call().entity(Flux.class);
    }

}

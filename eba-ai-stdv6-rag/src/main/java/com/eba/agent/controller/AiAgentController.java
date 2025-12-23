package com.eba.agent.controller;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
        return chatClient.prompt().messages().user( question).call().content();
    }
    @RequestMapping("/agent/chat")
    public String react(@RequestParam String question) {
//        // 多个消息
//        List<Message> messages = List.of(
//                new UserMessage("我想了解 Java 多线程"),
//                new UserMessage("特别是线程池的使用")
//        );
//        return  reactAgent.call(messages).
        return "";
    }
}

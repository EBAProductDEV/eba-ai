package com.eba.ai.hello;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: 粪豆儿
 * @CreateTime: 2025-07-08 18:33:51
 * @Desc:
 */
@RestController
@RequestMapping("/hello")
public class controller {

    @Autowired
    @Qualifier("dashScopeChatClient")
    private ChatClient dashScopeChatClient;


    /**
     * ChatClient 简单调用
     */
    @GetMapping("/simple/chat")
    public String simpleChat(@RequestParam(value = "query", defaultValue = "你好，很高兴认识你，能简单介绍一下自己吗？")String query) {

        return dashScopeChatClient.prompt(query).call().content();
    }
}

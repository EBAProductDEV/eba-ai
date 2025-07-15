package com.eba.ai.hello.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeImageApi;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: 粪豆儿
 * @CreateTime: 2025-07-10 14:53:07
 * @Desc:
 */
@RestController
@RequestMapping("/image")
public class ImageController {

    @Autowired
    private ImageModel imageModel;

    @GetMapping("/generate")
    public String generateImage(@RequestParam String prompt) {

        ImagePrompt imagePrompt = new ImagePrompt(prompt);
        ImageResponse call = imageModel.call(imagePrompt);

        // 返回图片链接
        return call.getResult().getOutput().getUrl();
    }
}

package com.qctv1.ai.hello.controller;

import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
@ConditionalOnBean(ImageModel.class)
public class ImageController {

    @Autowired
    @Qualifier("openAiImageModel")
    private ImageModel imageModel;

    @GetMapping("/generate")
    public String generateImage(@RequestParam String prompt) {

        ImagePrompt imagePrompt = new ImagePrompt(prompt);
        ImageResponse call = imageModel.call(imagePrompt);

        // 返回图片链接
        return call.getResult().getOutput().getUrl();
    }
}

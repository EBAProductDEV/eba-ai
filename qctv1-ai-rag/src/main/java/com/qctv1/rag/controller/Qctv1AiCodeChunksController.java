package com.qctv1.rag.controller;


import com.qctv1.rag.entity.AiCodeChunks;
import com.qctv1.rag.service.IAiCodeChunksService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 代码片段向量知识库 前端控制器
 * </p>
 *
 * @author 粪豆儿
 * @since 2025-07-15
 */
@RestController
@RequestMapping("/ai/code")
public class Qctv1AiCodeChunksController {

    @Autowired
    private IAiCodeChunksService aiCodeChunksService;

    @GetMapping("/list")
    public List<AiCodeChunks> list() {
        return aiCodeChunksService.getCodeChunkList("qq");
    }

}

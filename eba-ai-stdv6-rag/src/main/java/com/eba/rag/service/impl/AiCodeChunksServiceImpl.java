package com.eba.rag.service.impl;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.eba.rag.entity.AiCodeChunks;
import com.eba.rag.mapper.AiCodeChunksMapper;
import com.eba.rag.service.IAiCodeChunksService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 代码片段向量知识库 服务实现类
 * </p>
 *
 * @author 粪豆儿
 * @since 2025-07-15
 */
@Service
public class AiCodeChunksServiceImpl extends ServiceImpl<AiCodeChunksMapper, AiCodeChunks> implements IAiCodeChunksService {
    @Autowired
    private  EmbeddingModel embeddingModel;

    @Override
    public boolean saveCodeChunksBatch(List<AiCodeChunks> aiCodeChunks) {

        return this.saveBatch(aiCodeChunks);
    }

    @Override
    public List<AiCodeChunks> getCodeChunkList(String filePath) {
        EmbeddingResponse contentVector = embeddingModel.embedForResponse(List.of("你好"));
        return List.of();
    }
}

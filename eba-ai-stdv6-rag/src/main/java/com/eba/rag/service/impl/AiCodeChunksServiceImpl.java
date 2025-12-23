package com.eba.rag.service.impl;


import com.eba.rag.entity.AiCodeChunks;
import com.eba.rag.mapper.AiCodeChunksMapper;
import com.eba.rag.service.IAiCodeChunksService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.eba.rag.util.FileScannerUtil;
import jakarta.annotation.Resource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.File;
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
    @Resource(name = "ollamaEmbeddingModel")
    private  EmbeddingModel embeddingModel;

    @Override
    public boolean saveCodeChunksBatch(List<AiCodeChunks> aiCodeChunks) {

        return this.saveBatch(aiCodeChunks);
    }

    @Override
    public List<AiCodeChunks> getCodeChunkList(String filePath) {
        // 获取本地代码文件
        List<File> codeFileList = FileScannerUtil.scanFiles(filePath, List.of("java", "js", "ts", "html", "vue"));
        // 将本地文件代码切分成固定大小的 chunk
        for (File file : codeFileList) {

        }
        // 生成向量模型
        EmbeddingResponse contentVector = embeddingModel.embedForResponse(List.of("你好"));

        // 封装数据库实体类

        // 存库
        return List.of();
    }
}

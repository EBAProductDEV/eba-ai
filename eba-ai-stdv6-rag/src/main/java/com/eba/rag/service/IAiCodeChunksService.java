package com.eba.rag.service;

import com.eba.rag.entity.AiCodeChunks;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 代码片段向量知识库 服务类
 * </p>
 *
 * @author 粪豆儿
 * @since 2025-07-15
 */
public interface IAiCodeChunksService extends IService<AiCodeChunks> {

    /**
     * 添加代码片段向量知识库
     *
     * @param aiCodeChunks
     * @return
     */
    boolean saveCodeChunksBatch(List<AiCodeChunks> aiCodeChunks);

}

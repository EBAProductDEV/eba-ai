package com.qctv1.ai.drama.rag;

import com.qctv1.ai.drama.config.DramaProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisStackDramaVectorStore implements DramaVectorStore, ApplicationRunner {

    private final DramaProperties properties;
    private final StringRedisTemplate redisTemplate;

    public RedisStackDramaVectorStore(DramaProperties properties, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureIndex();
    }

    @Override
    public void ensureIndex() {
        // Spring Data Redis 不直接封装 Redis Stack FT.CREATE；这里先做连通性和配置占位。
        // 后续可以改为 RedisConnection 执行 FT.INFO/FT.CREATE，保证 qctv1_ai_drama_chunk_idx 幂等创建。
        try {
            redisTemplate.opsForValue().setIfAbsent(properties.getVectorStore().getKeyPrefix() + "index:marker", properties.getVectorStore().getIndexName());
        } catch (DataAccessException ex) {
            // 本地只做页面或接口骨架联调时，Redis Stack 可能尚未启动；这里不阻断服务启动。
        }
    }

    @Override
    public void upsertChunk(Long seriesId, Long chunkId, String content, float[] embedding) {
        // v1 保存文本占位，真实向量字段后续应以 Redis Stack HASH + VECTOR 字段写入。
        String key = properties.getVectorStore().getKeyPrefix() + chunkId;
        redisTemplate.opsForHash().put(key, "seriesId", String.valueOf(seriesId));
        redisTemplate.opsForHash().put(key, "content", content == null ? "" : content);
        redisTemplate.opsForHash().put(key, "dimension", String.valueOf(embedding == null ? 0 : embedding.length));
    }

    @Override
    public List<RagSearchResult> search(Long seriesId, float[] queryEmbedding, int topK) {
        // 搜索接口先固定存在，后续替换为 FT.SEARCH KNN 并按 seriesId 过滤。
        return List.of();
    }
}

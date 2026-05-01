package com.qctv1.ai.drama.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Service
public class DramaEpisodeBreakdownMemoryService {

    private static final Logger log = LoggerFactory.getLogger(DramaEpisodeBreakdownMemoryService.class);

    private static final String KEY_PREFIX = "qctv1:ai:drama:episode-breakdown-memory:";
    private static final int MAX_MEMORY_SIZE = 20;
    private static final Duration MEMORY_TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;

    public DramaEpisodeBreakdownMemoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Long recordRunning(Long seriesId, String requirement) {
        if (seriesId == null || requirement == null || requirement.isBlank()) {
            return null;
        }
        Long memoryId = System.currentTimeMillis();
        String key = buildKey(seriesId);
        String normalizedRequirement = requirement.trim();
        try {
            // 这里只做轻量记忆：保存最近的重新拆分要求，用于下一次 AI 拆分时作为上下文参考。
            redisTemplate.opsForList().leftPush(key, normalizedRequirement);
            redisTemplate.opsForList().trim(key, 0, MAX_MEMORY_SIZE - 1);
            redisTemplate.expire(key, MEMORY_TTL);
        } catch (RuntimeException ex) {
            // 记忆不是强一致业务数据，Redis 临时不可用不能阻断用户重新拆分分集。
            log.warn("记录分集重新拆分要求失败，seriesId={}，原因：{}", seriesId, ex.getMessage());
        }
        return memoryId;
    }

    public void markSucceeded(Long id, int generatedEpisodeCount) {
        // Redis 版本只保存用户要求文本，不维护执行状态。
    }

    public void markFailed(Long id, String errorMessage) {
        // Redis 版本只保存用户要求文本，不维护执行状态。
    }

    public List<String> listRecentRequirements(Long seriesId, int limit) {
        if (seriesId == null) {
            return Collections.emptyList();
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_MEMORY_SIZE));
        try {
            List<String> requirements = redisTemplate.opsForList().range(buildKey(seriesId), 0, safeLimit - 1);
            if (requirements == null) {
                return Collections.emptyList();
            }
            return requirements.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("读取分集重新拆分要求记忆失败，seriesId={}，原因：{}", seriesId, ex.getMessage());
            return Collections.emptyList();
        }
    }

    private String buildKey(Long seriesId) {
        return KEY_PREFIX + seriesId;
    }
}

package com.qctv1.ai.chat.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qctv1.ai.chat.config.ChatMemoryProperties;
import com.qctv1.ai.chat.domain.ChatConversationRecord;
import com.qctv1.ai.chat.domain.ChatMessageRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ChatMemoryRedisCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatMemoryProperties properties;

    public ChatMemoryRedisCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ChatMemoryProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ConversationMetaCache getConversationMeta(Long conversationId) {
        String raw = redisTemplate.opsForValue().get(metaKey(conversationId));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, ConversationMetaCache.class);
        } catch (JsonProcessingException ex) {
            redisTemplate.delete(metaKey(conversationId));
            return null;
        }
    }

    public void saveConversationMeta(ChatConversationRecord conversation) {
        saveConversationMeta(new ConversationMetaCache(
                conversation.id(),
                conversation.title(),
                conversation.summary(),
                conversation.provider(),
                conversation.model(),
                conversation.lastMessageAt()
        ));
    }

    public void saveConversationMeta(ConversationMetaCache cache) {
        try {
            redisTemplate.opsForValue().set(
                    metaKey(cache.conversationId()),
                    objectMapper.writeValueAsString(cache),
                    ttl()
            );
        } catch (JsonProcessingException ignored) {
        }
    }

    public List<ChatMessageRecord> getRecentMessages(Long conversationId) {
        List<String> values = redisTemplate.opsForList().range(recentKey(conversationId), 0, -1);
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<ChatMessageRecord> messages = new ArrayList<>();
        for (String value : values) {
            try {
                messages.add(objectMapper.readValue(value, ChatMessageRecord.class));
            } catch (JsonProcessingException ignored) {
            }
        }
        return messages;
    }

    public void replaceRecentMessages(Long conversationId, List<ChatMessageRecord> messages) {
        String key = recentKey(conversationId);
        redisTemplate.delete(key);
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<String> payload = messages.stream()
                .map(this::serialize)
                .filter(value -> value != null)
                .toList();
        if (!payload.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(key, payload);
            redisTemplate.expire(key, ttl());
        }
    }

    public void pushRecentMessage(Long conversationId, ChatMessageRecord message) {
        String payload = serialize(message);
        if (payload == null) {
            return;
        }
        String key = recentKey(conversationId);
        redisTemplate.opsForList().rightPush(key, payload);
        redisTemplate.opsForList().trim(key, -properties.getRecentMessageLimit(), -1);
        redisTemplate.expire(key, ttl());
    }

    public void touchRecentConversation(Long userId, Long conversationId, LocalDateTime lastMessageAt) {
        String key = recentConversationsKey(userId);
        double score = lastMessageAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        redisTemplate.opsForZSet().add(key, conversationId.toString(), score);
        redisTemplate.expire(key, ttl());
    }

    public List<Long> getRecentConversationIds(Long userId, int limit) {
        Set<String> values = redisTemplate.opsForZSet().reverseRange(recentConversationsKey(userId), 0, Math.max(limit - 1, 0));
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(Long::valueOf).collect(Collectors.toList());
    }

    private String serialize(ChatMessageRecord message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private Duration ttl() {
        return Duration.ofDays(properties.getRedisTtlDays());
    }

    private String metaKey(Long conversationId) {
        return "qctv1:ai:chat:conv:" + conversationId + ":meta";
    }

    private String recentKey(Long conversationId) {
        return "qctv1:ai:chat:conv:" + conversationId + ":recent";
    }

    private String recentConversationsKey(Long userId) {
        return "qctv1:ai:chat:user:" + userId + ":recent_conversations";
    }

    public record ConversationMetaCache(
            Long conversationId,
            String title,
            String summary,
            String provider,
            String model,
            LocalDateTime lastMessageAt
    ) {
    }
}

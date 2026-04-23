package com.qctv1.ai.chat.service;

import com.qctv1.ai.chat.cache.ChatMemoryRedisCache;
import com.qctv1.ai.chat.cache.ChatMemoryRedisCache.ConversationMetaCache;
import com.qctv1.ai.chat.config.ChatMemoryProperties;
import com.qctv1.ai.chat.domain.ChatConversationRecord;
import com.qctv1.ai.chat.domain.ChatMessageRecord;
import com.qctv1.ai.chat.domain.ConversationContext;
import com.qctv1.ai.chat.domain.PreparedConversation;
import com.qctv1.ai.chat.dto.ChatConversationCreateRequest;
import com.qctv1.ai.chat.dto.ChatStreamRequest;
import com.qctv1.ai.chat.repository.ChatConversationRepository;
import com.qctv1.ai.chat.repository.ChatMessageRepository;
import com.qctv1.ai.chat.support.BusinessException;
import com.qctv1.ai.chat.vo.ChatConversationCreateVo;
import com.qctv1.ai.chat.vo.ChatConversationDetailVo;
import com.qctv1.ai.chat.vo.ChatConversationSummaryVo;
import com.qctv1.ai.chat.vo.ChatMessageVo;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ChatConversationService {

    private static final String DEFAULT_TITLE = "New conversation";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatMemoryRedisCache redisCache;
    private final ChatMemoryProperties properties;
    private final ChatGatewayService chatGatewayService;

    public ChatConversationService(
            ChatConversationRepository conversationRepository,
            ChatMessageRepository messageRepository,
            ChatMemoryRedisCache redisCache,
            ChatMemoryProperties properties,
            ChatGatewayService chatGatewayService
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.redisCache = redisCache;
        this.properties = properties;
        this.chatGatewayService = chatGatewayService;
    }

    @Transactional
    public ChatConversationCreateVo createConversation(Long userId, ChatConversationCreateRequest request) {
        ensureLogin(userId);
        String provider = normalizeProvider(request == null ? null : request.provider(), null);
        String model = normalizeModel(provider, request == null ? null : request.model(), null);
        ChatConversationRecord conversation = conversationRepository.create(userId, DEFAULT_TITLE, provider, model);
        redisCache.saveConversationMeta(conversation);
        return new ChatConversationCreateVo(conversation.id(), conversation.title(), conversation.provider(), conversation.model());
    }

    public List<ChatConversationSummaryVo> listConversations(Long userId) {
        ensureLogin(userId);
        List<Long> cachedIds = redisCache.getRecentConversationIds(userId, 20);
        List<ChatConversationSummaryVo> summaries = conversationRepository.listByUserId(userId, 20);
        if (!cachedIds.isEmpty()) {
            summaries.sort(Comparator.comparingInt(item -> {
                int idx = cachedIds.indexOf(item.id());
                return idx >= 0 ? idx : Integer.MAX_VALUE;
            }));
        }
        return summaries;
    }

    public ChatConversationDetailVo getConversationDetail(Long userId, Long conversationId) {
        ensureLogin(userId);
        ChatConversationRecord conversation = getConversation(userId, conversationId);
        List<ChatMessageVo> messages = messageRepository.listByConversationId(conversationId, userId).stream()
                .map(message -> new ChatMessageVo(
                        message.id(),
                        message.seqNo(),
                        message.role(),
                        message.content(),
                        message.status(),
                        message.createdAt()
                ))
                .toList();
        redisCache.touchRecentConversation(userId, conversationId, conversation.lastMessageAt() == null ? LocalDateTime.now() : conversation.lastMessageAt());
        return new ChatConversationDetailVo(
                conversation.id(),
                conversation.title(),
                conversation.summary(),
                conversation.provider(),
                conversation.model(),
                messages
        );
    }

    @Transactional
    public PreparedConversation prepareConversation(Long userId, ChatStreamRequest request) {
        ensureLogin(userId);
        ChatConversationRecord conversation = resolveConversationForWrite(userId, request.conversationId(), request.provider(), request.model());
        int nextSeq = conversation.messageCount() + 1;
        ChatMessageRecord userMessage = messageRepository.save(
                conversation.id(),
                userId,
                nextSeq,
                ROLE_USER,
                request.message(),
                STATUS_SUCCESS
        );
        redisCache.pushRecentMessage(conversation.id(), userMessage);

        ConversationContext context = loadContextBeforeMessage(conversation, userId, userMessage.seqNo());
        List<Message> promptMessages = buildPromptMessages(context, request.message());

        return new PreparedConversation(
                conversation,
                userMessage,
                conversation.provider(),
                conversation.model(),
                promptMessages
        );
    }

    @Transactional
    public ChatConversationRecord finalizeConversation(
            Long userId,
            PreparedConversation preparedConversation,
            String assistantContent,
            boolean success
    ) {
        ChatConversationRecord conversation = getConversation(userId, preparedConversation.conversation().id());
        int assistantSeqNo = preparedConversation.userMessage().seqNo() + 1;
        String normalizedContent = assistantContent == null ? "" : assistantContent.trim();
        if (normalizedContent.isBlank()) {
            normalizedContent = success ? "" : "[error] AI service error";
        }
        ChatMessageRecord assistantMessage = messageRepository.save(
                conversation.id(),
                userId,
                assistantSeqNo,
                ROLE_ASSISTANT,
                normalizedContent,
                success ? STATUS_SUCCESS : STATUS_FAILED
        );
        redisCache.pushRecentMessage(conversation.id(), assistantMessage);

        int messageCount = assistantSeqNo;
        LocalDateTime lastMessageAt = assistantMessage.createdAt();
        String title = resolveTitle(conversation.title(), preparedConversation.userMessage().content());
        String summary = maybeRefreshSummary(conversation, userId, messageCount, preparedConversation.provider(), preparedConversation.model());

        conversationRepository.updateConversationState(
                conversation.id(),
                userId,
                title,
                summary,
                preparedConversation.provider(),
                preparedConversation.model(),
                messageCount,
                lastMessageAt
        );

        ChatConversationRecord refreshed = getConversation(userId, conversation.id());
        redisCache.saveConversationMeta(refreshed);
        redisCache.touchRecentConversation(userId, refreshed.id(), lastMessageAt);
        return refreshed;
    }

    private ChatConversationRecord resolveConversationForWrite(Long userId, Long conversationId, String provider, String model) {
        if (conversationId == null) {
            String normalizedProvider = normalizeProvider(provider, null);
            String normalizedModel = normalizeModel(normalizedProvider, model, null);
            ChatConversationRecord created = conversationRepository.create(userId, DEFAULT_TITLE, normalizedProvider, normalizedModel);
            redisCache.saveConversationMeta(created);
            return created;
        }

        ChatConversationRecord existing = getConversation(userId, conversationId);
        String normalizedProvider = normalizeProvider(provider, existing.provider());
        String normalizedModel = normalizeModel(normalizedProvider, model, existing.model());
        if (normalizedProvider.equals(existing.provider()) && normalizedModel.equals(existing.model())) {
            return existing;
        }
        conversationRepository.updateConversationState(
                existing.id(),
                userId,
                existing.title(),
                existing.summary(),
                normalizedProvider,
                normalizedModel,
                existing.messageCount(),
                existing.lastMessageAt() == null ? LocalDateTime.now() : existing.lastMessageAt()
        );
        ChatConversationRecord updated = getConversation(userId, existing.id());
        redisCache.saveConversationMeta(updated);
        return updated;
    }

    private ConversationContext loadContextBeforeMessage(ChatConversationRecord conversation, Long userId, int currentSeqNo) {
        ConversationMetaCache metaCache = redisCache.getConversationMeta(conversation.id());
        String summary = metaCache == null || metaCache.summary() == null ? conversation.summary() : metaCache.summary();

        List<ChatMessageRecord> recentMessages = redisCache.getRecentMessages(conversation.id());
        if (recentMessages.isEmpty()) {
            recentMessages = messageRepository.listRecentByConversationId(conversation.id(), userId, properties.getRecentMessageLimit());
            redisCache.replaceRecentMessages(conversation.id(), recentMessages);
        }

        List<ChatMessageRecord> filteredMessages = recentMessages.stream()
                .filter(message -> message.seqNo() < currentSeqNo)
                .sorted(Comparator.comparing(ChatMessageRecord::seqNo))
                .toList();

        if (filteredMessages.isEmpty() && currentSeqNo > 1) {
            filteredMessages = messageRepository.listRecentBeforeSeq(
                    conversation.id(),
                    userId,
                    currentSeqNo,
                    properties.getRecentMessageLimit()
            );
        }

        int promptMessageLimit = properties.getPromptRoundLimit() * 2;
        if (filteredMessages.size() > promptMessageLimit) {
            filteredMessages = filteredMessages.subList(filteredMessages.size() - promptMessageLimit, filteredMessages.size());
        }

        return new ConversationContext(summary, filteredMessages);
    }

    private List<Message> buildPromptMessages(ConversationContext context, String currentMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage("""
                You are a helpful AI assistant.
                Use the conversation summary and recent messages when they are useful.
                Keep answers grounded in the current conversation.
                """));

        if (context.summary() != null && !context.summary().isBlank()) {
            messages.add(new SystemMessage("Conversation summary:\n" + context.summary()));
        }

        for (ChatMessageRecord recentMessage : context.recentMessages()) {
            if (ROLE_ASSISTANT.equalsIgnoreCase(recentMessage.role())) {
                messages.add(new AssistantMessage(recentMessage.content()));
            } else {
                messages.add(new UserMessage(recentMessage.content()));
            }
        }

        messages.add(new UserMessage(currentMessage));
        return messages;
    }

    private String maybeRefreshSummary(
            ChatConversationRecord conversation,
            Long userId,
            int messageCount,
            String provider,
            String model
    ) {
        if (messageCount <= properties.getSummaryTriggerInitialCount()) {
            return conversation.summary();
        }
        int delta = messageCount - properties.getSummaryTriggerInitialCount();
        if (delta % properties.getSummaryTriggerStep() != 0) {
            return conversation.summary();
        }

        List<ChatMessageRecord> messages = messageRepository.listByConversationId(conversation.id(), userId);
        StringBuilder transcript = new StringBuilder();
        for (ChatMessageRecord message : messages) {
            transcript.append(message.role()).append(": ").append(message.content()).append('\n');
        }

        List<Message> summaryPrompt = List.of(
                new SystemMessage("""
                        Summarize the conversation into a compact memory note.
                        Keep only stable facts, constraints, goals, preferences, and confirmed conclusions.
                        Use concise Chinese within 200 characters.
                        """),
                new UserMessage("""
                        Existing summary:
                        %s

                        Conversation:
                        %s
                        """.formatted(conversation.summary() == null ? "" : conversation.summary(), transcript))
        );

        try {
            return chatGatewayService.call(summaryPrompt, provider, model);
        } catch (Exception ex) {
            return fallbackSummary(messages, conversation.summary());
        }
    }

    private String fallbackSummary(List<ChatMessageRecord> messages, String existingSummary) {
        if (existingSummary != null && !existingSummary.isBlank()) {
            return existingSummary;
        }
        return messages.stream()
                .filter(message -> ROLE_USER.equalsIgnoreCase(message.role()))
                .limit(3)
                .map(ChatMessageRecord::content)
                .reduce((left, right) -> left + " | " + right)
                .map(summary -> summary.length() > 200 ? summary.substring(0, 200) : summary)
                .orElse("");
    }

    private ChatConversationRecord getConversation(Long userId, Long conversationId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException(404, "Conversation not found"));
    }

    private String resolveTitle(String currentTitle, String firstUserMessage) {
        if (currentTitle != null && !currentTitle.isBlank() && !DEFAULT_TITLE.equals(currentTitle)) {
            return currentTitle;
        }
        String trimmed = firstUserMessage == null ? "" : firstUserMessage.trim();
        if (trimmed.isBlank()) {
            return DEFAULT_TITLE;
        }
        return trimmed.length() > 20 ? trimmed.substring(0, 20) : trimmed;
    }

    private void ensureLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "Please login before using chat memory");
        }
    }

    private String normalizeProvider(String provider, String fallback) {
        String candidate = provider == null || provider.isBlank() ? fallback : provider;
        if (candidate == null || candidate.isBlank()) {
            return "openai";
        }
        return candidate.toLowerCase().contains("qwen") ? "dashscope" : candidate.toLowerCase();
    }

    private String normalizeModel(String provider, String model, String fallback) {
        String candidate = model == null || model.isBlank() ? fallback : model;
        if (candidate != null && !candidate.isBlank()) {
            return candidate;
        }
        if ("dashscope".equalsIgnoreCase(provider)) {
            return "qwen3.5-35b-a3b";
        }
        return "gpt-5.3-codex-spark";
    }
}

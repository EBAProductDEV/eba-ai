package com.qctv1.ai.chat.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qctv1.ai.chat.domain.PreparedConversation;
import com.qctv1.ai.chat.dto.ChatConversationCreateRequest;
import com.qctv1.ai.chat.dto.ChatStreamRequest;
import com.qctv1.ai.chat.service.ChatConversationService;
import com.qctv1.ai.chat.service.ChatGatewayService;
import com.qctv1.ai.chat.support.ApiResponse;
import com.qctv1.ai.chat.support.ChatRequestUserResolver;
import com.qctv1.ai.chat.vo.ChatConversationCreateVo;
import com.qctv1.ai.chat.vo.ChatConversationDetailVo;
import com.qctv1.ai.chat.vo.ChatConversationSummaryVo;
import com.qctv1.ai.chat.vo.ChatErrorEvent;
import com.qctv1.ai.chat.vo.ChatSimpleResponseVo;
import com.qctv1.ai.chat.vo.ChatStreamMetaEvent;
import com.qctv1.iam.api.header.IamUserHeaders;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatGatewayService chatGatewayService;
    private final ChatConversationService chatConversationService;
    private final ObjectMapper objectMapper;
    private final ChatRequestUserResolver chatRequestUserResolver;

    public ChatController(
            ChatGatewayService chatGatewayService,
            ChatConversationService chatConversationService,
            ObjectMapper objectMapper,
            ChatRequestUserResolver chatRequestUserResolver
    ) {
        this.chatGatewayService = chatGatewayService;
        this.chatConversationService = chatConversationService;
        this.objectMapper = objectMapper;
        this.chatRequestUserResolver = chatRequestUserResolver;
    }

    @GetMapping("/prompt/template")
    public Flux<String> promptTemplate(
            @RequestParam String topic,
            @RequestParam String count,
            @RequestParam String format
    ) {
        PromptTemplate promptTemplate = new PromptTemplate(
                "Please write a story about {topic}, keep it around {count} words, and return it in {format} format."
        );
        Message message = promptTemplate.createMessage(Map.of(
                "topic", topic,
                "count", count,
                "format", format
        ));
        Prompt prompt = new Prompt(List.of(message));
        return chatGatewayService.stream(prompt.getInstructions(), "openai", "gpt-5.3-codex-spark");
    }

    @PostMapping("/conversations")
    public Mono<ApiResponse<ChatConversationCreateVo>> createConversation(
            @RequestBody(required = false) ChatConversationCreateRequest request,
            @RequestHeader(value = IamUserHeaders.USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = resolveUserId(userIdHeader, authorizationHeader);
        return Mono.fromCallable(() -> ApiResponse.success(chatConversationService.createConversation(userId, request)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/conversations")
    public Mono<ApiResponse<List<ChatConversationSummaryVo>>> listConversations(
            @RequestHeader(value = IamUserHeaders.USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = resolveUserId(userIdHeader, authorizationHeader);
        return Mono.fromCallable(() -> ApiResponse.success(chatConversationService.listConversations(userId)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/conversations/{id}")
    public Mono<ApiResponse<ChatConversationDetailVo>> getConversation(
            @PathVariable("id") Long conversationId,
            @RequestHeader(value = IamUserHeaders.USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = resolveUserId(userIdHeader, authorizationHeader);
        return Mono.fromCallable(() -> ApiResponse.success(chatConversationService.getConversationDetail(userId, conversationId)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(
            @Valid @RequestBody ChatStreamRequest request,
            @RequestHeader(value = IamUserHeaders.USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = resolveUserId(userIdHeader, authorizationHeader);
        return Mono.fromCallable(() -> chatConversationService.prepareConversation(userId, request))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(preparedConversation -> {
                    StringBuilder assistantContent = new StringBuilder();
                    AtomicBoolean persisted = new AtomicBoolean(false);

                    Flux<String> modelFlux = chatGatewayService.stream(
                                    preparedConversation.promptMessages(),
                                    preparedConversation.provider(),
                                    preparedConversation.model()
                            )
                            .timeout(Duration.ofSeconds(180))
                            .doOnNext(assistantContent::append)
                            .doOnComplete(() -> persistConversation(userId, preparedConversation, assistantContent, true, persisted))
                            .onErrorResume(error -> {
                                persistConversation(userId, preparedConversation, assistantContent, false, persisted);
                                return Flux.just(toJson(new ChatErrorEvent("error", error.getMessage() == null ? "AI service error" : error.getMessage())));
                            })
                            .doFinally(signalType -> {
                                if (signalType == SignalType.CANCEL) {
                                    persistConversation(userId, preparedConversation, assistantContent, false, persisted);
                                }
                            });

                    return Flux.concat(
                            Flux.just(toJson(new ChatStreamMetaEvent(
                                    "meta",
                                    preparedConversation.conversation().id(),
                                    preparedConversation.conversation().title()
                            ))),
                            modelFlux
                    );
                });
    }

    @PostMapping("/simple")
    public Mono<ApiResponse<ChatSimpleResponseVo>> simple(
            @Valid @RequestBody ChatStreamRequest request,
            @RequestHeader(value = IamUserHeaders.USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader
    ) {
        Long userId = resolveUserId(userIdHeader, authorizationHeader);
        return Mono.fromCallable(() -> {
                    PreparedConversation preparedConversation = chatConversationService.prepareConversation(userId, request);
                    try {
                        String content = chatGatewayService.call(
                                preparedConversation.promptMessages(),
                                preparedConversation.provider(),
                                preparedConversation.model()
                        );
                        var conversation = chatConversationService.finalizeConversation(
                                userId,
                                preparedConversation,
                                content,
                                true
                        );
                        return ApiResponse.success(new ChatSimpleResponseVo(
                                conversation.id(),
                                conversation.title(),
                                content
                        ));
                    } catch (Exception ex) {
                        var conversation = chatConversationService.finalizeConversation(
                                userId,
                                preparedConversation,
                                ex.getMessage() == null ? "[error] AI service error" : "[error] " + ex.getMessage(),
                                false
                        );
                        return ApiResponse.success(new ChatSimpleResponseVo(
                                conversation.id(),
                                conversation.title(),
                                ex.getMessage() == null ? "[error] AI service error" : "[error] " + ex.getMessage()
                        ));
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> legacyStream(
            @RequestParam String query,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model
    ) {
        return chatGatewayService.stream(List.of(new UserMessage(query)), provider, model)
                .timeout(Duration.ofSeconds(60));
    }

    @GetMapping("/simple")
    public Mono<String> legacySimple(
            @RequestParam String query,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String model
    ) {
        return Mono.fromCallable(() -> chatGatewayService.call(List.of(new UserMessage(query)), provider, model))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void persistConversation(
            Long userId,
            PreparedConversation preparedConversation,
            StringBuilder assistantContent,
            boolean success,
            AtomicBoolean persisted
    ) {
        if (persisted.compareAndSet(false, true)) {
            Mono.fromCallable(() -> chatConversationService.finalizeConversation(
                            userId,
                            preparedConversation,
                            assistantContent.toString(),
                            success
                    ))
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnError(error -> log.warn(
                            "Failed to persist chat conversation, conversationId={}",
                            preparedConversation.conversation().id(),
                            error
                    ))
                    .subscribe();
        }
    }

    private Long resolveUserId(String userIdHeader, String authorizationHeader) {
        return chatRequestUserResolver.resolveUserId(userIdHeader, authorizationHeader);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"type\":\"error\",\"content\":\"serialization failed\"}";
        }
    }
}

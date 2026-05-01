package com.qctv1.ai.drama.config;

import com.qctv1.ai.drama.websocket.DramaTaskWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableScheduling
public class DramaTaskWebSocketConfig implements WebSocketConfigurer {

    private final DramaTaskWebSocketHandler taskWebSocketHandler;

    public DramaTaskWebSocketConfig(DramaTaskWebSocketHandler taskWebSocketHandler) {
        this.taskWebSocketHandler = taskWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(taskWebSocketHandler, "/drama/ws/tasks")
                .setAllowedOriginPatterns("*");
    }
}

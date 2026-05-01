package com.qctv1.ai.drama.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qctv1.ai.drama.vo.DramaTaskCenterVo;
import com.qctv1.common.websocket.JsonWebSocketSessionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DramaTaskWebSocketBeanConfig {

    @Bean
    public JsonWebSocketSessionManager<DramaTaskCenterVo> dramaTaskWebSocketSessionManager(ObjectMapper objectMapper) {
        return new JsonWebSocketSessionManager<>(objectMapper, "短剧任务中心 WebSocket");
    }
}

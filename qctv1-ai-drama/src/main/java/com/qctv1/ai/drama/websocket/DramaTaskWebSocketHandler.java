package com.qctv1.ai.drama.websocket;

import com.qctv1.ai.drama.service.DramaTaskCenterService;
import com.qctv1.ai.drama.vo.DramaTaskCenterVo;
import com.qctv1.common.websocket.JsonWebSocketSessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class DramaTaskWebSocketHandler extends TextWebSocketHandler {

    private final DramaTaskCenterService taskCenterService;
    private final JsonWebSocketSessionManager<DramaTaskCenterVo> sessionManager;

    public DramaTaskWebSocketHandler(
            DramaTaskCenterService taskCenterService,
            JsonWebSocketSessionManager<DramaTaskCenterVo> sessionManager
    ) {
        this.taskCenterService = taskCenterService;
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.add(session);
        sessionManager.send(session, "TASK_CENTER_SNAPSHOT", taskCenterService.listTasks(80));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if ("refresh".equalsIgnoreCase(message.getPayload())) {
            sessionManager.send(session, "TASK_CENTER_SNAPSHOT", taskCenterService.listTasks(80));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessionManager.remove(session);
    }
}

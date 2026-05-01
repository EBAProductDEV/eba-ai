package com.qctv1.ai.drama.websocket;

import com.qctv1.ai.drama.service.DramaTaskCenterService;
import com.qctv1.ai.drama.vo.DramaTaskCenterVo;
import com.qctv1.common.websocket.JsonWebSocketSessionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DramaTaskPushScheduler {

    private final DramaTaskCenterService taskCenterService;
    private final JsonWebSocketSessionManager<DramaTaskCenterVo> sessionManager;

    public DramaTaskPushScheduler(
            DramaTaskCenterService taskCenterService,
            JsonWebSocketSessionManager<DramaTaskCenterVo> sessionManager
    ) {
        this.taskCenterService = taskCenterService;
        this.sessionManager = sessionManager;
    }

    @Scheduled(fixedDelayString = "${app.ai.drama.task-center.push-interval-ms:2000}")
    public void pushTaskCenterSnapshot() {
        if (!sessionManager.hasSessions()) {
            return;
        }
        sessionManager.broadcastIfChanged("TASK_CENTER_SNAPSHOT", taskCenterService.listTasks(80));
    }
}

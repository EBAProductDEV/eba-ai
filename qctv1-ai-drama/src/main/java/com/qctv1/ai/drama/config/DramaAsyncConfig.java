package com.qctv1.ai.drama.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class DramaAsyncConfig {

    @Bean("dramaImageTaskExecutor")
    public TaskExecutor dramaImageTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("drama-image-");
        // 图片任务允许适度并发，便于批量生成角色图、场景图和镜头首帧图。
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }

    @Bean("dramaVideoTaskExecutor")
    public TaskExecutor dramaVideoTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("drama-video-");
        // 视频生成耗时长、成本高，默认降低并发，避免一次性提交过多镜头任务。
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}

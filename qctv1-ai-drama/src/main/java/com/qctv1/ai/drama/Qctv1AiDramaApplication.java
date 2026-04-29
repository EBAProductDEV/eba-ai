package com.qctv1.ai.drama;

import com.qctv1.ai.drama.config.DramaProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DramaProperties.class)
@MapperScan("com.qctv1.ai.drama.mapper")
public class Qctv1AiDramaApplication {

    public static void main(String[] args) {
        SpringApplication.run(Qctv1AiDramaApplication.class, args);
    }
}

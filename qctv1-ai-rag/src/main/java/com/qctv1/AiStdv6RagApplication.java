package com.qctv1;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.qctv1.rag.mapper")
@EnableFeignClients(basePackageClasses = com.qctv1.iam.client.IamUserFeignClient.class)
public class AiStdv6RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiStdv6RagApplication.class, args);
    }
}

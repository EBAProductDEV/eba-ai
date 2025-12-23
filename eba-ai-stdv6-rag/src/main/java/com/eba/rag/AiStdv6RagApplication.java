package com.eba.rag;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("com.eba.rag.mapper")
@ComponentScan(basePackages = {"com.eba.rag", "com.eba.agent"})
public class AiStdv6RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiStdv6RagApplication.class, args);
    }

}

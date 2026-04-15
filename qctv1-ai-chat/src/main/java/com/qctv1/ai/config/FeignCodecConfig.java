package com.qctv1.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.nio.charset.StandardCharsets;

@Configuration
public class FeignCodecConfig {

    @Bean
    public HttpMessageConverters httpMessageConverters(ObjectMapper objectMapper) {
        HttpMessageConverter<?> jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        HttpMessageConverter<?> stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        HttpMessageConverter<?> byteArrayConverter = new ByteArrayHttpMessageConverter();
        return new HttpMessageConverters(jsonConverter, stringConverter, byteArrayConverter);
    }
}

package com.qctv1.ai.drama.dto;

public record GenerateRequest(
        String instruction,
        Integer count
) {
}

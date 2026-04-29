package com.qctv1.ai.drama.dto;

import jakarta.validation.constraints.NotBlank;

public record DramaCharacterSaveRequest(
        @NotBlank(message = "角色名称不能为空") String name,
        String profile,
        String appearance,
        String costume,
        String personality,
        String relationship
) {
}
package com.qctv1.ai.drama.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DramaSeriesCreateRequest(
        @NotBlank(message = "项目名称不能为空") String name,
        @NotBlank(message = "类型不能为空") String type,
        String intro,
        String theme,
        String style,
        @NotNull(message = "总集数不能为空") @Min(1) @Max(500) Integer totalEpisodes,
        @NotNull(message = "单集时长不能为空") @Min(1) @Max(180) Integer episodeDurationMinutes
) {
}

package com.qctv1.ai.drama.controller;

import com.qctv1.ai.drama.dto.DramaCharacterSaveRequest;
import com.qctv1.ai.drama.service.DramaSeriesService;
import com.qctv1.ai.drama.support.ApiResponse;
import com.qctv1.ai.drama.vo.DramaCharacterVo;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/drama/series/{seriesId}/characters")
public class DramaCharacterController {

    private final DramaSeriesService seriesService;

    public DramaCharacterController(DramaSeriesService seriesService) {
        this.seriesService = seriesService;
    }

    @GetMapping
    public ApiResponse<List<DramaCharacterVo>> list(@PathVariable Long seriesId) {
        return ApiResponse.success(seriesService.listCharacters(seriesId));
    }

    @PostMapping
    public ApiResponse<DramaCharacterVo> create(@PathVariable Long seriesId, @Valid @RequestBody DramaCharacterSaveRequest request) {
        return ApiResponse.success(seriesService.createCharacter(seriesId, request));
    }

    @DeleteMapping("/{characterId}")
    public ApiResponse<Void> delete(@PathVariable Long seriesId, @PathVariable Long characterId) {
        seriesService.deleteCharacter(seriesId, characterId);
        return ApiResponse.success(null);
    }
}
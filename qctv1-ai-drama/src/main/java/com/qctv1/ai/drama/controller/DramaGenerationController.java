package com.qctv1.ai.drama.controller;

import com.qctv1.ai.drama.dto.GenerateRequest;
import com.qctv1.ai.drama.service.DramaGenerationService;
import com.qctv1.ai.drama.service.DramaSeriesService;
import com.qctv1.ai.drama.support.ApiResponse;
import com.qctv1.ai.drama.vo.DramaEpisodeDetailVo;
import com.qctv1.ai.drama.vo.DramaTaskVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/drama")
public class DramaGenerationController {

    private final DramaGenerationService generationService;
    private final DramaSeriesService seriesService;

    public DramaGenerationController(DramaGenerationService generationService, DramaSeriesService seriesService) {
        this.generationService = generationService;
        this.seriesService = seriesService;
    }

    @PostMapping("/series/{seriesId}/story/generate")
    public ApiResponse<DramaTaskVo> generateStory(@PathVariable Long seriesId, @RequestBody(required = false) GenerateRequest request) {
        return ApiResponse.success(generationService.generateStory(seriesId, request == null ? new GenerateRequest(null, null) : request));
    }

    @PostMapping("/series/{seriesId}/episodes/generate")
    public ApiResponse<DramaTaskVo> generateEpisodes(@PathVariable Long seriesId, @RequestBody(required = false) GenerateRequest request) {
        return ApiResponse.success(generationService.generateEpisodes(seriesId, request == null ? new GenerateRequest(null, null) : request));
    }

    @GetMapping("/episodes/{episodeId}")
    public ApiResponse<DramaEpisodeDetailVo> episodeDetail(@PathVariable Long episodeId) {
        return ApiResponse.success(seriesService.episodeDetail(episodeId));
    }

    @PostMapping("/episodes/{episodeId}/script/generate")
    public ApiResponse<DramaTaskVo> generateScript(@PathVariable Long episodeId, @RequestBody(required = false) GenerateRequest request) {
        return ApiResponse.success(generationService.generateScript(episodeId, request == null ? new GenerateRequest(null, null) : request));
    }

    @PostMapping("/episodes/{episodeId}/shots/generate")
    public ApiResponse<DramaTaskVo> generateShots(@PathVariable Long episodeId, @RequestBody(required = false) GenerateRequest request) {
        return ApiResponse.success(generationService.generateShots(episodeId, request == null ? new GenerateRequest(null, null) : request));
    }

    @PostMapping("/characters/{characterId}/image/generate")
    public ApiResponse<DramaTaskVo> generateCharacterImage(@PathVariable Long characterId, @RequestBody(required = false) GenerateRequest request) {
        return ApiResponse.success(generationService.generateCharacterImage(characterId, request == null ? new GenerateRequest(null, null) : request));
    }

    @PostMapping("/scenes/{sceneId}/image/generate")
    public ApiResponse<DramaTaskVo> generateSceneImage(@PathVariable Long sceneId, @RequestBody(required = false) GenerateRequest request) {
        return ApiResponse.success(generationService.generateSceneImage(sceneId, request == null ? new GenerateRequest(null, null) : request));
    }

    @PostMapping("/shots/{shotId}/image/generate")
    public ApiResponse<DramaTaskVo> generateShotImage(@PathVariable Long shotId, @RequestBody(required = false) GenerateRequest request) {
        return ApiResponse.success(generationService.generateShotImage(shotId, request == null ? new GenerateRequest(null, null) : request));
    }

    @PostMapping("/shots/{shotId}/video/generate")
    public ApiResponse<DramaTaskVo> generateShotVideo(@PathVariable Long shotId, @RequestBody(required = false) GenerateRequest request) {
        return ApiResponse.success(generationService.generateShotVideo(shotId, request == null ? new GenerateRequest(null, null) : request));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<DramaTaskVo> task(@PathVariable Long taskId) {
        return ApiResponse.success(generationService.task(taskId));
    }
}
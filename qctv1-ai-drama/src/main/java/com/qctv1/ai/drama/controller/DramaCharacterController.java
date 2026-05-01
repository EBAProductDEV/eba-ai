package com.qctv1.ai.drama.controller;

import com.qctv1.ai.drama.dto.DramaCharacterImageGenerateRequest;
import com.qctv1.ai.drama.dto.DramaCharacterSaveRequest;
import com.qctv1.ai.drama.service.DramaSeriesService;
import com.qctv1.ai.drama.support.ApiResponse;
import com.qctv1.ai.drama.vo.DramaAssetVo;
import com.qctv1.ai.drama.vo.DramaCharacterVo;
import com.qctv1.ai.drama.vo.DramaTaskVo;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @GetMapping("/{characterId}")
    public ApiResponse<DramaCharacterVo> detail(@PathVariable Long seriesId, @PathVariable Long characterId) {
        return ApiResponse.success(seriesService.characterDetail(seriesId, characterId));
    }

    @GetMapping("/{characterId}/assets")
    public ApiResponse<List<DramaAssetVo>> listAssets(@PathVariable Long seriesId, @PathVariable Long characterId) {
        return ApiResponse.success(seriesService.listCharacterAssets(seriesId, characterId));
    }

    @PostMapping
    public ApiResponse<DramaCharacterVo> create(@PathVariable Long seriesId, @Valid @RequestBody DramaCharacterSaveRequest request) {
        return ApiResponse.success(seriesService.createCharacter(seriesId, request));
    }

    @PutMapping("/{characterId}")
    public ApiResponse<DramaCharacterVo> update(
            @PathVariable Long seriesId,
            @PathVariable Long characterId,
            @Valid @RequestBody DramaCharacterSaveRequest request
    ) {
        return ApiResponse.success(seriesService.updateCharacter(seriesId, characterId, request));
    }

    @PostMapping("/generate")
    public ApiResponse<List<DramaCharacterVo>> generate(@PathVariable Long seriesId) {
        return ApiResponse.success(seriesService.generateCharacters(seriesId));
    }

    @PostMapping("/{characterId}/images/generate")
    public ApiResponse<DramaTaskVo> generateImage(
            @PathVariable Long seriesId,
            @PathVariable Long characterId,
            @RequestBody DramaCharacterImageGenerateRequest request
    ) {
        return ApiResponse.success(seriesService.generateCharacterImage(seriesId, characterId, request));
    }

    @PostMapping("/{characterId}/images/auxiliary/generate")
    public ApiResponse<List<DramaTaskVo>> generateAuxiliaryImages(
            @PathVariable Long seriesId,
            @PathVariable Long characterId
    ) {
        return ApiResponse.success(seriesService.generateCharacterAuxiliaryImages(seriesId, characterId));
    }

    @DeleteMapping("/{characterId}/assets/{assetId}")
    public ApiResponse<Void> deleteAsset(
            @PathVariable Long seriesId,
            @PathVariable Long characterId,
            @PathVariable Long assetId
    ) {
        seriesService.deleteCharacterAsset(seriesId, characterId, assetId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{characterId}")
    public ApiResponse<Void> delete(@PathVariable Long seriesId, @PathVariable Long characterId) {
        seriesService.deleteCharacter(seriesId, characterId);
        return ApiResponse.success(null);
    }
}

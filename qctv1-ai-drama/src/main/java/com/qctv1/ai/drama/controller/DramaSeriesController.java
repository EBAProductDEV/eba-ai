package com.qctv1.ai.drama.controller;

import com.qctv1.ai.drama.dto.DramaSeriesCreateRequest;
import com.qctv1.ai.drama.dto.DramaStorySaveRequest;
import com.qctv1.ai.drama.service.DramaSeriesService;
import com.qctv1.ai.drama.support.ApiResponse;
import com.qctv1.ai.drama.vo.DramaSeriesDetailVo;
import com.qctv1.ai.drama.vo.DramaSeriesSummaryVo;
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
@RequestMapping("/drama/series")
public class DramaSeriesController {

    private final DramaSeriesService seriesService;

    public DramaSeriesController(DramaSeriesService seriesService) {
        this.seriesService = seriesService;
    }

    @PostMapping
    public ApiResponse<DramaSeriesSummaryVo> create(@Valid @RequestBody DramaSeriesCreateRequest request) {
        return ApiResponse.success(seriesService.create(request));
    }

    @GetMapping
    public ApiResponse<List<DramaSeriesSummaryVo>> list() {
        return ApiResponse.success(seriesService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<DramaSeriesDetailVo> detail(@PathVariable Long id) {
        return ApiResponse.success(seriesService.detail(id));
    }

    @PutMapping("/{id}/story")
    public ApiResponse<DramaSeriesDetailVo> saveStory(@PathVariable Long id, @RequestBody DramaStorySaveRequest request) {
        return ApiResponse.success(seriesService.saveStory(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        seriesService.delete(id);
        return ApiResponse.success(null);
    }
}

package com.qctv1.ai.drama.controller;

import com.qctv1.ai.drama.service.DramaAssetService;
import com.qctv1.ai.drama.support.ApiResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/drama/assets")
public class DramaAssetController {

    private final DramaAssetService assetService;

    public DramaAssetController(DramaAssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping("/{assetId}/content")
    public ResponseEntity<Resource> content(@PathVariable Long assetId) {
        Resource resource = assetService.loadAssetContent(assetId);
        MediaType mediaType = assetService.detectMediaType(assetId);
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    @DeleteMapping("/{assetId}")
    public ApiResponse<Void> delete(@PathVariable Long assetId) {
        assetService.deleteAsset(assetId);
        return ApiResponse.success(null);
    }
}

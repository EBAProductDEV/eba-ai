package com.qctv1.ai.drama.controller;

import com.qctv1.ai.drama.support.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/drama")
public class DramaKnowledgeController {

    @PostMapping("/series/{seriesId}/knowledge/documents")
    public ApiResponse<String> uploadKnowledge(@PathVariable Long seriesId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.success("v1 已预留知识资料上传入口，后续会保存到 D:/AI视频/短剧-" + seriesId + "/知识资料 并写入知识库");
    }

    @PostMapping("/knowledge/documents/{documentId}/index")
    public ApiResponse<String> indexKnowledge(@PathVariable Long documentId) {
        return ApiResponse.success("v1 已预留 Redis Stack 索引入口，documentId=" + documentId);
    }
}

package com.qctv1.ai.drama.controller;

import com.qctv1.ai.drama.dto.DramaStoryAssistantChatRequest;
import com.qctv1.ai.drama.dto.DramaEpisodeScriptSaveRequest;
import com.qctv1.ai.drama.dto.DramaEpisodeStepCompleteRequest;
import com.qctv1.ai.drama.dto.DramaEpisodeStepRollbackRequest;
import com.qctv1.ai.drama.dto.DramaImageGenerateRequest;
import com.qctv1.ai.drama.dto.DramaVideoGenerateRequest;
import com.qctv1.ai.drama.dto.DramaStoryGenerateRequest;
import com.qctv1.ai.drama.dto.DramaStoryBriefRequest;
import com.qctv1.ai.drama.dto.GenerateRequest;
import com.qctv1.ai.drama.service.DramaGenerationService;
import com.qctv1.ai.drama.service.DramaJianyingDraftService;
import com.qctv1.ai.drama.service.DramaSeriesService;
import com.qctv1.ai.drama.service.DramaTaskCenterService;
import com.qctv1.ai.drama.support.ApiResponse;
import com.qctv1.ai.drama.vo.DramaEpisodeDetailVo;
import com.qctv1.ai.drama.vo.DramaImagePromptPreviewVo;
import com.qctv1.ai.drama.vo.DramaJianyingDraftVo;
import com.qctv1.ai.drama.vo.DramaSeriesDetailVo;
import com.qctv1.ai.drama.vo.DramaStoryAssistantChatVo;
import com.qctv1.ai.drama.vo.DramaStoryBriefVo;
import com.qctv1.ai.drama.vo.DramaTaskCenterVo;
import com.qctv1.ai.drama.vo.DramaTaskVo;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/drama")
public class DramaGenerationController {

    private final DramaGenerationService generationService;
    private final DramaSeriesService seriesService;
    private final DramaTaskCenterService taskCenterService;
    private final DramaJianyingDraftService jianyingDraftService;

    public DramaGenerationController(
            DramaGenerationService generationService,
            DramaSeriesService seriesService,
            DramaTaskCenterService taskCenterService,
            DramaJianyingDraftService jianyingDraftService
    ) {
        this.generationService = generationService;
        this.seriesService = seriesService;
        this.taskCenterService = taskCenterService;
        this.jianyingDraftService = jianyingDraftService;
    }

    @PostMapping("/series/{seriesId}/story/generate")
    public ApiResponse<DramaTaskVo> generateStory(@PathVariable Long seriesId, @RequestBody(required = false) GenerateRequest request) {
        return ApiResponse.success(generationService.generateStory(seriesId, request == null ? new GenerateRequest(null, null) : request));
    }

    @PostMapping("/series/{seriesId}/story/brief")
    public ApiResponse<DramaStoryBriefVo> prepareStoryBrief(@PathVariable Long seriesId, @RequestBody(required = false) DramaStoryBriefRequest request) {
        return ApiResponse.success(generationService.prepareStoryBrief(seriesId, request == null ? new DramaStoryBriefRequest(null) : request));
    }

    @PostMapping("/series/{seriesId}/story/generate-content")
    public ApiResponse<DramaSeriesDetailVo> generateStoryContent(@PathVariable Long seriesId, @RequestBody(required = false) DramaStoryGenerateRequest request) {
        return ApiResponse.success(generationService.generateStoryContent(seriesId, request == null ? new DramaStoryGenerateRequest(null, null) : request));
    }

    @PostMapping("/series/{seriesId}/story/assistant/chat")
    public ApiResponse<DramaStoryAssistantChatVo> chatWithStoryAssistant(@PathVariable Long seriesId, @RequestBody(required = false) DramaStoryAssistantChatRequest request) {
        return ApiResponse.success(generationService.chatWithStoryAssistant(seriesId, request));
    }

    @PostMapping(value = "/series/{seriesId}/story/assistant/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChatWithStoryAssistant(@PathVariable Long seriesId, @RequestBody(required = false) DramaStoryAssistantChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        CompletableFuture.runAsync(() -> {
            try {
                DramaStoryAssistantChatVo result = generationService.chatWithStoryAssistant(seriesId, request);
                streamAnswer(emitter, result.answer());
                emitter.send(SseEmitter.event().name("result").data(result));
                emitter.send(SseEmitter.event().name("done").data(Map.of("done", true)));
                emitter.complete();
            } catch (Exception ex) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of("message", ex.getMessage() == null ? "AI 助手调用失败" : ex.getMessage())));
                } catch (IOException ignored) {
                    // 客户端已断开时无需再处理。
                }
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    private void streamAnswer(SseEmitter emitter, String answer) throws IOException {
        String text = answer == null ? "" : answer;
        int chunkSize = 24;
        for (int start = 0; start < text.length(); start += chunkSize) {
            int end = Math.min(start + chunkSize, text.length());
            emitter.send(SseEmitter.event().name("chunk").data(text.substring(start, end)));
        }
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

    @PostMapping("/episodes/{episodeId}/novel/generate")
    public ApiResponse<DramaEpisodeDetailVo> generateNovelContent(@PathVariable Long episodeId, @RequestBody(required = false) GenerateRequest request) {
        return ApiResponse.success(generationService.generateNovelContent(episodeId, request == null ? new GenerateRequest(null, null) : request));
    }

    @PutMapping("/episodes/{episodeId}/script")
    public ApiResponse<DramaEpisodeDetailVo> saveScript(@PathVariable Long episodeId, @RequestBody(required = false) DramaEpisodeScriptSaveRequest request) {
        return ApiResponse.success(generationService.saveScript(episodeId, request));
    }

    @PostMapping("/episodes/{episodeId}/scenes/generate")
    public ApiResponse<DramaTaskVo> generateScenes(@PathVariable Long episodeId, @RequestBody(required = false) GenerateRequest request) {
        return ApiResponse.success(generationService.generateScenes(episodeId, request == null ? new GenerateRequest(null, null) : request));
    }

    @PutMapping("/episodes/{episodeId}/workflow/complete")
    public ApiResponse<DramaEpisodeDetailVo> completeEpisodeStep(@PathVariable Long episodeId, @RequestBody(required = false) DramaEpisodeStepCompleteRequest request) {
        return ApiResponse.success(generationService.completeEpisodeStep(episodeId, request));
    }

    @PutMapping("/episodes/{episodeId}/workflow/rollback")
    public ApiResponse<DramaEpisodeDetailVo> rollbackEpisodeStep(@PathVariable Long episodeId, @RequestBody(required = false) DramaEpisodeStepRollbackRequest request) {
        return ApiResponse.success(generationService.rollbackEpisodeStep(episodeId, request));
    }

    @PostMapping("/episodes/{episodeId}/shots/generate")
    public ApiResponse<DramaTaskVo> generateShots(@PathVariable Long episodeId, @RequestBody(required = false) GenerateRequest request) {
        return ApiResponse.success(generationService.generateShots(episodeId, request == null ? new GenerateRequest(null, null) : request));
    }

    @PostMapping("/episodes/{episodeId}/dialogues/generate")
    public ApiResponse<DramaTaskVo> generateDialogues(@PathVariable Long episodeId, @RequestBody(required = false) GenerateRequest request) {
        return ApiResponse.success(generationService.generateDialogues(episodeId, request == null ? new GenerateRequest(null, null) : request));
    }

    @PostMapping("/episodes/{episodeId}/scene-images/generate")
    public ApiResponse<List<DramaTaskVo>> generateEpisodeSceneImages(@PathVariable Long episodeId) {
        return ApiResponse.success(generationService.generateEpisodeSceneImages(episodeId));
    }

    @PostMapping("/episodes/{episodeId}/shot-images/generate")
    public ApiResponse<List<DramaTaskVo>> generateEpisodeShotImages(@PathVariable Long episodeId) {
        return ApiResponse.success(generationService.generateEpisodeShotImages(episodeId));
    }

    @PostMapping("/episodes/{episodeId}/shot-videos/generate")
    public ApiResponse<List<DramaTaskVo>> generateEpisodeShotVideos(@PathVariable Long episodeId) {
        return ApiResponse.success(generationService.generateEpisodeShotVideos(episodeId));
    }

    @PostMapping("/episodes/{episodeId}/jianying-draft/generate")
    public ApiResponse<DramaTaskVo> generateJianyingDraft(@PathVariable Long episodeId) {
        return ApiResponse.success(jianyingDraftService.submit(episodeId));
    }

    @GetMapping("/episodes/{episodeId}/jianying-draft/latest")
    public ApiResponse<DramaJianyingDraftVo> latestJianyingDraft(@PathVariable Long episodeId) {
        return ApiResponse.success(jianyingDraftService.latestPackage(episodeId).orElse(null));
    }

    @GetMapping("/jianying/packages/{packageId}/download")
    public ResponseEntity<Resource> downloadJianyingPackage(@PathVariable Long packageId) {
        Path path = jianyingDraftService.packagePath(packageId);
        return fileResponse(path, MediaType.APPLICATION_OCTET_STREAM, true);
    }

    @GetMapping("/jianying/packages/{packageId}/reference")
    public ResponseEntity<Resource> referenceJianyingVideo(@PathVariable Long packageId) {
        Path reference = jianyingDraftService.referenceVideoPath(packageId);
        return fileResponse(reference, MediaType.parseMediaType("video/mp4"), false);
    }

    @PostMapping("/characters/{characterId}/image/generate")
    public ApiResponse<DramaTaskVo> generateCharacterImage(@PathVariable Long characterId, @RequestBody(required = false) GenerateRequest request) {
        return ApiResponse.success(generationService.generateCharacterImage(characterId, request == null ? new GenerateRequest(null, null) : request));
    }

    @PostMapping("/scenes/{sceneId}/image/generate")
    public ApiResponse<DramaTaskVo> generateSceneImage(@PathVariable Long sceneId, @RequestBody(required = false) DramaImageGenerateRequest request) {
        return ApiResponse.success(generationService.generateSceneImage(sceneId, request));
    }

    @PostMapping("/scenes/{sceneId}/image/prompt")
    public ApiResponse<DramaImagePromptPreviewVo> savedSceneImagePrompt(@PathVariable Long sceneId) {
        return ApiResponse.success(generationService.savedSceneImagePrompt(sceneId));
    }

    @PostMapping("/scenes/{sceneId}/image/preview")
    public ApiResponse<DramaImagePromptPreviewVo> previewSceneImagePrompt(@PathVariable Long sceneId, @RequestBody(required = false) DramaImageGenerateRequest request) {
        return ApiResponse.success(generationService.previewSceneImagePrompt(sceneId, request));
    }

    @PostMapping("/shots/{shotId}/image/generate")
    public ApiResponse<DramaTaskVo> generateShotImage(@PathVariable Long shotId, @RequestBody(required = false) DramaImageGenerateRequest request) {
        return ApiResponse.success(generationService.generateShotImage(shotId, request));
    }

    @PostMapping("/shots/{shotId}/image/prompt")
    public ApiResponse<DramaImagePromptPreviewVo> savedShotImagePrompt(@PathVariable Long shotId) {
        return ApiResponse.success(generationService.savedShotImagePrompt(shotId));
    }

    @PostMapping("/shots/{shotId}/image/preview")
    public ApiResponse<DramaImagePromptPreviewVo> previewShotImagePrompt(@PathVariable Long shotId, @RequestBody(required = false) DramaImageGenerateRequest request) {
        return ApiResponse.success(generationService.previewShotImagePrompt(shotId, request));
    }

    @PostMapping("/shots/{shotId}/video/prompt")
    public ApiResponse<DramaImagePromptPreviewVo> savedShotVideoPrompt(@PathVariable Long shotId) {
        return ApiResponse.success(generationService.savedShotVideoPrompt(shotId));
    }

    @PostMapping("/shots/{shotId}/video/prompt/save")
    public ApiResponse<DramaImagePromptPreviewVo> saveShotVideoPrompt(@PathVariable Long shotId, @RequestBody(required = false) DramaVideoGenerateRequest request) {
        return ApiResponse.success(generationService.saveShotVideoPrompt(shotId, request));
    }

    @PostMapping("/shots/{shotId}/video/preview")
    public ApiResponse<DramaImagePromptPreviewVo> previewShotVideoPrompt(@PathVariable Long shotId, @RequestBody(required = false) DramaVideoGenerateRequest request) {
        return ApiResponse.success(generationService.previewShotVideoPrompt(shotId, request));
    }

    @PostMapping("/shots/{shotId}/video/generate")
    public ApiResponse<DramaTaskVo> generateShotVideo(@PathVariable Long shotId, @RequestBody(required = false) DramaVideoGenerateRequest request) {
        return ApiResponse.success(generationService.generateShotVideo(shotId, request));
    }

    @GetMapping("/tasks/{taskId:\\d+}")
    public ApiResponse<DramaTaskVo> task(@PathVariable Long taskId) {
        return ApiResponse.success(generationService.task(taskId));
    }

    @GetMapping("/tasks")
    public ApiResponse<DramaTaskCenterVo> tasks(@RequestParam(defaultValue = "80") Integer limit) {
        return ApiResponse.success(taskCenterService.listTasks(limit == null ? 80 : limit));
    }

    @DeleteMapping("/tasks/{taskId:\\d+}")
    public ApiResponse<DramaTaskCenterVo> cancelTask(@PathVariable Long taskId) {
        return ApiResponse.success(taskCenterService.cancelTask(taskId));
    }

    private ResponseEntity<Resource> fileResponse(Path path, MediaType mediaType, boolean attachment) {
        FileSystemResource resource = new FileSystemResource(path);
        String encodedFileName = URLEncoder.encode(path.getFileName().toString(), StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = attachment ? "attachment" : "inline";
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(path.toFile().length())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename*=UTF-8''" + encodedFileName)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(resource);
    }
}

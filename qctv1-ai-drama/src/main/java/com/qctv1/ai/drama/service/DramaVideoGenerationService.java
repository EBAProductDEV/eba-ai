package com.qctv1.ai.drama.service;

import com.qctv1.ai.drama.config.DramaProperties;
import com.qctv1.ai.drama.domain.DramaAssetRecord;
import com.qctv1.ai.drama.domain.DramaCharacterRecord;
import com.qctv1.ai.drama.domain.DramaEpisodeRecord;
import com.qctv1.ai.drama.domain.DramaSceneRecord;
import com.qctv1.ai.drama.domain.DramaSeriesRecord;
import com.qctv1.ai.drama.domain.DramaShotRecord;
import com.qctv1.ai.drama.domain.DramaTaskRecord;
import com.qctv1.ai.drama.provider.TextGenerationClient;
import com.qctv1.ai.drama.provider.VideoGenerationClient;
import com.qctv1.ai.drama.repository.DramaAssetRepository;
import com.qctv1.ai.drama.repository.DramaCharacterRepository;
import com.qctv1.ai.drama.repository.DramaWorkflowRepository;
import com.qctv1.ai.drama.support.BusinessException;
import com.qctv1.ai.drama.vo.DramaTaskVo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class DramaVideoGenerationService {

    private static final int MAX_QUERY_ATTEMPTS = 180;
    private static final long QUERY_INTERVAL_MILLIS = 8_000L;

    private final DramaProperties properties;
    private final DramaAssetRepository assetRepository;
    private final DramaCharacterRepository characterRepository;
    private final DramaWorkflowRepository workflowRepository;
    private final DramaAssetService assetService;
    private final TextGenerationClient textGenerationClient;
    private final VideoGenerationClient videoGenerationClient;
    private final TaskExecutor videoTaskExecutor;

    public DramaVideoGenerationService(
            DramaProperties properties,
            DramaAssetRepository assetRepository,
            DramaCharacterRepository characterRepository,
            DramaWorkflowRepository workflowRepository,
            DramaAssetService assetService,
            TextGenerationClient textGenerationClient,
            VideoGenerationClient videoGenerationClient,
            @Qualifier("dramaVideoTaskExecutor") TaskExecutor videoTaskExecutor
    ) {
        this.properties = properties;
        this.assetRepository = assetRepository;
        this.characterRepository = characterRepository;
        this.workflowRepository = workflowRepository;
        this.assetService = assetService;
        this.textGenerationClient = textGenerationClient;
        this.videoGenerationClient = videoGenerationClient;
        this.videoTaskExecutor = videoTaskExecutor;
    }

    public DramaTaskVo submit(DramaSeriesRecord series, DramaEpisodeRecord episode, DramaSceneRecord scene, DramaShotRecord shot) {
        DramaAssetRecord firstFrame = findShotFirstFrame(episode.id(), shot.id())
                .orElseThrow(() -> new BusinessException(400, "请先生成该镜头的首帧图，再生成视频"));
        Long taskId = workflowRepository.createTask(
                series.id(),
                episode.id(),
                shot.id(),
                null,
                null,
                "SHOT",
                shot.id(),
                "SHOT_VIDEO",
                "VIDEO_CLIP",
                "SHOT_VIDEO_GENERATE",
                null,
                "PENDING",
                0,
                "PROMPTING",
                "视频任务已提交，正在组装英文视频提示词"
        );
        videoTaskExecutor.execute(() -> runVideoTask(taskId, series, episode, scene, shot, firstFrame));
        return toTaskVo(workflowRepository.findTask(taskId).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "任务不存在")));
    }

    private void runVideoTask(Long taskId, DramaSeriesRecord series, DramaEpisodeRecord episode, DramaSceneRecord scene, DramaShotRecord shot, DramaAssetRecord firstFrame) {
        try {
            workflowRepository.updateTaskProgress(taskId, "RUNNING", 8, "PROMPTING", "视频提示词组装中");
            String prompt = buildVideoPrompt(series, episode, scene, shot);
            Path referenceImage = Path.of(firstFrame.localPath()).toAbsolutePath().normalize();
            assetService.ensureInsideAssetRootForWrite(referenceImage);
            if (!Files.exists(referenceImage) || !Files.isRegularFile(referenceImage)) {
                throw new BusinessException(404, "镜头首帧图文件不存在，无法生成视频");
            }

            workflowRepository.updateTaskProgress(taskId, "RUNNING", 18, "SUBMITTING", "正在提交视频生成任务");
            VideoGenerationClient.VideoTask providerTask = videoGenerationClient.submitVideoTask(new VideoGenerationClient.VideoRequest(
                    prompt,
                    referenceImage,
                    resolveVideoSeconds(shot),
                    resolveVideoRatio(series),
                    "720p"
            ));
            workflowRepository.updateTaskProviderTaskId(taskId, providerTask.providerTaskId());
            workflowRepository.updateTaskProgress(taskId, "RUNNING", 30, "GENERATING", "AI 视频生成中");

            String videoUrl = waitForVideoUrl(taskId, providerTask.providerTaskId());
            workflowRepository.updateTaskProgress(taskId, "RUNNING", 86, "DOWNLOADING", "视频已生成，正在下载 mp4 文件");
            VideoGenerationClient.VideoFile videoFile = videoGenerationClient.downloadVideo(videoUrl);

            workflowRepository.updateTaskProgress(taskId, "RUNNING", 94, "SAVING", "视频已下载，正在保存素材记录");
            Path saveDirectory = resolveShotDirectory(series, episode, shot);
            Files.createDirectories(saveDirectory);
            String fileName = safeFileName("shot-video-" + shot.shotNo() + "-" + shot.id()) + "-" + System.currentTimeMillis() + videoFile.fileExtension();
            Path videoPath = saveDirectory.resolve(fileName).toAbsolutePath().normalize();
            assetService.ensureInsideAssetRootForWrite(videoPath);
            Files.write(videoPath, videoFile.content());

            Long assetId = assetRepository.create(
                    series.id(),
                    episode.id(),
                    scene == null ? shot.sceneId() : scene.id(),
                    shot.id(),
                    null,
                    "SHOT_VIDEO",
                    "VIDEO_CLIP",
                    firstFrame.id(),
                    fileName,
                    normalizeVideoContentType(videoFile.contentType()),
                    videoPath.toString(),
                    prompt,
                    providerTask.providerTaskId(),
                    "READY"
            );
            workflowRepository.completeTask(taskId, assetId, providerTask.providerTaskId(), "视频生成完成");
        } catch (Exception ex) {
            workflowRepository.failTask(taskId, "视频生成失败：" + summarizeError(ex));
        }
    }

    private String waitForVideoUrl(Long taskId, String providerTaskId) {
        RuntimeException lastQueryError = null;
        for (int attempt = 1; attempt <= MAX_QUERY_ATTEMPTS; attempt++) {
            try {
                VideoGenerationClient.VideoTaskStatus status = videoGenerationClient.queryVideoTask(providerTaskId);
                String normalizedStatus = nullToEmpty(status.status()).toLowerCase(Locale.ROOT);
                if ("failed".equals(normalizedStatus) || "failure".equals(normalizedStatus)
                        || "cancelled".equals(normalizedStatus) || "canceled".equals(normalizedStatus)) {
                    throw new BusinessException(500, "视频模型任务失败：" + nullToDefault(status.errorMessage(), "未知错误"));
                }
                int progress = normalizeProviderProgress(status.progress());
                workflowRepository.updateTaskProgress(taskId, "RUNNING", progress, "GENERATING", "AI 视频生成中，供应商状态：" + nullToDefault(status.status(), "unknown"));
                if (("completed".equals(normalizedStatus) || "succeeded".equals(normalizedStatus) || "success".equals(normalizedStatus))
                        && status.videoUrl() != null && !status.videoUrl().isBlank()) {
                    return status.videoUrl();
                }
            } catch (RuntimeException ex) {
                lastQueryError = ex;
                // NewAPI 新任务刚创建时偶发 model 为空的 403，不能立刻判失败，继续轮询等待任务同步完成。
                workflowRepository.updateTaskProgress(taskId, "RUNNING", 45, "GENERATING", "视频任务查询等待中：" + summarizeError(ex));
            }
            sleepQueryInterval();
        }
        throw lastQueryError == null
                ? new BusinessException(500, "视频生成超时，未拿到下载地址")
                : new BusinessException(500, "视频生成超时，最后一次查询错误：" + summarizeError(lastQueryError));
    }

    private String buildVideoPrompt(DramaSeriesRecord series, DramaEpisodeRecord episode, DramaSceneRecord scene, DramaShotRecord shot) {
        if (!properties.getText().isReady()) {
            throw new BusinessException(400, "视频提示词生成失败：文本模型未配置");
        }
        List<DramaCharacterRecord> characters = characterRepository.listBySeries(series.id());
        String productionBrief = """
                视频生成生产资料：
                - 项目名称：%s
                - 项目类型：%s
                - 题材：%s
                - 风格：%s
                - 画面比例：%s
                - 当前分集：第 %s 集《%s》
                - 分集摘要：%s
                - 场景名称：%s
                - 场景地点：%s
                - 场景时间：%s
                - 场景氛围：%s
                - 镜头编号：%s
                - 景别：%s
                - 镜头时长：%s 秒
                - 运镜：%s
                - 构图：%s
                - 动作：%s
                - 台词/旁白：%s
                - 原始视频提示词：%s
                - 角色资料：%s
                """.formatted(
                nullToDefault(series.name(), "未命名项目"),
                nullToDefault(series.type(), "短剧/漫剧"),
                nullToDefault(series.theme(), "未设置"),
                nullToDefault(series.style(), "未设置"),
                resolveVideoRatio(series),
                episode.episodeNo(),
                nullToDefault(episode.title(), "未命名"),
                nullToDefault(episode.summary(), "暂无"),
                scene == null ? "暂无" : nullToDefault(scene.name(), "未命名场景"),
                scene == null ? "暂无" : nullToDefault(scene.location(), "暂无"),
                scene == null ? "暂无" : nullToDefault(scene.timeOfDay(), "暂无"),
                scene == null ? "暂无" : nullToDefault(scene.atmosphere(), "暂无"),
                shot.shotNo(),
                nullToDefault(shot.shotSize(), "未设置"),
                resolveVideoSeconds(shot),
                nullToDefault(shot.cameraMovement(), "自然轻微镜头运动"),
                nullToDefault(shot.composition(), "保持首帧构图"),
                nullToDefault(shot.action(), "暂无"),
                nullToDefault(shot.dialogue(), "无"),
                nullToDefault(shot.videoPrompt(), "暂无"),
                formatCharactersForVideoPrompt(characters)
        );
        String instruction = """
                OUTPUT LANGUAGE POLICY:
                Return ASCII English only. Do not output Chinese, Markdown, explanations, labels, or code fences.

                You are a professional short-drama director, animation director, and AI video prompt engineer.
                Rewrite the following Chinese production brief into one concise but detailed English image-to-video prompt.
                The video model will receive a first-frame reference image through input_reference. The prompt must extend that exact first frame, not redesign the frame.

                Mandatory rules:
                1. Start with: "Use the input reference image as the exact first frame".
                2. Strongly preserve character identity from the first frame and role cards: face, age, gender, hairstyle, outfit, body type, temperament, color palette.
                3. Dialogue is handled later by TTS. The video must perform emotion and action only. Do not ask for accurate lip sync, do not generate speaking mouth close-ups, and avoid exaggerated mouth movement.
                4. Absolutely forbid duplicated bodies, duplicated heads, extra limbs, fused limbs, malformed hands, distorted faces, cloned characters, random extra people, or character redesign.
                5. Keep motion smooth and physically plausible. Prefer subtle acting, fabric movement, hair movement, mist, light, camera movement, and body reaction.
                6. Include camera movement, action rhythm, mood, environment motion, shot start state, shot end state, and continuity with adjacent shots.
                7. Include negative requirements: no subtitles, no text, no logo, no watermark.
                8. End with exactly these model parameters: -ratio=%s -resolution=720p -seconds=%s -generate_audio=false -camera_fixed=false

                Production brief:
                %s
                """.formatted(resolveVideoRatio(series), resolveVideoSeconds(shot), productionBrief);
        String prompt = textGenerationClient.generate(instruction).trim();
        if (!isUsableEnglishVideoPrompt(prompt)) {
            throw new BusinessException(500, "视频英文提示词生成失败，文本模型返回不可用：" + limitText(prompt, 500));
        }
        return prompt;
    }

    public DramaTaskVo toTaskVo(DramaTaskRecord record) {
        return new DramaTaskVo(
                record.id(), record.seriesId(), record.episodeId(), record.shotId(), record.characterId(), record.assetId(),
                record.targetType(), record.targetId(), record.assetType(), record.assetSubType(), record.taskType(),
                record.providerTaskId(), record.status(), record.progress(), record.stage(), record.errorMessage(),
                record.createdAt(), record.updatedAt()
        );
    }

    private Optional<DramaAssetRecord> findShotFirstFrame(Long episodeId, Long shotId) {
        return assetRepository.listByEpisode(episodeId, 1000).stream()
                .filter(asset -> shotId.equals(asset.shotId()))
                .filter(asset -> "SHOT_IMAGE".equals(asset.assetType()))
                .findFirst();
    }

    private Path resolveShotDirectory(DramaSeriesRecord series, DramaEpisodeRecord episode, DramaShotRecord shot) throws IOException {
        Path directory = assetService.ensureSeriesRoot(series.id())
                .resolve(safeFileName("第" + episode.episodeNo() + "集-" + episode.id()))
                .resolve("镜头")
                .resolve(safeFileName("镜头-" + shot.shotNo() + "-" + shot.id()))
                .normalize();
        assetService.ensureInsideAssetRootForWrite(directory);
        return directory;
    }

    private String resolveVideoRatio(DramaSeriesRecord series) {
        return "PORTRAIT_9_16".equalsIgnoreCase(nullToEmpty(series.aspectRatio())) ? "9:16" : "16:9";
    }

    private int resolveVideoSeconds(DramaShotRecord shot) {
        Integer seconds = shot.durationSeconds();
        if (seconds == null || seconds <= 0) {
            return 5;
        }
        return Math.max(4, Math.min(seconds, 12));
    }

    private int normalizeProviderProgress(Integer providerProgress) {
        if (providerProgress == null) {
            return 50;
        }
        return Math.max(30, Math.min(82, providerProgress));
    }

    private boolean isUsableEnglishVideoPrompt(String value) {
        String text = nullToEmpty(value).trim();
        if (text.length() < 120) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                return false;
            }
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("input reference")
                && lower.contains("no subtitles")
                && lower.contains("no watermark")
                && lower.contains("-generate_audio=false")
                && lower.contains("-ratio=");
    }

    private String formatCharactersForVideoPrompt(List<DramaCharacterRecord> characters) {
        if (characters == null || characters.isEmpty()) {
            return "暂无角色资料";
        }
        return characters.stream()
                .map(character -> "角色：" + nullToDefault(character.name(), "未命名")
                        + "；人设：" + nullToDefault(character.profile(), "暂无")
                        + "；外貌：" + nullToDefault(character.appearance(), "暂无")
                        + "；服装：" + nullToDefault(character.costume(), "暂无")
                        + "；性格：" + nullToDefault(character.personality(), "暂无")
                        + "；关系：" + nullToDefault(character.relationship(), "暂无"))
                .toList()
                .toString();
    }

    private String normalizeVideoContentType(String contentType) {
        return contentType == null || contentType.isBlank() ? "video/mp4" : contentType.split(";")[0].trim();
    }

    private void sleepQueryInterval() {
        try {
            Thread.sleep(QUERY_INTERVAL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("视频任务轮询被中断", ex);
        }
    }

    private String summarizeError(Throwable ex) {
        String message = ex == null || ex.getMessage() == null ? "未知错误" : ex.getMessage();
        String text = message
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() <= 220 ? text : text.substring(0, 220) + "...";
    }

    private String safeFileName(String value) {
        String safe = value == null ? "未命名" : value.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return safe.isBlank() ? "未命名" : safe;
    }

    private String limitText(String value, int maxLength) {
        String text = nullToEmpty(value).trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private String nullToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

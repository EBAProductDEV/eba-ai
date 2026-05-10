package com.qctv1.ai.drama.service;

import com.qctv1.ai.drama.config.DramaProperties;
import com.qctv1.ai.drama.domain.DramaImageGenerationContext;
import com.qctv1.ai.drama.domain.DramaTaskRecord;
import com.qctv1.ai.drama.provider.ImageGenerationClient;
import com.qctv1.ai.drama.repository.DramaAssetRepository;
import com.qctv1.ai.drama.repository.DramaCharacterRepository;
import com.qctv1.ai.drama.repository.DramaWorkflowRepository;
import com.qctv1.ai.drama.support.BusinessException;
import com.qctv1.ai.drama.vo.DramaTaskVo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class DramaImageGenerationService {

    private static final int MODEL_IMAGE_MAX_ATTEMPTS = 1;

    private final DramaProperties properties;
    private final DramaAssetRepository assetRepository;
    private final DramaCharacterRepository characterRepository;
    private final DramaWorkflowRepository workflowRepository;
    private final DramaAssetService assetService;
    private final ImageGenerationClient imageGenerationClient;
    private final TaskExecutor imageTaskExecutor;

    public DramaImageGenerationService(
            DramaProperties properties,
            DramaAssetRepository assetRepository,
            DramaCharacterRepository characterRepository,
            DramaWorkflowRepository workflowRepository,
            DramaAssetService assetService,
            ImageGenerationClient imageGenerationClient,
            @Qualifier("dramaImageTaskExecutor") TaskExecutor imageTaskExecutor
    ) {
        this.properties = properties;
        this.assetRepository = assetRepository;
        this.characterRepository = characterRepository;
        this.workflowRepository = workflowRepository;
        this.assetService = assetService;
        this.imageGenerationClient = imageGenerationClient;
        this.imageTaskExecutor = imageTaskExecutor;
    }

    public DramaTaskVo submit(DramaImageGenerationContext context) {
        Long taskId = workflowRepository.createTask(
                context.seriesId(),
                context.episodeId(),
                context.shotId(),
                context.characterId(),
                null,
                context.targetType(),
                context.targetId(),
                context.assetType(),
                context.assetSubType(),
                context.assetType() + "_GENERATE",
                null,
                "PENDING",
                0,
                "QUEUED",
                buildQueuedMessage(context)
        );
        imageTaskExecutor.execute(() -> runImageTask(taskId, context));
        return toTaskVo(workflowRepository.findTask(taskId).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "任务不存在")));
    }

    public DramaTaskVo submit(DramaImageGenerationContext context, Supplier<String> promptSupplier) {
        Long taskId = workflowRepository.createTask(
                context.seriesId(),
                context.episodeId(),
                context.shotId(),
                context.characterId(),
                null,
                context.targetType(),
                context.targetId(),
                context.assetType(),
                context.assetSubType(),
                context.assetType() + "_GENERATE",
                null,
                "PENDING",
                0,
                "PROMPTING",
                "图片任务已提交，正在进行提示词翻译与组装"
        );
        imageTaskExecutor.execute(() -> runImageTaskWithPrompt(taskId, context, promptSupplier));
        return toTaskVo(workflowRepository.findTask(taskId).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "任务不存在")));
    }

    private void runImageTaskWithPrompt(Long taskId, DramaImageGenerationContext context, Supplier<String> promptSupplier) {
        try {
            workflowRepository.updateTaskProgress(taskId, "RUNNING", 8, "PROMPTING", "提示词翻译组装中");
            String prompt = promptSupplier.get();
            if (prompt == null || prompt.isBlank()) {
                throw new BusinessException(500, "图片提示词生成失败，请检查文本模型配置后重新生成");
            }
            runImageTask(taskId, new DramaImageGenerationContext(
                    context.seriesId(),
                    context.episodeId(),
                    context.sceneId(),
                    context.shotId(),
                    context.characterId(),
                    context.targetType(),
                    context.targetId(),
                    context.assetType(),
                    context.assetSubType(),
                    context.referenceAssetId(),
                    context.referenceAssetIds(),
                    prompt,
                    context.seed(),
                    context.fileNamePrefix(),
                    context.contentType(),
                    context.imageSize(),
                    context.imageQuality(),
                    context.imageFormat(),
                    context.saveDirectory()
            ));
        } catch (Exception ex) {
            workflowRepository.failTask(taskId, "图片生成失败：" + summarizeProviderError(ex));
        }
    }

    public void runImageTask(Long taskId, DramaImageGenerationContext context) {
        try {
            workflowRepository.updateTaskProgress(taskId, "RUNNING", 10, "GENERATING", "图片生成任务已开始");
            Thread.sleep(resolveMockDelayMillis());
            workflowRepository.updateTaskProgress(taskId, "RUNNING", 60, "DOWNLOADING", "图片已生成，正在下载或准备文件");

            GeneratedImageFile imageFile = writeImageFile(taskId, context);
            workflowRepository.updateTaskProgress(taskId, "RUNNING", 85, "SAVING", "图片文件已写入，正在保存素材记录");
            Long assetId = assetRepository.create(
                    context.seriesId(),
                    context.episodeId(),
                    context.sceneId(),
                    context.shotId(),
                    context.characterId(),
                    context.assetType(),
                    context.assetSubType(),
                    context.referenceAssetId(),
                    imageFile.path().getFileName().toString(),
                    imageFile.contentType(),
                    imageFile.path().toString(),
                    context.prompt(),
                    context.seed(),
                    "READY"
            );
            updateTargetReference(context, assetId);
            workflowRepository.completeTask(taskId, assetId, imageFile.providerTaskId(), "图片生成完成");
        } catch (Exception ex) {
            workflowRepository.failTask(taskId, "图片生成失败：" + summarizeProviderError(ex));
        }
    }

    private void updateTargetReference(DramaImageGenerationContext context, Long assetId) {
        if (!"CHARACTER".equals(context.targetType()) || context.characterId() == null) {
            return;
        }
        Long avatarAssetId = "AVATAR".equals(context.assetSubType()) ? assetId : null;
        Long primaryReferenceAssetId = "PORTRAIT".equals(context.assetSubType()) ? assetId : null;
        if (avatarAssetId != null || primaryReferenceAssetId != null) {
            characterRepository.updateImageReference(context.seriesId(), context.characterId(), avatarAssetId, primaryReferenceAssetId, context.seed());
        }
    }

    public DramaTaskVo toTaskVo(DramaTaskRecord record) {
        return new DramaTaskVo(
                record.id(),
                record.seriesId(),
                record.episodeId(),
                record.shotId(),
                record.characterId(),
                record.assetId(),
                record.targetType(),
                record.targetId(),
                record.assetType(),
                record.assetSubType(),
                record.taskType(),
                record.providerTaskId(),
                record.status(),
                record.progress(),
                record.stage(),
                record.errorMessage(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private GeneratedImageFile writeImageFile(Long taskId, DramaImageGenerationContext context) throws IOException {
        Path saveDirectory = context.saveDirectory().toAbsolutePath().normalize();
        assetService.ensureInsideAssetRootForWrite(saveDirectory);
        Files.createDirectories(saveDirectory);

        if (properties.getImage().isReady()) {
            ImageGenerationClient.ImageResult imageResult = generateModelImage(taskId, context);
            if (imageResult != null && imageResult.content() != null && imageResult.content().length > 0) {
                String fileName = safeFileName(context.fileNamePrefix()) + "-" + System.currentTimeMillis() + imageResult.fileExtension();
                Path imagePath = saveDirectory.resolve(fileName).normalize();
                assetService.ensureInsideAssetRootForWrite(imagePath);
                Files.write(imagePath, imageResult.content());
                return new GeneratedImageFile(imagePath, imageResult.contentType(), imageResult.providerTaskId());
            }
        }

        String extension = ".svg";
        String fileName = safeFileName(context.fileNamePrefix()) + "-" + System.currentTimeMillis() + extension;
        Path imagePath = saveDirectory.resolve(fileName).normalize();
        assetService.ensureInsideAssetRootForWrite(imagePath);
        Files.writeString(imagePath, buildPlaceholderSvg(context), StandardCharsets.UTF_8);
        return new GeneratedImageFile(imagePath, "image/svg+xml", null);
    }

    private ImageGenerationClient.ImageResult generateModelImage(Long taskId, DramaImageGenerationContext context) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MODEL_IMAGE_MAX_ATTEMPTS; attempt++) {
            try {
                workflowRepository.updateTaskProgress(
                        taskId,
                        "RUNNING",
                        Math.min(55, 15 + attempt * 10),
                        "GENERATING",
                        "正在调用图片模型，第 " + attempt + "/" + MODEL_IMAGE_MAX_ATTEMPTS + " 次"
                );
                List<Path> referenceImages = resolveReferenceImages(context);
                if (referenceImages.isEmpty()) {
                    return imageGenerationClient.generateImage(context.prompt(), context.imageSize(), context.imageQuality(), context.imageFormat());
                }
                return imageGenerationClient.editImage(context.prompt(), referenceImages, context.imageSize(), context.imageQuality(), context.imageFormat());
            } catch (RuntimeException ex) {
                lastException = ex;
                if (attempt >= MODEL_IMAGE_MAX_ATTEMPTS || !isTransientProviderError(ex)) {
                    throw ex;
                }
                long retryDelayMillis = retryDelayMillis(attempt);
                workflowRepository.updateTaskProgress(
                        taskId,
                        "RUNNING",
                        Math.min(65, 25 + attempt * 12),
                        "RETRYING",
                        "图片供应商临时失败，" + (retryDelayMillis / 1000) + " 秒后自动重试第 "
                                + (attempt + 1) + "/" + MODEL_IMAGE_MAX_ATTEMPTS + " 次："
                                + summarizeProviderError(ex)
                );
                sleepBeforeRetry(retryDelayMillis);
            }
        }
        throw lastException == null ? new IllegalStateException("图片模型调用失败") : lastException;
    }

    private List<Path> resolveReferenceImages(DramaImageGenerationContext context) {
        List<Long> referenceIds = context.referenceAssetIds() == null || context.referenceAssetIds().isEmpty()
                ? (context.referenceAssetId() == null ? List.of() : List.of(context.referenceAssetId()))
                : context.referenceAssetIds();
        return referenceIds.stream()
                .distinct()
                .limit(16)
                .map(assetRepository::findById)
                .flatMap(Optional::stream)
                .map(referenceAsset -> Path.of(referenceAsset.localPath()).toAbsolutePath().normalize())
                .filter(path -> Files.exists(path) && Files.isRegularFile(path))
                .toList();
    }

    private boolean isTransientProviderError(Throwable ex) {
        String message = ex == null || ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("524")
                || message.contains("502")
                || message.contains("503")
                || message.contains("504")
                || message.contains("bad_gateway")
                || message.contains("gateway")
                || message.contains("timeout")
                || message.contains("timed out")
                || message.contains("cloudflare")
                || message.contains("connection reset")
                || message.contains("premature close");
    }

    private long retryDelayMillis(int attempt) {
        return switch (attempt) {
            case 1 -> 10_000L;
            case 2 -> 20_000L;
            default -> 30_000L;
        };
    }

    private void sleepBeforeRetry(long retryDelayMillis) {
        try {
            Thread.sleep(retryDelayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("图片生成重试等待被中断", ex);
        }
    }

    private String summarizeProviderError(Throwable ex) {
        String message = ex == null || ex.getMessage() == null ? "" : ex.getMessage();
        String text = message
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() <= 180 ? text : text.substring(0, 180) + "...";
    }

    private String buildPlaceholderSvg(DramaImageGenerationContext context) {
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="960" height="1280" viewBox="0 0 960 1280">
                  <defs>
                    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%%" stop-color="#eff6ff"/>
                      <stop offset="50%%" stop-color="#ffffff"/>
                      <stop offset="100%%" stop-color="#fff7ed"/>
                    </linearGradient>
                    <linearGradient id="mark" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%%" stop-color="#0052d9"/>
                      <stop offset="100%%" stop-color="#19a7ce"/>
                    </linearGradient>
                  </defs>
                  <rect width="960" height="1280" rx="56" fill="url(#bg)"/>
                  <rect x="70" y="70" width="820" height="1140" rx="44" fill="rgba(255,255,255,0.78)" stroke="#d7e3f8" stroke-width="3"/>
                  <circle cx="480" cy="360" r="150" fill="url(#mark)"/>
                  <text x="480" y="385" text-anchor="middle" font-family="Microsoft YaHei, Arial, sans-serif" font-size="56" font-weight="800" fill="#ffffff">%s</text>
                  <text x="480" y="610" text-anchor="middle" font-family="Microsoft YaHei, Arial, sans-serif" font-size="54" font-weight="800" fill="#172033">%s</text>
                  <text x="480" y="690" text-anchor="middle" font-family="Microsoft YaHei, Arial, sans-serif" font-size="34" font-weight="700" fill="#0052d9">%s</text>
                  <text x="480" y="780" text-anchor="middle" font-family="Microsoft YaHei, Arial, sans-serif" font-size="28" fill="#667085">图片模型未配置，当前为任务流占位图</text>
                  <foreignObject x="130" y="845" width="700" height="250">
                    <div xmlns="http://www.w3.org/1999/xhtml" style="font-family:'Microsoft YaHei',Arial,sans-serif;font-size:24px;line-height:1.65;color:#475467;text-align:center;">
                      %s
                    </div>
                  </foreignObject>
                </svg>
                """.formatted(
                escapeXml(context.targetType()),
                escapeXml(context.assetType()),
                escapeXml(context.assetSubType()),
                escapeXml(shortText(context.prompt(), 180)).replace("\n", "<br/>")
        );
    }

    private String buildQueuedMessage(DramaImageGenerationContext context) {
        if (properties.getImage().isReady()) {
            return "图片生成任务已提交，等待模型生成";
        }
        return "图片模型配置未完成，任务会生成本地占位图";
    }

    private long resolveMockDelayMillis() {
        return properties.getImage().isReady() ? 0L : 1200L;
    }

    private String safeFileName(String value) {
        String safe = value == null ? "image" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
        return safe.isBlank() ? "image" : safe;
    }

    private String shortText(String value, int maxLength) {
        String text = value == null || value.isBlank() ? "无提示词" : value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private String escapeXml(String value) {
        String text = value == null || value.isBlank() ? "待补充" : value;
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private record GeneratedImageFile(
            Path path,
            String contentType,
            String providerTaskId
    ) {
    }
}

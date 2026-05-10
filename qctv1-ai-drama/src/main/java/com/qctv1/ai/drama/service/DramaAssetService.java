package com.qctv1.ai.drama.service;

import com.qctv1.ai.drama.config.DramaProperties;
import com.qctv1.ai.drama.domain.DramaAssetRecord;
import com.qctv1.ai.drama.domain.DramaCharacterRecord;
import com.qctv1.ai.drama.repository.DramaAssetRepository;
import com.qctv1.ai.drama.repository.DramaCharacterRepository;
import com.qctv1.ai.drama.support.BusinessException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DramaAssetService implements ApplicationRunner {

    private static final String CHARACTER_IMAGE_ASSET_TYPE = "CHARACTER_IMAGE";
    private static final Set<String> CHARACTER_IMAGE_TYPES = Set.of("AVATAR", "PORTRAIT", "THREE_VIEW", "EXPRESSION", "COSTUME");
    private static final Map<String, String> CHARACTER_IMAGE_TYPE_NAMES = Map.of(
            "AVATAR", "头像",
            "PORTRAIT", "定妆图",
            "THREE_VIEW", "三视图",
            "EXPRESSION", "表情参考图",
            "COSTUME", "服装版本图"
    );

    private final DramaProperties properties;
    private final DramaAssetRepository assetRepository;
    private final DramaCharacterRepository characterRepository;

    public DramaAssetService(
            DramaProperties properties,
            DramaAssetRepository assetRepository,
            DramaCharacterRepository characterRepository
    ) {
        this.properties = properties;
        this.assetRepository = assetRepository;
        this.characterRepository = characterRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Files.createDirectories(assetRoot());
    }

    public Path assetRoot() {
        return Path.of(properties.getAssetRoot()).toAbsolutePath().normalize();
    }

    public void ensureInsideAssetRootForWrite(Path path) {
        ensureInsideAssetRoot(path);
    }

    public Path ensureSeriesRoot(Long seriesId) throws IOException {
        Path seriesRoot = assetRoot().resolve("短剧-" + seriesId).normalize();
        ensureInsideAssetRoot(seriesRoot);
        Files.createDirectories(seriesRoot);
        Files.createDirectories(seriesRoot.resolve("知识资料"));
        Files.createDirectories(seriesRoot.resolve("角色图"));
        Files.createDirectories(seriesRoot.resolve("场景图"));
        Files.createDirectories(seriesRoot.resolve("镜头图"));
        Files.createDirectories(seriesRoot.resolve("成片"));
        return seriesRoot;
    }

    public Resource loadAssetContent(Long assetId) {
        DramaAssetRecord asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException(404, "素材不存在"));
        Path path = Path.of(asset.localPath()).toAbsolutePath().normalize();
        ensureInsideAssetRoot(path);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new BusinessException(404, "素材文件不存在");
        }
        return new FileSystemResource(path);
    }

    public MediaType detectMediaType(Long assetId) {
        DramaAssetRecord asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException(404, "素材不存在"));
        if (asset.contentType() != null && !asset.contentType().isBlank()) {
            return MediaType.parseMediaType(asset.contentType());
        }
        String fileName = asset.fileName() == null ? "" : asset.fileName().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (fileName.endsWith(".mp4")) {
            return MediaType.parseMediaType("video/mp4");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @Transactional
    public void deleteAsset(Long assetId) {
        DramaAssetRecord asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException(404, "素材不存在"));
        moveAssetToRecycle(asset);
        if (!assetRepository.deleteById(assetId)) {
            throw new BusinessException(404, "素材不存在");
        }
        if (asset.characterId() != null) {
            characterRepository.findBySeriesAndId(asset.seriesId(), asset.characterId())
                    .ifPresent(character -> characterRepository.clearImageReference(
                            asset.seriesId(),
                            asset.characterId(),
                            assetId.equals(character.avatarAssetId()),
                            assetId.equals(character.primaryReferenceAssetId())
                    ));
        }
    }

    public DramaAssetRecord generatePlaceholderCharacterImage(DramaCharacterRecord character, String imageType, Long referenceAssetId) {
        String normalizedType = normalizeCharacterImageType(imageType);
        String typeName = CHARACTER_IMAGE_TYPE_NAMES.get(normalizedType);
        String seed = character.imageSeed() == null || character.imageSeed().isBlank()
                ? "CHAR-" + character.id() + "-" + UUID.randomUUID().toString().substring(0, 8)
                : character.imageSeed();
        try {
            Path seriesRoot = ensureSeriesRoot(character.seriesId());
            Path imageDir = seriesRoot
                    .resolve("角色图")
                    .resolve(safeFileName("角色-" + character.id() + "-" + character.name()))
                    .resolve(typeName)
                    .normalize();
            ensureInsideAssetRoot(imageDir);
            Files.createDirectories(imageDir);

            String fileName = normalizedType.toLowerCase(Locale.ROOT) + "-" + System.currentTimeMillis() + ".svg";
            Path imagePath = imageDir.resolve(fileName).normalize();
            ensureInsideAssetRoot(imagePath);
            String prompt = buildCharacterImagePrompt(character, typeName, referenceAssetId);
            Files.writeString(imagePath, buildPlaceholderSvg(character, typeName, prompt), StandardCharsets.UTF_8);

            Long assetId = assetRepository.create(
                    character.seriesId(),
                    null,
                    null,
                    null,
                    character.id(),
                    CHARACTER_IMAGE_ASSET_TYPE,
                    normalizedType,
                    referenceAssetId,
                    fileName,
                    "image/svg+xml",
                    imagePath.toString(),
                    prompt,
                    seed,
                    "READY"
            );
            return assetRepository.findById(assetId)
                    .orElseThrow(() -> new BusinessException(500, "角色图片素材保存失败"));
        } catch (IOException ex) {
            throw new BusinessException(500, "生成角色占位图失败：" + ex.getMessage());
        }
    }

    public DramaAssetRecord saveCharacterVoiceSample(DramaCharacterRecord character, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请先选择音频文件");
        }
        String originalName = safeFileName(file.getOriginalFilename() == null ? "voice-sample" : file.getOriginalFilename());
        String contentType = file.getContentType() == null || file.getContentType().isBlank()
                ? detectAudioContentType(originalName)
                : file.getContentType();
        if (!isAudioFile(originalName, contentType)) {
            throw new BusinessException(400, "只支持上传音频样例文件");
        }
        try {
            Path voiceDir = ensureSeriesRoot(character.seriesId())
                    .resolve("角色音色")
                    .resolve(safeFileName("角色-" + character.id() + "-" + character.name()))
                    .normalize();
            ensureInsideAssetRoot(voiceDir);
            Files.createDirectories(voiceDir);

            String extension = fileExtension(originalName);
            String fileName = "voice-sample-" + System.currentTimeMillis() + extension;
            Path target = voiceDir.resolve(fileName).normalize();
            ensureInsideAssetRoot(target);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            Long assetId = assetRepository.create(
                    character.seriesId(),
                    null,
                    null,
                    null,
                    character.id(),
                    "CHARACTER_VOICE",
                    "VOICE_SAMPLE",
                    null,
                    fileName,
                    contentType,
                    target.toString(),
                    "角色音频样例：" + originalName,
                    null,
                    "READY"
            );
            return assetRepository.findById(assetId)
                    .orElseThrow(() -> new BusinessException(500, "音频样例素材保存失败"));
        } catch (IOException ex) {
            throw new BusinessException(500, "保存音频样例失败：" + ex.getMessage());
        }
    }

    public void moveAssetToRecycle(DramaAssetRecord asset) {
        if (asset.localPath() == null || asset.localPath().isBlank()) {
            return;
        }
        Path source = Path.of(asset.localPath()).toAbsolutePath().normalize();
        ensureInsideAssetRoot(source);
        if (!Files.exists(source) || !Files.isRegularFile(source)) {
            return;
        }
        Path recycleDir = assetRoot().resolve("回收站").normalize();
        ensureInsideAssetRoot(recycleDir);
        try {
            Files.createDirectories(recycleDir);
            String recycledFileName = System.currentTimeMillis() + "-asset-" + asset.id() + "-" + source.getFileName();
            Path target = recycleDir.resolve(recycledFileName).normalize();
            ensureInsideAssetRoot(target);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException(500, "移动素材到回收站失败：" + ex.getMessage());
        }
    }

    public String normalizeCharacterImageType(String imageType) {
        String normalizedType = imageType == null ? "" : imageType.trim().toUpperCase(Locale.ROOT);
        if (!CHARACTER_IMAGE_TYPES.contains(normalizedType)) {
            throw new BusinessException(400, "不支持的角色图片类型：" + imageType);
        }
        return normalizedType;
    }

    private String buildCharacterImagePrompt(DramaCharacterRecord character, String typeName, Long referenceAssetId) {
        return """
                角色图片占位生成说明：
                图片类型：%s
                角色名称：%s
                人设定位：%s
                外貌特点：%s
                常用服装：%s
                性格特点：%s
                人物关系：%s
                基准素材ID：%s
                当前阶段不调用真实图片模型，后续接入模型时直接替换 SVG 占位文件生成步骤。
                """.formatted(
                typeName,
                nullToDefault(character.name()),
                nullToDefault(character.profile()),
                nullToDefault(character.appearance()),
                nullToDefault(character.costume()),
                nullToDefault(character.personality()),
                nullToDefault(character.relationship()),
                referenceAssetId == null ? "无" : referenceAssetId
        );
    }

    private String buildPlaceholderSvg(DramaCharacterRecord character, String typeName, String prompt) {
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="960" height="1280" viewBox="0 0 960 1280">
                  <defs>
                    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%%" stop-color="#eff6ff"/>
                      <stop offset="50%%" stop-color="#ffffff"/>
                      <stop offset="100%%" stop-color="#fff7ed"/>
                    </linearGradient>
                    <linearGradient id="avatar" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%%" stop-color="#0052d9"/>
                      <stop offset="100%%" stop-color="#19a7ce"/>
                    </linearGradient>
                  </defs>
                  <rect width="960" height="1280" rx="56" fill="url(#bg)"/>
                  <rect x="70" y="70" width="820" height="1140" rx="44" fill="rgba(255,255,255,0.78)" stroke="#d7e3f8" stroke-width="3"/>
                  <circle cx="480" cy="360" r="150" fill="url(#avatar)"/>
                  <circle cx="480" cy="315" r="58" fill="#ffffff" opacity="0.92"/>
                  <path d="M350 486c36-70 224-70 260 0" fill="#ffffff" opacity="0.92"/>
                  <text x="480" y="610" text-anchor="middle" font-family="Microsoft YaHei, Arial, sans-serif" font-size="58" font-weight="800" fill="#172033">%s</text>
                  <text x="480" y="690" text-anchor="middle" font-family="Microsoft YaHei, Arial, sans-serif" font-size="36" font-weight="700" fill="#0052d9">%s</text>
                  <text x="480" y="780" text-anchor="middle" font-family="Microsoft YaHei, Arial, sans-serif" font-size="28" fill="#667085">占位图，后续接入图片模型</text>
                  <foreignObject x="130" y="845" width="700" height="250">
                    <div xmlns="http://www.w3.org/1999/xhtml" style="font-family:'Microsoft YaHei',Arial,sans-serif;font-size:24px;line-height:1.65;color:#475467;text-align:center;">
                      %s
                    </div>
                  </foreignObject>
                </svg>
                """.formatted(
                escapeXml(character.name()),
                escapeXml(typeName),
                escapeXml(shortText(prompt, 180)).replace("\n", "<br/>")
        );
    }

    private String safeFileName(String value) {
        String safe = value == null ? "未命名" : value.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return safe.isBlank() ? "未命名" : safe;
    }

    private boolean isAudioFile(String fileName, String contentType) {
        String lowerName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String lowerType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return lowerType.startsWith("audio/")
                || lowerName.endsWith(".mp3")
                || lowerName.endsWith(".wav")
                || lowerName.endsWith(".m4a")
                || lowerName.endsWith(".aac")
                || lowerName.endsWith(".ogg")
                || lowerName.endsWith(".flac");
    }

    private String detectAudioContentType(String fileName) {
        String lowerName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lowerName.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lowerName.endsWith(".m4a")) {
            return "audio/mp4";
        }
        if (lowerName.endsWith(".ogg")) {
            return "audio/ogg";
        }
        if (lowerName.endsWith(".flac")) {
            return "audio/flac";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private String fileExtension(String fileName) {
        String safe = fileName == null ? "" : fileName;
        int index = safe.lastIndexOf('.');
        if (index < 0 || index == safe.length() - 1) {
            return "";
        }
        String extension = safe.substring(index).toLowerCase(Locale.ROOT);
        return extension.length() > 12 ? "" : extension;
    }

    private String shortText(String value, int maxLength) {
        String text = nullToDefault(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private String nullToDefault(String value) {
        return value == null || value.isBlank() ? "待补充" : value;
    }

    private String escapeXml(String value) {
        return nullToDefault(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private void ensureInsideAssetRoot(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(assetRoot())) {
            throw new BusinessException(400, "非法素材路径");
        }
    }
}

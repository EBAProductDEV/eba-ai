package com.qctv1.ai.drama.service;

import com.qctv1.ai.drama.config.DramaProperties;
import com.qctv1.ai.drama.domain.DramaAssetRecord;
import com.qctv1.ai.drama.repository.DramaAssetRepository;
import com.qctv1.ai.drama.support.BusinessException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Service
public class DramaAssetService implements ApplicationRunner {

    private final DramaProperties properties;
    private final DramaAssetRepository assetRepository;

    public DramaAssetService(DramaProperties properties, DramaAssetRepository assetRepository) {
        this.properties = properties;
        this.assetRepository = assetRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Files.createDirectories(assetRoot());
    }

    public Path assetRoot() {
        return Path.of(properties.getAssetRoot()).toAbsolutePath().normalize();
    }

    public Path ensureSeriesRoot(Long seriesId) throws IOException {
        Path seriesRoot = assetRoot().resolve("短剧-" + seriesId).normalize();
        ensureInsideAssetRoot(seriesRoot);
        Files.createDirectories(seriesRoot);
        Files.createDirectories(seriesRoot.resolve("知识资料"));
        Files.createDirectories(seriesRoot.resolve("角色图"));
        Files.createDirectories(seriesRoot.resolve("场景图"));
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

    private void ensureInsideAssetRoot(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(assetRoot())) {
            throw new BusinessException(400, "非法素材路径");
        }
    }
}

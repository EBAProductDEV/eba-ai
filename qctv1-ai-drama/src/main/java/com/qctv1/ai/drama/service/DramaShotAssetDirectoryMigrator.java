package com.qctv1.ai.drama.service;

import com.qctv1.ai.drama.domain.DramaAssetRecord;
import com.qctv1.ai.drama.domain.DramaEpisodeRecord;
import com.qctv1.ai.drama.domain.DramaShotRecord;
import com.qctv1.ai.drama.repository.DramaAssetRepository;
import com.qctv1.ai.drama.repository.DramaWorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

@Component
public class DramaShotAssetDirectoryMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DramaShotAssetDirectoryMigrator.class);

    private final DramaAssetRepository assetRepository;
    private final DramaWorkflowRepository workflowRepository;
    private final DramaAssetService assetService;

    public DramaShotAssetDirectoryMigrator(
            DramaAssetRepository assetRepository,
            DramaWorkflowRepository workflowRepository,
            DramaAssetService assetService
    ) {
        this.assetRepository = assetRepository;
        this.workflowRepository = workflowRepository;
        this.assetService = assetService;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (DramaAssetRecord asset : assetRepository.listShotAssets()) {
            migrate(asset);
        }
    }

    private void migrate(DramaAssetRecord asset) {
        if (asset.shotId() == null || asset.episodeId() == null || asset.localPath() == null || asset.localPath().isBlank()) {
            return;
        }
        Optional<DramaEpisodeRecord> episodeOptional = workflowRepository.findEpisode(asset.episodeId());
        Optional<DramaShotRecord> shotOptional = workflowRepository.findShot(asset.shotId());
        if (episodeOptional.isEmpty() || shotOptional.isEmpty()) {
            return;
        }
        Path source = Path.of(asset.localPath()).toAbsolutePath().normalize();
        if (!Files.exists(source) || !Files.isRegularFile(source)) {
            return;
        }
        DramaEpisodeRecord episode = episodeOptional.get();
        DramaShotRecord shot = shotOptional.get();
        try {
            Path targetDir = assetService.ensureSeriesRoot(asset.seriesId())
                    .resolve(safeFileName("第" + episode.episodeNo() + "集-" + episode.id()))
                    .resolve("镜头")
                    .resolve(safeFileName("镜头-" + shot.shotNo() + "-" + shot.id()))
                    .normalize();
            assetService.ensureInsideAssetRootForWrite(targetDir);
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(source.getFileName().toString()).toAbsolutePath().normalize();
            assetService.ensureInsideAssetRootForWrite(target);
            if (!source.equals(target)) {
                if (Files.exists(target)) {
                    String name = stripExtension(source.getFileName().toString());
                    String ext = extension(source.getFileName().toString());
                    target = targetDir.resolve(name + "-migrated-" + asset.id() + ext).toAbsolutePath().normalize();
                    assetService.ensureInsideAssetRootForWrite(target);
                }
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                assetRepository.updateLocalPath(asset.id(), target.getFileName().toString(), target.toString());
                log.info("迁移镜头素材目录完成：assetId={}, target={}", asset.id(), target);
            }
        } catch (Exception ex) {
            log.warn("迁移镜头素材目录失败：assetId={}, path={}, error={}", asset.id(), asset.localPath(), ex.getMessage());
        }
    }

    private String safeFileName(String value) {
        String safe = value == null ? "未命名" : value.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return safe.isBlank() ? "未命名" : safe;
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index <= 0 ? fileName : fileName.substring(0, index);
    }

    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index <= 0 ? "" : fileName.substring(index);
    }
}

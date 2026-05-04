package com.qctv1.ai.drama.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qctv1.ai.drama.domain.DramaAssetRecord;
import com.qctv1.ai.drama.entity.DramaAssetEntity;
import com.qctv1.ai.drama.mapper.DramaAssetMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class DramaAssetRepository {

    private final DramaAssetMapper assetMapper;

    public DramaAssetRepository(DramaAssetMapper assetMapper) {
        this.assetMapper = assetMapper;
    }

    public Long create(
            Long seriesId,
            Long episodeId,
            Long shotId,
            String assetType,
            String fileName,
            String contentType,
            String localPath
    ) {
        return create(seriesId, episodeId, null, shotId, null, assetType, null, null, fileName, contentType, localPath, null, null, "READY");
    }

    public Long create(
            Long seriesId,
            Long episodeId,
            Long sceneId,
            Long shotId,
            Long characterId,
            String assetType,
            String assetSubType,
            Long referenceAssetId,
            String fileName,
            String contentType,
            String localPath,
            String prompt,
            String seed,
            String status
    ) {
        DramaAssetEntity entity = new DramaAssetEntity();
        entity.setSeriesId(seriesId);
        entity.setEpisodeId(episodeId);
        entity.setSceneId(sceneId);
        entity.setShotId(shotId);
        entity.setCharacterId(characterId);
        entity.setAssetType(assetType);
        entity.setAssetSubType(assetSubType);
        entity.setReferenceAssetId(referenceAssetId);
        entity.setFileName(fileName);
        entity.setContentType(contentType);
        entity.setLocalPath(localPath);
        entity.setAccessUrl("");
        entity.setPrompt(prompt);
        entity.setSeed(seed);
        entity.setStatus(status == null || status.isBlank() ? "READY" : status);
        entity.setCreatedAt(LocalDateTime.now());
        assetMapper.insert(entity);
        if (entity.getId() != null) {
            assetMapper.update(null, new LambdaUpdateWrapper<DramaAssetEntity>()
                    .set(DramaAssetEntity::getAccessUrl, "/api/ai/drama/assets/" + entity.getId() + "/content")
                    .eq(DramaAssetEntity::getId, entity.getId()));
        }
        return entity.getId();
    }

    public Optional<DramaAssetRecord> findById(Long assetId) {
        if (assetId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(assetMapper.selectById(assetId)).map(this::toRecord);
    }

    public Optional<DramaAssetRecord> findByCharacterAndSubType(Long characterId, String assetSubType) {
        return Optional.ofNullable(assetMapper.selectOne(new LambdaQueryWrapper<DramaAssetEntity>()
                        .eq(DramaAssetEntity::getCharacterId, characterId)
                        .eq(DramaAssetEntity::getAssetSubType, assetSubType)
                        .orderByDesc(DramaAssetEntity::getCreatedAt)
                        .orderByDesc(DramaAssetEntity::getId)
                        .last("LIMIT 1")))
                .map(this::toRecord);
    }

    public boolean existsByCharacterAndSubTypes(Long characterId, List<String> assetSubTypes) {
        if (characterId == null || assetSubTypes == null || assetSubTypes.isEmpty()) {
            return false;
        }
        return assetMapper.selectCount(new LambdaQueryWrapper<DramaAssetEntity>()
                .eq(DramaAssetEntity::getCharacterId, characterId)
                .in(DramaAssetEntity::getAssetSubType, assetSubTypes)) > 0;
    }

    public boolean existsByShotAndAssetType(Long shotId, String assetType) {
        if (shotId == null || assetType == null || assetType.isBlank()) {
            return false;
        }
        return assetMapper.selectCount(new LambdaQueryWrapper<DramaAssetEntity>()
                .eq(DramaAssetEntity::getShotId, shotId)
                .eq(DramaAssetEntity::getAssetType, assetType)) > 0;
    }

    public boolean existsBySceneAndAssetType(Long sceneId, String assetType) {
        if (sceneId == null || assetType == null || assetType.isBlank()) {
            return false;
        }
        return assetMapper.selectCount(new LambdaQueryWrapper<DramaAssetEntity>()
                .eq(DramaAssetEntity::getSceneId, sceneId)
                .eq(DramaAssetEntity::getAssetType, assetType)) > 0;
    }

    public List<DramaAssetRecord> listRecentBySeries(Long seriesId, int limit) {
        return assetMapper.selectList(new LambdaQueryWrapper<DramaAssetEntity>()
                        .eq(DramaAssetEntity::getSeriesId, seriesId)
                        .orderByDesc(DramaAssetEntity::getCreatedAt)
                        .orderByDesc(DramaAssetEntity::getId)
                        .last(limitSql(limit)))
                .stream()
                .map(this::toRecord)
                .toList();
    }

    public List<DramaAssetRecord> listByEpisode(Long episodeId, int limit) {
        return assetMapper.selectList(new LambdaQueryWrapper<DramaAssetEntity>()
                        .eq(DramaAssetEntity::getEpisodeId, episodeId)
                        .orderByDesc(DramaAssetEntity::getCreatedAt)
                        .orderByDesc(DramaAssetEntity::getId)
                        .last(limitSql(limit)))
                .stream()
                .map(this::toRecord)
                .toList();
    }

    public List<DramaAssetRecord> listByCharacter(Long characterId, int limit) {
        return assetMapper.selectList(new LambdaQueryWrapper<DramaAssetEntity>()
                        .eq(DramaAssetEntity::getCharacterId, characterId)
                        .orderByDesc(DramaAssetEntity::getCreatedAt)
                        .orderByDesc(DramaAssetEntity::getId)
                        .last(limitSql(limit)))
                .stream()
                .map(this::toRecord)
                .toList();
    }

    public boolean deleteById(Long assetId) {
        return assetMapper.deleteById(assetId) > 0;
    }

    public List<DramaAssetRecord> listShotAssets() {
        return assetMapper.selectList(new LambdaQueryWrapper<DramaAssetEntity>()
                        .in(DramaAssetEntity::getAssetType, List.of("SHOT_IMAGE", "SHOT_VIDEO"))
                        .orderByAsc(DramaAssetEntity::getId))
                .stream()
                .map(this::toRecord)
                .toList();
    }

    public void updateLocalPath(Long assetId, String fileName, String localPath) {
        assetMapper.update(null, new LambdaUpdateWrapper<DramaAssetEntity>()
                .set(DramaAssetEntity::getFileName, fileName)
                .set(DramaAssetEntity::getLocalPath, localPath)
                .eq(DramaAssetEntity::getId, assetId));
    }

    public int deleteEpisodeAssetsBySeries(Long seriesId) {
        if (seriesId == null) {
            return 0;
        }
        return assetMapper.delete(new LambdaQueryWrapper<DramaAssetEntity>()
                .eq(DramaAssetEntity::getSeriesId, seriesId)
                .isNotNull(DramaAssetEntity::getEpisodeId));
    }

    private String limitSql(int limit) {
        return "LIMIT " + Math.max(1, Math.min(limit, 1000));
    }

    private DramaAssetRecord toRecord(DramaAssetEntity entity) {
        return new DramaAssetRecord(
                entity.getId(),
                entity.getSeriesId(),
                entity.getEpisodeId(),
                entity.getSceneId(),
                entity.getShotId(),
                entity.getCharacterId(),
                entity.getAssetType(),
                entity.getAssetSubType(),
                entity.getReferenceAssetId(),
                entity.getFileName(),
                entity.getContentType(),
                entity.getLocalPath(),
                entity.getAccessUrl(),
                entity.getPrompt(),
                entity.getSeed(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}

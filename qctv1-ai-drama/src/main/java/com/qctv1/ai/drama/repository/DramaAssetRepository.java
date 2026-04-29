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
        DramaAssetEntity entity = new DramaAssetEntity();
        entity.setSeriesId(seriesId);
        entity.setEpisodeId(episodeId);
        entity.setShotId(shotId);
        entity.setAssetType(assetType);
        entity.setFileName(fileName);
        entity.setContentType(contentType);
        entity.setLocalPath(localPath);
        entity.setAccessUrl("");
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
        return Optional.ofNullable(assetMapper.selectById(assetId)).map(this::toRecord);
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

    private String limitSql(int limit) {
        return "LIMIT " + Math.max(1, Math.min(limit, 100));
    }

    private DramaAssetRecord toRecord(DramaAssetEntity entity) {
        return new DramaAssetRecord(
                entity.getId(),
                entity.getSeriesId(),
                entity.getEpisodeId(),
                entity.getShotId(),
                entity.getAssetType(),
                entity.getFileName(),
                entity.getContentType(),
                entity.getLocalPath(),
                entity.getAccessUrl(),
                entity.getCreatedAt()
        );
    }
}

package com.qctv1.ai.drama.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.qctv1.ai.drama.domain.DramaCharacterRecord;
import com.qctv1.ai.drama.entity.DramaCharacterEntity;
import com.qctv1.ai.drama.mapper.DramaCharacterMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class DramaCharacterRepository {

    private final DramaCharacterMapper characterMapper;

    public DramaCharacterRepository(DramaCharacterMapper characterMapper) {
        this.characterMapper = characterMapper;
    }

    public DramaCharacterRecord create(
            Long seriesId,
            String name,
            String profile,
            String appearance,
            String costume,
            String personality,
            String relationship
    ) {
        LocalDateTime now = LocalDateTime.now();
        DramaCharacterEntity entity = new DramaCharacterEntity();
        entity.setSeriesId(seriesId);
        entity.setName(name);
        entity.setProfile(profile);
        entity.setAppearance(appearance);
        entity.setCostume(costume);
        entity.setPersonality(personality);
        entity.setRelationship(relationship);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        characterMapper.insert(entity);
        return findById(entity.getId()).orElseThrow();
    }

    public List<DramaCharacterRecord> listBySeries(Long seriesId) {
        return characterMapper.selectList(new LambdaQueryWrapper<DramaCharacterEntity>()
                        .eq(DramaCharacterEntity::getSeriesId, seriesId)
                        .orderByAsc(DramaCharacterEntity::getId))
                .stream()
                .map(this::toRecord)
                .toList();
    }

    public Optional<DramaCharacterRecord> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(characterMapper.selectById(id)).map(this::toRecord);
    }

    public Optional<DramaCharacterRecord> findBySeriesAndId(Long seriesId, Long characterId) {
        return Optional.ofNullable(characterMapper.selectOne(new LambdaQueryWrapper<DramaCharacterEntity>()
                        .eq(DramaCharacterEntity::getSeriesId, seriesId)
                        .eq(DramaCharacterEntity::getId, characterId)
                        .last("LIMIT 1")))
                .map(this::toRecord);
    }

    public boolean delete(Long seriesId, Long characterId) {
        Wrapper<DramaCharacterEntity> wrapper = new LambdaQueryWrapper<DramaCharacterEntity>()
                .eq(DramaCharacterEntity::getId, characterId)
                .eq(DramaCharacterEntity::getSeriesId, seriesId);
        return characterMapper.delete(wrapper) > 0;
    }

    public boolean update(
            Long seriesId,
            Long characterId,
            String name,
            String profile,
            String appearance,
            String costume,
            String personality,
            String relationship
    ) {
        LambdaUpdateWrapper<DramaCharacterEntity> wrapper = new LambdaUpdateWrapper<DramaCharacterEntity>()
                .set(DramaCharacterEntity::getName, name)
                .set(DramaCharacterEntity::getProfile, profile)
                .set(DramaCharacterEntity::getAppearance, appearance)
                .set(DramaCharacterEntity::getCostume, costume)
                .set(DramaCharacterEntity::getPersonality, personality)
                .set(DramaCharacterEntity::getRelationship, relationship)
                .set(DramaCharacterEntity::getUpdatedAt, LocalDateTime.now())
                .eq(DramaCharacterEntity::getId, characterId)
                .eq(DramaCharacterEntity::getSeriesId, seriesId);
        return characterMapper.update(null, wrapper) > 0;
    }

    public void updateImageReference(Long seriesId, Long characterId, Long avatarAssetId, Long primaryReferenceAssetId, String imageSeed) {
        LambdaUpdateWrapper<DramaCharacterEntity> wrapper = new LambdaUpdateWrapper<DramaCharacterEntity>()
                .set(avatarAssetId != null, DramaCharacterEntity::getAvatarAssetId, avatarAssetId)
                .set(primaryReferenceAssetId != null, DramaCharacterEntity::getPrimaryReferenceAssetId, primaryReferenceAssetId)
                .set(imageSeed != null && !imageSeed.isBlank(), DramaCharacterEntity::getImageSeed, imageSeed)
                .set(DramaCharacterEntity::getUpdatedAt, LocalDateTime.now())
                .eq(DramaCharacterEntity::getId, characterId)
                .eq(DramaCharacterEntity::getSeriesId, seriesId);
        characterMapper.update(null, wrapper);
    }

    public void clearImageReference(Long seriesId, Long characterId, boolean clearAvatar, boolean clearPrimaryReference) {
        LambdaUpdateWrapper<DramaCharacterEntity> wrapper = new LambdaUpdateWrapper<DramaCharacterEntity>()
                .set(clearAvatar, DramaCharacterEntity::getAvatarAssetId, null)
                .set(clearPrimaryReference, DramaCharacterEntity::getPrimaryReferenceAssetId, null)
                .set(DramaCharacterEntity::getUpdatedAt, LocalDateTime.now())
                .eq(DramaCharacterEntity::getId, characterId)
                .eq(DramaCharacterEntity::getSeriesId, seriesId);
        characterMapper.update(null, wrapper);
    }

    private DramaCharacterRecord toRecord(DramaCharacterEntity entity) {
        return new DramaCharacterRecord(
                entity.getId(),
                entity.getSeriesId(),
                entity.getName(),
                entity.getProfile(),
                entity.getAppearance(),
                entity.getCostume(),
                entity.getPersonality(),
                entity.getRelationship(),
                entity.getVisualProfile(),
                entity.getPrimaryReferenceAssetId(),
                entity.getAvatarAssetId(),
                entity.getImageSeed(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

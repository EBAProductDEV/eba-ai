package com.qctv1.ai.drama.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        return Optional.ofNullable(characterMapper.selectById(id)).map(this::toRecord);
    }

    public boolean delete(Long seriesId, Long characterId) {
        Wrapper<DramaCharacterEntity> wrapper = new LambdaQueryWrapper<DramaCharacterEntity>()
                .eq(DramaCharacterEntity::getId, characterId)
                .eq(DramaCharacterEntity::getSeriesId, seriesId);
        return characterMapper.delete(wrapper) > 0;
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
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

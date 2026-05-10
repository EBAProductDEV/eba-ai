package com.qctv1.ai.drama.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qctv1.ai.drama.domain.DramaSeriesRecord;
import com.qctv1.ai.drama.entity.DramaSeriesEntity;
import com.qctv1.ai.drama.mapper.DramaSeriesMapper;
import com.qctv1.ai.drama.support.BusinessException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class DramaSeriesRepository {

    private final DramaSeriesMapper seriesMapper;

    public DramaSeriesRepository(DramaSeriesMapper seriesMapper) {
        this.seriesMapper = seriesMapper;
    }

    public DramaSeriesRecord create(
            Long userId,
            String name,
            String aspectRatio,
            String type,
            String intro,
            String theme,
            String style,
            Integer totalEpisodes,
            Integer episodeDurationMinutes
    ) {
        LocalDateTime now = LocalDateTime.now();
        DramaSeriesEntity entity = new DramaSeriesEntity();
        entity.setUserId(userId);
        entity.setName(name);
        entity.setAspectRatio(normalizeAspectRatio(aspectRatio));
        entity.setType(type);
        entity.setIntro(intro);
        entity.setTheme(theme);
        entity.setStyle(style);
        entity.setStoryStatus("NOT_STARTED");
        entity.setTotalEpisodes(totalEpisodes);
        entity.setEpisodeDurationMinutes(episodeDurationMinutes);
        entity.setStatus("DRAFT");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleted(false);
        int inserted = seriesMapper.insert(entity);
        if (inserted <= 0 || entity.getId() == null) {
            throw new BusinessException(500, "创建短剧项目失败");
        }
        return findByIdAndUserId(entity.getId(), userId)
                .orElseThrow(() -> new BusinessException(500, "创建后未找到短剧项目"));
    }

    public List<DramaSeriesRecord> listByUserId(Long userId) {
        return seriesMapper.selectList(new LambdaQueryWrapper<DramaSeriesEntity>()
                        .eq(DramaSeriesEntity::getUserId, userId)
                        .eq(DramaSeriesEntity::getDeleted, false)
                        .orderByDesc(DramaSeriesEntity::getCreatedAt)
                        .orderByDesc(DramaSeriesEntity::getId))
                .stream()
                .map(this::toRecord)
                .toList();
    }

    public Optional<DramaSeriesRecord> findByIdAndUserId(Long id, Long userId) {
        DramaSeriesEntity entity = seriesMapper.selectOne(new LambdaQueryWrapper<DramaSeriesEntity>()
                .eq(DramaSeriesEntity::getId, id)
                .eq(DramaSeriesEntity::getUserId, userId)
                .eq(DramaSeriesEntity::getDeleted, false)
                .last("LIMIT 1"));
        return Optional.ofNullable(entity).map(this::toRecord);
    }

    public Optional<DramaSeriesRecord> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        DramaSeriesEntity entity = seriesMapper.selectOne(new LambdaQueryWrapper<DramaSeriesEntity>()
                .eq(DramaSeriesEntity::getId, id)
                .eq(DramaSeriesEntity::getDeleted, false)
                .last("LIMIT 1"));
        return Optional.ofNullable(entity).map(this::toRecord);
    }

    public void updateBasic(
            Long id,
            Long userId,
            String name,
            String aspectRatio,
            String type,
            String intro,
            String theme,
            String style,
            Integer totalEpisodes,
            Integer episodeDurationMinutes
    ) {
        seriesMapper.update(new LambdaUpdateWrapper<DramaSeriesEntity>()
                .set(DramaSeriesEntity::getName, name)
                .set(DramaSeriesEntity::getAspectRatio, normalizeAspectRatio(aspectRatio))
                .set(DramaSeriesEntity::getType, type)
                .set(DramaSeriesEntity::getIntro, intro)
                .set(DramaSeriesEntity::getTheme, theme)
                .set(DramaSeriesEntity::getStyle, style)
                .set(DramaSeriesEntity::getTotalEpisodes, totalEpisodes)
                .set(DramaSeriesEntity::getEpisodeDurationMinutes, episodeDurationMinutes)
                .set(DramaSeriesEntity::getUpdatedAt, LocalDateTime.now())
                .eq(DramaSeriesEntity::getId, id)
                .eq(DramaSeriesEntity::getUserId, userId)
                .eq(DramaSeriesEntity::getDeleted, false));
    }

    public void updateStory(Long id, String originalStory, String storySummary, String fullStory, String storyStatus) {
        String projectStatus = "READY".equals(storyStatus) ? "STORY_READY" : "DRAFT";
        seriesMapper.update(new LambdaUpdateWrapper<DramaSeriesEntity>()
                .set(DramaSeriesEntity::getOriginalStory, originalStory)
                .set(DramaSeriesEntity::getStorySummary, storySummary)
                .set(DramaSeriesEntity::getFullStory, fullStory)
                .set(DramaSeriesEntity::getStoryStatus, storyStatus)
                .set(DramaSeriesEntity::getStatus, projectStatus)
                .set(DramaSeriesEntity::getUpdatedAt, LocalDateTime.now())
                .eq(DramaSeriesEntity::getId, id)
                .eq(DramaSeriesEntity::getDeleted, false));
    }

    public void updateTotalEpisodes(Long id, Integer totalEpisodes) {
        seriesMapper.update(new LambdaUpdateWrapper<DramaSeriesEntity>()
                .set(DramaSeriesEntity::getTotalEpisodes, totalEpisodes)
                .set(DramaSeriesEntity::getUpdatedAt, LocalDateTime.now())
                .eq(DramaSeriesEntity::getId, id)
                .eq(DramaSeriesEntity::getDeleted, false));
    }

    public boolean softDelete(Long id, Long userId) {
        int updated = seriesMapper.update(new LambdaUpdateWrapper<DramaSeriesEntity>()
                .set(DramaSeriesEntity::getDeleted, true)
                .set(DramaSeriesEntity::getUpdatedAt, LocalDateTime.now())
                .eq(DramaSeriesEntity::getId, id)
                .eq(DramaSeriesEntity::getUserId, userId)
                .eq(DramaSeriesEntity::getDeleted, false));
        return updated > 0;
    }

    private DramaSeriesRecord toRecord(DramaSeriesEntity entity) {
        return new DramaSeriesRecord(
                entity.getId(),
                entity.getUserId(),
                entity.getName(),
                normalizeAspectRatio(entity.getAspectRatio()),
                entity.getType(),
                entity.getIntro(),
                entity.getTheme(),
                entity.getStyle(),
                entity.getOriginalStory(),
                entity.getStorySummary(),
                entity.getFullStory(),
                entity.getStoryStatus(),
                entity.getTotalEpisodes(),
                entity.getEpisodeDurationMinutes(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeleted()
        );
    }

    private String normalizeAspectRatio(String aspectRatio) {
        if ("LANDSCAPE_16_9".equalsIgnoreCase(aspectRatio) || "16:9".equals(aspectRatio) || "横屏".equals(aspectRatio)) {
            return "LANDSCAPE_16_9";
        }
        return "PORTRAIT_9_16";
    }
}

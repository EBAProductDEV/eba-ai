package com.qctv1.ai.drama.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qctv1.ai.drama.domain.DramaEpisodeRecord;
import com.qctv1.ai.drama.domain.DramaSceneRecord;
import com.qctv1.ai.drama.domain.DramaShotRecord;
import com.qctv1.ai.drama.domain.DramaTaskRecord;
import com.qctv1.ai.drama.entity.DramaEpisodeEntity;
import com.qctv1.ai.drama.entity.DramaSceneEntity;
import com.qctv1.ai.drama.entity.DramaShotEntity;
import com.qctv1.ai.drama.entity.DramaTaskEntity;
import com.qctv1.ai.drama.mapper.DramaEpisodeMapper;
import com.qctv1.ai.drama.mapper.DramaSceneMapper;
import com.qctv1.ai.drama.mapper.DramaShotMapper;
import com.qctv1.ai.drama.mapper.DramaTaskMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class DramaWorkflowRepository {

    private final DramaEpisodeMapper episodeMapper;
    private final DramaSceneMapper sceneMapper;
    private final DramaShotMapper shotMapper;
    private final DramaTaskMapper taskMapper;

    public DramaWorkflowRepository(
            DramaEpisodeMapper episodeMapper,
            DramaSceneMapper sceneMapper,
            DramaShotMapper shotMapper,
            DramaTaskMapper taskMapper
    ) {
        this.episodeMapper = episodeMapper;
        this.sceneMapper = sceneMapper;
        this.shotMapper = shotMapper;
        this.taskMapper = taskMapper;
    }

    public List<DramaEpisodeRecord> listEpisodes(Long seriesId) {
        return episodeMapper.selectList(new LambdaQueryWrapper<DramaEpisodeEntity>()
                        .eq(DramaEpisodeEntity::getSeriesId, seriesId)
                        .orderByAsc(DramaEpisodeEntity::getEpisodeNo)
                        .orderByAsc(DramaEpisodeEntity::getId))
                .stream()
                .map(this::toEpisodeRecord)
                .toList();
    }

    public Optional<DramaEpisodeRecord> findEpisode(Long episodeId) {
        return Optional.ofNullable(episodeMapper.selectById(episodeId)).map(this::toEpisodeRecord);
    }

    public Long upsertEpisode(Long seriesId, Integer episodeNo, String title, String summary, String hook, String cliffhanger) {
        LocalDateTime now = LocalDateTime.now();
        DramaEpisodeEntity existing = episodeMapper.selectOne(new LambdaQueryWrapper<DramaEpisodeEntity>()
                .eq(DramaEpisodeEntity::getSeriesId, seriesId)
                .eq(DramaEpisodeEntity::getEpisodeNo, episodeNo)
                .last("LIMIT 1"));
        if (existing == null) {
            DramaEpisodeEntity entity = new DramaEpisodeEntity();
            entity.setSeriesId(seriesId);
            entity.setEpisodeNo(episodeNo);
            entity.setTitle(title);
            entity.setSummary(summary);
            entity.setHook(hook);
            entity.setCliffhanger(cliffhanger);
            entity.setScript("");
            entity.setStatus("OUTLINE_READY");
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            episodeMapper.insert(entity);
            return entity.getId();
        }
        episodeMapper.update(null, new LambdaUpdateWrapper<DramaEpisodeEntity>()
                .set(DramaEpisodeEntity::getTitle, title)
                .set(DramaEpisodeEntity::getSummary, summary)
                .set(DramaEpisodeEntity::getHook, hook)
                .set(DramaEpisodeEntity::getCliffhanger, cliffhanger)
                .set(DramaEpisodeEntity::getStatus, "OUTLINE_READY")
                .set(DramaEpisodeEntity::getUpdatedAt, now)
                .eq(DramaEpisodeEntity::getId, existing.getId()));
        return existing.getId();
    }

    public void updateEpisodeScript(Long episodeId, String script, String status) {
        episodeMapper.update(null, new LambdaUpdateWrapper<DramaEpisodeEntity>()
                .set(DramaEpisodeEntity::getScript, script)
                .set(DramaEpisodeEntity::getStatus, status)
                .set(DramaEpisodeEntity::getUpdatedAt, LocalDateTime.now())
                .eq(DramaEpisodeEntity::getId, episodeId));
    }

    public List<DramaSceneRecord> listScenesByEpisode(Long episodeId) {
        return sceneMapper.selectList(new LambdaQueryWrapper<DramaSceneEntity>()
                        .eq(DramaSceneEntity::getEpisodeId, episodeId)
                        .orderByAsc(DramaSceneEntity::getId))
                .stream()
                .map(this::toSceneRecord)
                .toList();
    }

    public void createScene(Long seriesId, Long episodeId, String name, String location, String timeOfDay, String atmosphere, String plotPurpose) {
        LocalDateTime now = LocalDateTime.now();
        DramaSceneEntity entity = new DramaSceneEntity();
        entity.setSeriesId(seriesId);
        entity.setEpisodeId(episodeId);
        entity.setName(name);
        entity.setLocation(location);
        entity.setTimeOfDay(timeOfDay);
        entity.setAtmosphere(atmosphere);
        entity.setPlotPurpose(plotPurpose);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        sceneMapper.insert(entity);
    }

    public List<DramaShotRecord> listShotsByEpisode(Long episodeId) {
        return shotMapper.selectList(new LambdaQueryWrapper<DramaShotEntity>()
                        .eq(DramaShotEntity::getEpisodeId, episodeId)
                        .orderByAsc(DramaShotEntity::getShotNo)
                        .orderByAsc(DramaShotEntity::getId))
                .stream()
                .map(this::toShotRecord)
                .toList();
    }

    public Optional<DramaShotRecord> findShot(Long shotId) {
        return Optional.ofNullable(shotMapper.selectById(shotId)).map(this::toShotRecord);
    }

    public void upsertShot(Long episodeId, Long sceneId, Integer shotNo, String shotSize, String action, String dialogue, String imagePrompt, String videoPrompt) {
        LocalDateTime now = LocalDateTime.now();
        DramaShotEntity existing = shotMapper.selectOne(new LambdaQueryWrapper<DramaShotEntity>()
                .eq(DramaShotEntity::getEpisodeId, episodeId)
                .eq(DramaShotEntity::getShotNo, shotNo)
                .last("LIMIT 1"));
        if (existing == null) {
            DramaShotEntity entity = new DramaShotEntity();
            entity.setEpisodeId(episodeId);
            entity.setSceneId(sceneId);
            entity.setShotNo(shotNo);
            entity.setShotSize(shotSize);
            entity.setAction(action);
            entity.setDialogue(dialogue);
            entity.setImagePrompt(imagePrompt);
            entity.setVideoPrompt(videoPrompt);
            entity.setStatus("SHOT_READY");
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            shotMapper.insert(entity);
            return;
        }
        shotMapper.update(null, new LambdaUpdateWrapper<DramaShotEntity>()
                .set(DramaShotEntity::getSceneId, sceneId)
                .set(DramaShotEntity::getShotSize, shotSize)
                .set(DramaShotEntity::getAction, action)
                .set(DramaShotEntity::getDialogue, dialogue)
                .set(DramaShotEntity::getImagePrompt, imagePrompt)
                .set(DramaShotEntity::getVideoPrompt, videoPrompt)
                .set(DramaShotEntity::getStatus, "SHOT_READY")
                .set(DramaShotEntity::getUpdatedAt, now)
                .eq(DramaShotEntity::getId, existing.getId()));
    }

    public Long createTask(Long seriesId, Long episodeId, Long shotId, String taskType, String status, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        DramaTaskEntity entity = new DramaTaskEntity();
        entity.setSeriesId(seriesId == null ? 0L : seriesId);
        entity.setEpisodeId(episodeId);
        entity.setShotId(shotId);
        entity.setTaskType(taskType);
        entity.setStatus(status);
        entity.setErrorMessage(errorMessage);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        taskMapper.insert(entity);
        return entity.getId();
    }

    public List<DramaTaskRecord> listRecentTasks(Long seriesId, int limit) {
        return taskMapper.selectList(new LambdaQueryWrapper<DramaTaskEntity>()
                        .eq(DramaTaskEntity::getSeriesId, seriesId)
                        .orderByDesc(DramaTaskEntity::getCreatedAt)
                        .orderByDesc(DramaTaskEntity::getId)
                        .last(limitSql(limit)))
                .stream()
                .map(this::toTaskRecord)
                .toList();
    }

    public List<DramaTaskRecord> listTasksByEpisode(Long episodeId, int limit) {
        return taskMapper.selectList(new LambdaQueryWrapper<DramaTaskEntity>()
                        .eq(DramaTaskEntity::getEpisodeId, episodeId)
                        .orderByDesc(DramaTaskEntity::getCreatedAt)
                        .orderByDesc(DramaTaskEntity::getId)
                        .last(limitSql(limit)))
                .stream()
                .map(this::toTaskRecord)
                .toList();
    }

    public List<DramaTaskRecord> findTask(Long taskId) {
        return taskMapper.selectList(new LambdaQueryWrapper<DramaTaskEntity>()
                        .eq(DramaTaskEntity::getId, taskId))
                .stream()
                .map(this::toTaskRecord)
                .toList();
    }

    private String limitSql(int limit) {
        return "LIMIT " + Math.max(1, Math.min(limit, 100));
    }

    private DramaEpisodeRecord toEpisodeRecord(DramaEpisodeEntity entity) {
        return new DramaEpisodeRecord(
                entity.getId(),
                entity.getSeriesId(),
                entity.getEpisodeNo(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getHook(),
                entity.getCliffhanger(),
                entity.getScript(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private DramaSceneRecord toSceneRecord(DramaSceneEntity entity) {
        return new DramaSceneRecord(
                entity.getId(),
                entity.getSeriesId(),
                entity.getEpisodeId(),
                entity.getName(),
                entity.getLocation(),
                entity.getTimeOfDay(),
                entity.getAtmosphere(),
                entity.getPlotPurpose(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private DramaShotRecord toShotRecord(DramaShotEntity entity) {
        return new DramaShotRecord(
                entity.getId(),
                entity.getEpisodeId(),
                entity.getSceneId(),
                entity.getShotNo(),
                entity.getShotSize(),
                entity.getAction(),
                entity.getDialogue(),
                entity.getImagePrompt(),
                entity.getVideoPrompt(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private DramaTaskRecord toTaskRecord(DramaTaskEntity entity) {
        return new DramaTaskRecord(
                entity.getId(),
                entity.getSeriesId(),
                entity.getEpisodeId(),
                entity.getShotId(),
                entity.getTaskType(),
                entity.getProviderTaskId(),
                entity.getStatus(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

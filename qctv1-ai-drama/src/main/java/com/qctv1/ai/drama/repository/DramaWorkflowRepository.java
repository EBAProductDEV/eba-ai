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

    public void deleteEpisodeWorkflowBySeries(Long seriesId) {
        List<Long> episodeIds = listEpisodes(seriesId).stream().map(DramaEpisodeRecord::id).toList();
        if (!episodeIds.isEmpty()) {
            shotMapper.delete(new LambdaQueryWrapper<DramaShotEntity>()
                    .in(DramaShotEntity::getEpisodeId, episodeIds));
            episodeMapper.delete(new LambdaQueryWrapper<DramaEpisodeEntity>()
                    .in(DramaEpisodeEntity::getId, episodeIds));
        }
        sceneMapper.delete(new LambdaQueryWrapper<DramaSceneEntity>()
                .eq(DramaSceneEntity::getSeriesId, seriesId)
                .isNotNull(DramaSceneEntity::getEpisodeId));
    }

    public Optional<DramaEpisodeRecord> findEpisode(Long episodeId) {
        if (episodeId == null) {
            return Optional.empty();
        }
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

    public void updateEpisodeNovelContent(Long episodeId, String novelContent) {
        episodeMapper.update(null, new LambdaUpdateWrapper<DramaEpisodeEntity>()
                .set(DramaEpisodeEntity::getNovelContent, novelContent)
                .set(DramaEpisodeEntity::getUpdatedAt, LocalDateTime.now())
                .eq(DramaEpisodeEntity::getId, episodeId));
    }

    public void updateEpisodeStatus(Long episodeId, String status) {
        episodeMapper.update(null, new LambdaUpdateWrapper<DramaEpisodeEntity>()
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

    public Optional<DramaSceneRecord> findScene(Long sceneId) {
        if (sceneId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sceneMapper.selectById(sceneId)).map(this::toSceneRecord);
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

    public void deleteScenesByEpisode(Long episodeId) {
        sceneMapper.delete(new LambdaQueryWrapper<DramaSceneEntity>()
                .eq(DramaSceneEntity::getEpisodeId, episodeId));
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

    public void deleteShotsByEpisode(Long episodeId) {
        shotMapper.delete(new LambdaQueryWrapper<DramaShotEntity>()
                .eq(DramaShotEntity::getEpisodeId, episodeId));
    }

    public Optional<DramaShotRecord> findShot(Long shotId) {
        if (shotId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(shotMapper.selectById(shotId)).map(this::toShotRecord);
    }

    public void upsertShot(
            Long episodeId,
            Long sceneId,
            Integer shotNo,
            String shotSize,
            Integer durationSeconds,
            String cameraMovement,
            String composition,
            String transitionType,
            String soundEffect,
            String musicCue,
            String voiceOver,
            String action,
            String dialogue,
            String imagePrompt,
            String videoPrompt
    ) {
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
            entity.setDurationSeconds(durationSeconds);
            entity.setCameraMovement(cameraMovement);
            entity.setComposition(composition);
            entity.setTransitionType(transitionType);
            entity.setSoundEffect(soundEffect);
            entity.setMusicCue(musicCue);
            entity.setVoiceOver(voiceOver);
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
                .set(DramaShotEntity::getDurationSeconds, durationSeconds)
                .set(DramaShotEntity::getCameraMovement, cameraMovement)
                .set(DramaShotEntity::getComposition, composition)
                .set(DramaShotEntity::getTransitionType, transitionType)
                .set(DramaShotEntity::getSoundEffect, soundEffect)
                .set(DramaShotEntity::getMusicCue, musicCue)
                .set(DramaShotEntity::getVoiceOver, voiceOver)
                .set(DramaShotEntity::getAction, action)
                .set(DramaShotEntity::getDialogue, dialogue)
                .set(DramaShotEntity::getImagePrompt, imagePrompt)
                .set(DramaShotEntity::getVideoPrompt, videoPrompt)
                .set(DramaShotEntity::getStatus, "SHOT_READY")
                .set(DramaShotEntity::getUpdatedAt, now)
                .eq(DramaShotEntity::getId, existing.getId()));
    }

    public void updateShotDialogue(Long shotId, String dialogue) {
        shotMapper.update(null, new LambdaUpdateWrapper<DramaShotEntity>()
                .set(DramaShotEntity::getDialogue, dialogue)
                .set(DramaShotEntity::getUpdatedAt, LocalDateTime.now())
                .eq(DramaShotEntity::getId, shotId));
    }

    public Long createTask(Long seriesId, Long episodeId, Long shotId, String taskType, String status, String errorMessage) {
        return createTask(seriesId, episodeId, shotId, null, taskType, status, errorMessage);
    }

    public Long createTask(Long seriesId, Long episodeId, Long shotId, Long characterId, String taskType, String status, String errorMessage) {
        return createTask(seriesId, episodeId, shotId, characterId, null, taskType, null, status, statusToStageProgress(status), statusToStage(status), errorMessage);
    }

    public Long createTask(
            Long seriesId,
            Long episodeId,
            Long shotId,
            Long characterId,
            Long assetId,
            String taskType,
            String providerTaskId,
            String status,
            Integer progress,
            String stage,
            String errorMessage
    ) {
        return createTask(
                seriesId,
                episodeId,
                shotId,
                characterId,
                assetId,
                null,
                null,
                null,
                null,
                taskType,
                providerTaskId,
                status,
                progress,
                stage,
                errorMessage
        );
    }

    public Long createTask(
            Long seriesId,
            Long episodeId,
            Long shotId,
            Long characterId,
            Long assetId,
            String targetType,
            Long targetId,
            String assetType,
            String assetSubType,
            String taskType,
            String providerTaskId,
            String status,
            Integer progress,
            String stage,
            String errorMessage
    ) {
        LocalDateTime now = LocalDateTime.now();
        DramaTaskEntity entity = new DramaTaskEntity();
        entity.setSeriesId(seriesId == null ? 0L : seriesId);
        entity.setEpisodeId(episodeId);
        entity.setShotId(shotId);
        entity.setCharacterId(characterId);
        entity.setAssetId(assetId);
        entity.setTargetType(targetType);
        entity.setTargetId(targetId);
        entity.setAssetType(assetType);
        entity.setAssetSubType(assetSubType);
        entity.setTaskType(taskType);
        entity.setProviderTaskId(providerTaskId);
        entity.setStatus(status);
        entity.setProgress(progress);
        entity.setStage(stage);
        entity.setErrorMessage(errorMessage);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        taskMapper.insert(entity);
        return entity.getId();
    }

    public void updateTaskProgress(Long taskId, String status, Integer progress, String stage, String errorMessage) {
        taskMapper.update(null, new LambdaUpdateWrapper<DramaTaskEntity>()
                .set(DramaTaskEntity::getStatus, status)
                .set(DramaTaskEntity::getProgress, progress)
                .set(DramaTaskEntity::getStage, stage)
                .set(DramaTaskEntity::getErrorMessage, errorMessage)
                .set(DramaTaskEntity::getUpdatedAt, LocalDateTime.now())
                .eq(DramaTaskEntity::getId, taskId));
    }

    public void completeTask(Long taskId, Long assetId, String providerTaskId, String message) {
        taskMapper.update(null, new LambdaUpdateWrapper<DramaTaskEntity>()
                .set(DramaTaskEntity::getAssetId, assetId)
                .set(providerTaskId != null && !providerTaskId.isBlank(), DramaTaskEntity::getProviderTaskId, providerTaskId)
                .set(DramaTaskEntity::getStatus, "SUCCEEDED")
                .set(DramaTaskEntity::getProgress, 100)
                .set(DramaTaskEntity::getStage, "DONE")
                .set(DramaTaskEntity::getErrorMessage, message)
                .set(DramaTaskEntity::getUpdatedAt, LocalDateTime.now())
                .eq(DramaTaskEntity::getId, taskId));
    }

    public void failTask(Long taskId, String errorMessage) {
        taskMapper.update(null, new LambdaUpdateWrapper<DramaTaskEntity>()
                .set(DramaTaskEntity::getStatus, "FAILED")
                .set(DramaTaskEntity::getProgress, 100)
                .set(DramaTaskEntity::getStage, "FAILED")
                .set(DramaTaskEntity::getErrorMessage, errorMessage)
                .set(DramaTaskEntity::getUpdatedAt, LocalDateTime.now())
                .eq(DramaTaskEntity::getId, taskId));
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

    public List<DramaTaskRecord> listRecentTasks(int limit) {
        return taskMapper.selectList(new LambdaQueryWrapper<DramaTaskEntity>()
                        .orderByDesc(DramaTaskEntity::getUpdatedAt)
                        .orderByDesc(DramaTaskEntity::getId)
                        .last(limitSql(limit)))
                .stream()
                .map(this::toTaskRecord)
                .toList();
    }

    public long countActiveTasks() {
        return taskMapper.selectCount(new LambdaQueryWrapper<DramaTaskEntity>()
                .in(DramaTaskEntity::getStatus, List.of("PENDING", "RUNNING")));
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

    public boolean existsNonFailedTaskByShotAndTaskType(Long shotId, String taskType) {
        if (shotId == null || taskType == null || taskType.isBlank()) {
            return false;
        }
        return taskMapper.selectCount(new LambdaQueryWrapper<DramaTaskEntity>()
                .eq(DramaTaskEntity::getShotId, shotId)
                .eq(DramaTaskEntity::getTaskType, taskType)
                // 图片/视频素材删除后，历史 SUCCEEDED 任务不能继续阻止重新生成。
                // 这里真正需要拦截的只有仍在排队或执行中的任务，避免重复提交并发任务。
                .in(DramaTaskEntity::getStatus, List.of("PENDING", "RUNNING"))) > 0;
    }

    public boolean existsNonFailedTaskByTargetAndTaskType(String targetType, Long targetId, String taskType) {
        if (targetType == null || targetType.isBlank() || targetId == null || taskType == null || taskType.isBlank()) {
            return false;
        }
        return taskMapper.selectCount(new LambdaQueryWrapper<DramaTaskEntity>()
                .eq(DramaTaskEntity::getTargetType, targetType)
                .eq(DramaTaskEntity::getTargetId, targetId)
                .eq(DramaTaskEntity::getTaskType, taskType)
                // 素材是否存在由 ai_drama_asset 判断；任务表只判断是否有正在执行的同类任务。
                // 否则删除图片后，旧的 SUCCEEDED 任务会导致前端看起来“点击生成没反应”。
                .in(DramaTaskEntity::getStatus, List.of("PENDING", "RUNNING"))) > 0;
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

    private Integer statusToStageProgress(String status) {
        return "SUCCEEDED".equals(status) || "FAILED".equals(status) ? 100 : 0;
    }

    private String statusToStage(String status) {
        if ("SUCCEEDED".equals(status)) {
            return "DONE";
        }
        if ("FAILED".equals(status)) {
            return "FAILED";
        }
        if ("RUNNING".equals(status)) {
            return "GENERATING";
        }
        return "QUEUED";
    }

    private DramaEpisodeRecord toEpisodeRecord(DramaEpisodeEntity entity) {
        return new DramaEpisodeRecord(
                entity.getId(),
                entity.getSeriesId(),
                entity.getEpisodeNo(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getNovelContent(),
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
                entity.getDurationSeconds(),
                entity.getCameraMovement(),
                entity.getComposition(),
                entity.getTransitionType(),
                entity.getSoundEffect(),
                entity.getMusicCue(),
                entity.getVoiceOver(),
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
                entity.getCharacterId(),
                entity.getAssetId(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getAssetType(),
                entity.getAssetSubType(),
                entity.getTaskType(),
                entity.getProviderTaskId(),
                entity.getStatus(),
                entity.getProgress() == null ? statusToStageProgress(entity.getStatus()) : entity.getProgress(),
                entity.getStage() == null ? statusToStage(entity.getStatus()) : entity.getStage(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

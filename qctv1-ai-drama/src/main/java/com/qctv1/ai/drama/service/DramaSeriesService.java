package com.qctv1.ai.drama.service;

import com.qctv1.ai.drama.domain.DramaAssetRecord;
import com.qctv1.ai.drama.domain.DramaCharacterRecord;
import com.qctv1.ai.drama.domain.DramaEpisodeRecord;
import com.qctv1.ai.drama.domain.DramaSceneRecord;
import com.qctv1.ai.drama.domain.DramaSeriesRecord;
import com.qctv1.ai.drama.domain.DramaTaskRecord;
import com.qctv1.ai.drama.dto.DramaCharacterSaveRequest;
import com.qctv1.ai.drama.dto.DramaSeriesCreateRequest;
import com.qctv1.ai.drama.dto.DramaStorySaveRequest;
import com.qctv1.ai.drama.repository.DramaAssetRepository;
import com.qctv1.ai.drama.repository.DramaCharacterRepository;
import com.qctv1.ai.drama.repository.DramaSeriesRepository;
import com.qctv1.ai.drama.repository.DramaWorkflowRepository;
import com.qctv1.ai.drama.support.BusinessException;
import com.qctv1.ai.drama.vo.DramaAssetVo;
import com.qctv1.ai.drama.vo.DramaCharacterVo;
import com.qctv1.ai.drama.vo.DramaEpisodeDetailVo;
import com.qctv1.ai.drama.vo.DramaEpisodeVo;
import com.qctv1.ai.drama.vo.DramaSceneVo;
import com.qctv1.ai.drama.vo.DramaSeriesDetailVo;
import com.qctv1.ai.drama.vo.DramaSeriesSummaryVo;
import com.qctv1.ai.drama.vo.DramaTaskVo;
import com.qctv1.ai.drama.vo.DramaShotVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Service
public class DramaSeriesService {

    private static final Long DEFAULT_USER_ID = 1L;

    private final DramaSeriesRepository seriesRepository;
    private final DramaWorkflowRepository workflowRepository;
    private final DramaAssetRepository assetRepository;
    private final DramaCharacterRepository characterRepository;
    private final DramaAssetService assetService;

    public DramaSeriesService(
            DramaSeriesRepository seriesRepository,
            DramaWorkflowRepository workflowRepository,
            DramaAssetRepository assetRepository,
            DramaCharacterRepository characterRepository,
            DramaAssetService assetService
    ) {
        this.seriesRepository = seriesRepository;
        this.workflowRepository = workflowRepository;
        this.assetRepository = assetRepository;
        this.characterRepository = characterRepository;
        this.assetService = assetService;
    }

    @Transactional
    public DramaSeriesSummaryVo create(DramaSeriesCreateRequest request) {
        DramaSeriesRecord record = seriesRepository.create(
                DEFAULT_USER_ID,
                request.name(),
                request.type(),
                request.intro(),
                request.theme(),
                request.style(),
                request.totalEpisodes(),
                request.episodeDurationMinutes()
        );
        try {
            assetService.ensureSeriesRoot(record.id());
        } catch (IOException ex) {
            throw new BusinessException(500, "创建素材目录失败：" + ex.getMessage());
        }
        return toSummary(record);
    }

    public List<DramaSeriesSummaryVo> list() {
        return seriesRepository.listByUserId(DEFAULT_USER_ID).stream().map(this::toSummary).toList();
    }

    public DramaSeriesDetailVo detail(Long id) {
        DramaSeriesRecord series = seriesRepository.findByIdAndUserId(id, DEFAULT_USER_ID)
                .orElseThrow(() -> new BusinessException(404, "短剧项目不存在"));
        List<DramaEpisodeVo> episodes = workflowRepository.listEpisodes(id).stream().map(this::toEpisodeVo).toList();
        List<DramaCharacterVo> characters = characterRepository.listBySeries(id).stream().map(this::toCharacterVo).toList();
        List<DramaAssetVo> assets = assetRepository.listRecentBySeries(id, 12).stream().map(this::toAssetVo).toList();
        List<DramaTaskVo> tasks = workflowRepository.listRecentTasks(id, 12).stream().map(this::toTaskVo).toList();
        return new DramaSeriesDetailVo(
                series.id(), series.name(), series.type(), series.intro(), series.theme(), series.style(),
                series.originalStory(), series.storySummary(), series.fullStory(), series.storyStatus(),
                series.totalEpisodes(), series.episodeDurationMinutes(), series.status(), series.createdAt(),
                characters, episodes, assets, tasks
        );
    }


    @Transactional
    public DramaSeriesDetailVo saveStory(Long id, DramaStorySaveRequest request) {
        ensureSeriesExists(id);
        String originalStory = request == null ? "" : nullToEmpty(request.originalStory()).trim();
        String storySummary = request == null ? "" : nullToEmpty(request.storySummary()).trim();
        String fullStory = request == null ? "" : nullToEmpty(request.fullStory()).trim();
        String storyStatus = fullStory.isBlank() ? "NOT_STARTED" : "READY";
        seriesRepository.updateStory(id, originalStory, storySummary, fullStory, storyStatus);
        return detail(id);
    }

    public List<DramaCharacterVo> listCharacters(Long seriesId) {
        ensureSeriesExists(seriesId);
        return characterRepository.listBySeries(seriesId).stream().map(this::toCharacterVo).toList();
    }

    @Transactional
    public DramaCharacterVo createCharacter(Long seriesId, DramaCharacterSaveRequest request) {
        ensureSeriesExists(seriesId);
        DramaCharacterRecord record = characterRepository.create(
                seriesId,
                request.name(),
                request.profile(),
                request.appearance(),
                request.costume(),
                request.personality(),
                request.relationship()
        );
        return toCharacterVo(record);
    }

    @Transactional
    public void deleteCharacter(Long seriesId, Long characterId) {
        ensureSeriesExists(seriesId);
        if (!characterRepository.delete(seriesId, characterId)) {
            throw new BusinessException(404, "角色不存在或不属于该短剧项目");
        }
    }

    public DramaEpisodeDetailVo episodeDetail(Long episodeId) {
        DramaEpisodeRecord episode = workflowRepository.findEpisode(episodeId)
                .orElseThrow(() -> new BusinessException(404, "分集不存在"));
        List<DramaSceneVo> scenes = workflowRepository.listScenesByEpisode(episodeId).stream().map(this::toSceneVo).toList();
        List<DramaShotVo> shots = workflowRepository.listShotsByEpisode(episodeId).stream().map(this::toShotVo).toList();
        List<DramaAssetVo> assets = assetRepository.listByEpisode(episodeId, 30).stream().map(this::toAssetVo).toList();
        List<DramaTaskVo> tasks = workflowRepository.listTasksByEpisode(episodeId, 20).stream().map(this::toTaskVo).toList();
        return new DramaEpisodeDetailVo(episode.seriesId(), toEpisodeVo(episode), scenes, shots, assets, tasks);
    }

    @Transactional
    public void delete(Long id) {
        boolean deleted = seriesRepository.softDelete(id, DEFAULT_USER_ID);
        if (!deleted) {
            throw new BusinessException(404, "短剧项目不存在或已删除");
        }
    }

    private DramaSeriesSummaryVo toSummary(DramaSeriesRecord record) {
        return new DramaSeriesSummaryVo(
                record.id(), record.name(), record.type(), record.intro(), record.style(),
                record.totalEpisodes(), record.episodeDurationMinutes(), record.status(), record.createdAt()
        );
    }

    private DramaEpisodeVo toEpisodeVo(DramaEpisodeRecord record) {
        return new DramaEpisodeVo(
                record.id(), record.episodeNo(), record.title(), record.summary(), record.hook(),
                record.cliffhanger(), record.script(), record.status()
        );
    }

    private DramaSceneVo toSceneVo(DramaSceneRecord record) {
        return new DramaSceneVo(record.id(), record.name(), record.location(), record.timeOfDay(), record.atmosphere(), record.plotPurpose());
    }

    private DramaCharacterVo toCharacterVo(DramaCharacterRecord record) {
        return new DramaCharacterVo(
                record.id(), record.seriesId(), record.name(), record.profile(), record.appearance(),
                record.costume(), record.personality(), record.relationship(), record.createdAt(), record.updatedAt()
        );
    }

    private DramaShotVo toShotVo(com.qctv1.ai.drama.domain.DramaShotRecord record) {
        return new DramaShotVo(
                record.id(), record.episodeId(), record.sceneId(), record.shotNo(), record.shotSize(), record.action(),
                record.dialogue(), record.imagePrompt(), record.videoPrompt(), record.status()
        );
    }

    private DramaAssetVo toAssetVo(DramaAssetRecord record) {
        return new DramaAssetVo(record.id(), record.assetType(), record.fileName(), record.contentType(), record.accessUrl(), record.createdAt());
    }

    private DramaTaskVo toTaskVo(DramaTaskRecord record) {
        return new DramaTaskVo(record.id(), record.taskType(), record.providerTaskId(), record.status(), record.errorMessage(), record.createdAt(), record.updatedAt());
    }

    private void ensureSeriesExists(Long seriesId) {
        seriesRepository.findByIdAndUserId(seriesId, DEFAULT_USER_ID)
                .orElseThrow(() -> new BusinessException(404, "短剧项目不存在"));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

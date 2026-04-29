package com.qctv1.ai.drama.service;

import com.qctv1.ai.drama.domain.DramaEpisodeRecord;
import com.qctv1.ai.drama.domain.DramaSceneRecord;
import com.qctv1.ai.drama.domain.DramaShotRecord;
import com.qctv1.ai.drama.domain.DramaSeriesRecord;
import com.qctv1.ai.drama.dto.GenerateRequest;
import com.qctv1.ai.drama.provider.ImageGenerationClient;
import com.qctv1.ai.drama.provider.TextGenerationClient;
import com.qctv1.ai.drama.provider.VideoGenerationClient;
import com.qctv1.ai.drama.repository.DramaSeriesRepository;
import com.qctv1.ai.drama.repository.DramaWorkflowRepository;
import com.qctv1.ai.drama.support.BusinessException;
import com.qctv1.ai.drama.vo.DramaTaskVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DramaGenerationService {

    private final DramaSeriesRepository seriesRepository;
    private final DramaWorkflowRepository workflowRepository;
    private final TextGenerationClient textGenerationClient;
    private final ImageGenerationClient imageGenerationClient;
    private final VideoGenerationClient videoGenerationClient;

    public DramaGenerationService(
            DramaSeriesRepository seriesRepository,
            DramaWorkflowRepository workflowRepository,
            TextGenerationClient textGenerationClient,
            ImageGenerationClient imageGenerationClient,
            VideoGenerationClient videoGenerationClient
    ) {
        this.seriesRepository = seriesRepository;
        this.workflowRepository = workflowRepository;
        this.textGenerationClient = textGenerationClient;
        this.imageGenerationClient = imageGenerationClient;
        this.videoGenerationClient = videoGenerationClient;
    }

    @Transactional
    public DramaTaskVo generateStory(Long seriesId, GenerateRequest request) {
        DramaSeriesRecord series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException(404, "短剧项目不存在"));
        String instruction = request == null ? "" : nullToEmpty(request.instruction());
        String storySummary = "《" + series.name() + "》总故事占位稿：围绕「" + series.type() + "」类型，建立主角目标、核心冲突、连续反转和最终情绪兑现。";
        String fullStory = "【整部故事总纲】\n"
                + "项目名称：" + series.name() + "\n"
                + "类型：" + series.type() + "\n"
                + "风格：" + nullToDefault(series.style(), "快节奏、强冲突、强反转") + "\n"
                + "用户补充要求：" + nullToDefault(instruction, "暂无") + "\n\n"
                + "第一幕：主角处于被压制或误解的位置，3 秒内给出强冲突，建立观众代入。\n"
                + "第二幕：主角通过关键能力或隐藏身份开始反击，每一集保留一个明确钩子。\n"
                + "第三幕：反派逐步加码，人物关系反转，主角付出代价但获得关键证据。\n"
                + "第四幕：终局反转，主角完成情绪释放，关系线和事业线同时收束。\n\n"
                + "【生产说明】这是 v1 占位稿，后续接入文本模型后会替换为真实故事圣经，包括人物小传、世界观、商业爆点和禁用设定。";
        seriesRepository.updateStory(seriesId, series.originalStory(), storySummary, fullStory, "READY");
        Long taskId = workflowRepository.createTask(seriesId, null, null, "STORY_GENERATE", "SUCCEEDED", "已生成整部故事占位稿");
        return task(taskId);
    }

    @Transactional
    public DramaTaskVo generateEpisodes(Long seriesId, GenerateRequest request) {
        DramaSeriesRecord series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException(404, "短剧项目不存在"));
        if (series.fullStory() == null || series.fullStory().isBlank()) {
            throw new BusinessException(400, "请先生成整部故事，再拆分分集大纲");
        }
        int count = request == null || request.count() == null || request.count() <= 0 ? series.totalEpisodes() : request.count();
        for (int i = 1; i <= count; i++) {
            workflowRepository.upsertEpisode(
                    seriesId,
                    i,
                    "第" + i + "集：冲突升级占位标题",
                    "基于整部故事总纲拆出的第 " + i + " 集占位大纲：本集需要完成一个明确冲突、一次关系推进和一个结尾钩子。",
                    "开场 3 秒给出人物冲突或身份误解",
                    "结尾留下反转悬念，引导进入下一集"
            );
        }
        Long taskId = workflowRepository.createTask(seriesId, null, null, "EPISODES_GENERATE", "SUCCEEDED", "已根据整部故事生成分集大纲占位内容");
        return task(taskId);
    }

    @Transactional
    public DramaTaskVo generateScript(Long episodeId, GenerateRequest request) {
        DramaEpisodeRecord episode = workflowRepository.findEpisode(episodeId)
                .orElseThrow(() -> new BusinessException(404, "分集不存在"));
        String script = "【第" + episode.episodeNo() + "集单集剧本占位稿】\n"
                + "场景一：用强冲突开场，主角被误解或被压制。\n"
                + "对白：反派提出挑衅，主角隐忍回应。\n"
                + "场景二：主角发现关键线索，冲突升级。\n"
                + "对白：配角给出误导信息，制造反转空间。\n"
                + "场景三：结尾钩子，主角身份或证据露出一角。\n"
                + "说明：这是 v1 占位脚本，后续接入文本模型后会生成完整对白、动作和情绪节奏。";
        workflowRepository.updateEpisodeScript(episodeId, script, "SCRIPT_READY");
        ensureDefaultScenes(episode);
        Long taskId = workflowRepository.createTask(episode.seriesId(), episodeId, null, "SCRIPT_GENERATE", "SUCCEEDED", "已生成单集剧本和场景占位内容");
        return task(taskId);
    }

    @Transactional
    public DramaTaskVo generateShots(Long episodeId, GenerateRequest request) {
        DramaEpisodeRecord episode = workflowRepository.findEpisode(episodeId)
                .orElseThrow(() -> new BusinessException(404, "分集不存在"));
        ensureDefaultScenes(episode);
        List<DramaSceneRecord> scenes = workflowRepository.listScenesByEpisode(episodeId);
        int shotNo = 1;
        for (DramaSceneRecord scene : scenes) {
            workflowRepository.upsertShot(
                    episodeId,
                    scene.id(),
                    shotNo++,
                    "近景",
                    scene.name() + "中，主角进入画面，表情压抑但克制",
                    "你们真的以为我没有证据吗？",
                    "竖屏短剧，" + scene.name() + "，近景，人物情绪压抑，背景略虚化",
                    "镜头缓慢推进，主角抬眼，情绪从隐忍转为坚定"
            );
            workflowRepository.upsertShot(
                    episodeId,
                    scene.id(),
                    shotNo++,
                    "中景",
                    scene.name() + "中，反派靠近主角，制造压迫感",
                    "证据？你拿得出来吗？",
                    "竖屏短剧，" + scene.name() + "，中景，对峙构图，强冲突氛围",
                    "反派前压，主角后退半步，镜头轻微晃动增强压迫"
            );
        }
        Long taskId = workflowRepository.createTask(episode.seriesId(), episodeId, null, "SHOTS_GENERATE", "SUCCEEDED", "已按场景生成镜头拆分占位内容");
        return task(taskId);
    }

    public DramaTaskVo generateCharacterImage(Long characterId, GenerateRequest request) {
        String providerTaskId = imageGenerationClient.submitImageTask(request == null ? "" : request.instruction());
        Long taskId = workflowRepository.createTask(0L, null, null, "CHARACTER_IMAGE_GENERATE", "PENDING", "providerTaskId=" + providerTaskId);
        return task(taskId);
    }

    public DramaTaskVo generateSceneImage(Long sceneId, GenerateRequest request) {
        String providerTaskId = imageGenerationClient.submitImageTask(request == null ? "" : request.instruction());
        Long taskId = workflowRepository.createTask(0L, null, null, "SCENE_IMAGE_GENERATE", "PENDING", "providerTaskId=" + providerTaskId);
        return task(taskId);
    }

    public DramaTaskVo generateShotImage(Long shotId, GenerateRequest request) {
        DramaShotRecord shot = workflowRepository.findShot(shotId)
                .orElseThrow(() -> new BusinessException(404, "镜头不存在"));
        DramaEpisodeRecord episode = workflowRepository.findEpisode(shot.episodeId())
                .orElseThrow(() -> new BusinessException(404, "镜头所属分集不存在"));
        String providerTaskId = imageGenerationClient.submitImageTask(request == null ? "" : request.instruction());
        Long taskId = workflowRepository.createTask(episode.seriesId(), shot.episodeId(), shotId, "SHOT_IMAGE_GENERATE", "PENDING", "providerTaskId=" + providerTaskId);
        return task(taskId);
    }

    public DramaTaskVo generateShotVideo(Long shotId, GenerateRequest request) {
        DramaShotRecord shot = workflowRepository.findShot(shotId)
                .orElseThrow(() -> new BusinessException(404, "镜头不存在"));
        DramaEpisodeRecord episode = workflowRepository.findEpisode(shot.episodeId())
                .orElseThrow(() -> new BusinessException(404, "镜头所属分集不存在"));
        String providerTaskId = videoGenerationClient.submitVideoTask(request == null ? "" : request.instruction());
        Long taskId = workflowRepository.createTask(episode.seriesId(), shot.episodeId(), shotId, "SHOT_VIDEO_GENERATE", "PENDING", "providerTaskId=" + providerTaskId);
        return task(taskId);
    }

    public DramaTaskVo task(Long taskId) {
        return workflowRepository.findTask(taskId).stream()
                .findFirst()
                .map(record -> new DramaTaskVo(record.id(), record.taskType(), record.providerTaskId(), record.status(), record.errorMessage(), record.createdAt(), record.updatedAt()))
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));
    }

    private void ensureDefaultScenes(DramaEpisodeRecord episode) {
        if (!workflowRepository.listScenesByEpisode(episode.id()).isEmpty()) {
            return;
        }
        workflowRepository.createScene(episode.seriesId(), episode.id(), "冲突开场场景", "主角所在核心空间", "白天", "紧张、压迫", "建立本集主要矛盾和人物情绪");
        workflowRepository.createScene(episode.seriesId(), episode.id(), "线索推进场景", "转场空间", "傍晚", "疑惑、压抑", "推动主角发现关键线索");
        workflowRepository.createScene(episode.seriesId(), episode.id(), "结尾反转场景", "高情绪场所", "夜晚", "爆发、悬念", "制造本集结尾钩子");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String nullToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}

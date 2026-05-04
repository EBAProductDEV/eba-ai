package com.qctv1.ai.drama.service;

import com.qctv1.ai.drama.config.DramaProperties;
import com.qctv1.ai.drama.domain.DramaAssetRecord;
import com.qctv1.ai.drama.domain.DramaCharacterRecord;
import com.qctv1.ai.drama.domain.DramaEpisodeRecord;
import com.qctv1.ai.drama.domain.DramaImageGenerationContext;
import com.qctv1.ai.drama.domain.DramaSceneRecord;
import com.qctv1.ai.drama.domain.DramaSeriesRecord;
import com.qctv1.ai.drama.domain.DramaShotRecord;
import com.qctv1.ai.drama.dto.DramaEpisodeScriptSaveRequest;
import com.qctv1.ai.drama.dto.DramaEpisodeStepCompleteRequest;
import com.qctv1.ai.drama.dto.DramaEpisodeStepRollbackRequest;
import com.qctv1.ai.drama.dto.DramaStoryAssistantChatRequest;
import com.qctv1.ai.drama.dto.DramaStoryBriefRequest;
import com.qctv1.ai.drama.dto.DramaStoryGenerateRequest;
import com.qctv1.ai.drama.dto.GenerateRequest;
import com.qctv1.ai.drama.provider.ImageGenerationClient;
import com.qctv1.ai.drama.provider.TextGenerationClient;
import com.qctv1.ai.drama.repository.DramaAssetRepository;
import com.qctv1.ai.drama.repository.DramaCharacterRepository;
import com.qctv1.ai.drama.repository.DramaSeriesRepository;
import com.qctv1.ai.drama.repository.DramaWorkflowRepository;
import com.qctv1.ai.drama.support.BusinessException;
import com.qctv1.ai.drama.vo.DramaSeriesDetailVo;
import com.qctv1.ai.drama.vo.DramaEpisodeDetailVo;
import com.qctv1.ai.drama.vo.DramaStoryAssistantChatVo;
import com.qctv1.ai.drama.vo.DramaStoryBriefVo;
import com.qctv1.ai.drama.vo.DramaTaskVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DramaGenerationService {

    private final DramaSeriesRepository seriesRepository;
    private final DramaWorkflowRepository workflowRepository;
    private final DramaAssetRepository assetRepository;
    private final DramaCharacterRepository characterRepository;
    private final TextGenerationClient textGenerationClient;
    private final ImageGenerationClient imageGenerationClient;
    private final DramaProperties properties;
    private final DramaSeriesService seriesService;
    private final DramaEpisodeBreakdownMemoryService episodeBreakdownMemoryService;
    private final DramaAssetService assetService;
    private final DramaImageGenerationService imageGenerationService;
    private final DramaVideoGenerationService videoGenerationService;

    public DramaGenerationService(
            DramaSeriesRepository seriesRepository,
            DramaWorkflowRepository workflowRepository,
            DramaAssetRepository assetRepository,
            DramaCharacterRepository characterRepository,
            TextGenerationClient textGenerationClient,
            ImageGenerationClient imageGenerationClient,
            DramaProperties properties,
            DramaSeriesService seriesService,
            DramaEpisodeBreakdownMemoryService episodeBreakdownMemoryService,
            DramaAssetService assetService,
            DramaImageGenerationService imageGenerationService,
            DramaVideoGenerationService videoGenerationService
    ) {
        this.seriesRepository = seriesRepository;
        this.workflowRepository = workflowRepository;
        this.assetRepository = assetRepository;
        this.characterRepository = characterRepository;
        this.textGenerationClient = textGenerationClient;
        this.imageGenerationClient = imageGenerationClient;
        this.properties = properties;
        this.seriesService = seriesService;
        this.episodeBreakdownMemoryService = episodeBreakdownMemoryService;
        this.assetService = assetService;
        this.imageGenerationService = imageGenerationService;
        this.videoGenerationService = videoGenerationService;
    }

    public DramaStoryBriefVo prepareStoryBrief(Long seriesId, DramaStoryBriefRequest request) {
        DramaSeriesRecord series = findSeries(seriesId);
        ensureStoryNotExists(series);
        String requirement = request == null ? "" : nullToEmpty(request.requirement()).trim();
        String modelBrief = textGenerationClient.generate(buildStoryBriefPrompt(series, requirement));
        String storyBrief = isModelFallback(modelBrief) ? buildFallbackStoryBrief(series, requirement) : modelBrief.trim();
        return new DramaStoryBriefVo(storyBrief, properties.getText().isReady());
    }

    @Transactional
    public DramaSeriesDetailVo generateStoryContent(Long seriesId, DramaStoryGenerateRequest request) {
        DramaSeriesRecord series = findSeries(seriesId);
        ensureStoryNotExists(series);
        String storyBrief = request == null ? "" : nullToEmpty(request.storyBrief()).trim();
        String requirement = request == null ? "" : nullToEmpty(request.requirement()).trim();
        String generatedText = textGenerationClient.generate(buildStoryGenerationPrompt(series, storyBrief, requirement));
        StoryContent content = parseStoryContent(generatedText);
        if (content.hasMissingUserVisibleContent()) {
            content = buildFallbackStoryContent(series, storyBrief, requirement);
        }
        seriesRepository.updateStory(seriesId, content.originalStory(), content.storySummary(), null, "READY");
        workflowRepository.createTask(seriesId, null, null, "STORY_AI_GENERATE", "SUCCEEDED", "已生成故事原文和故事摘要，完整大纲将在分集阶段生成");
        return seriesService.detail(seriesId);
    }

    /**
     * 故事原文 AI 修改：返回修改后的全文，前端直接刷新故事原文编辑框。
     */
    public DramaStoryAssistantChatVo chatWithStoryAssistant(Long seriesId, DramaStoryAssistantChatRequest request) {
        DramaSeriesRecord series = findSeries(seriesId);
        String question = request == null ? "" : nullToEmpty(request.question()).trim();
        String currentStory = request == null || !hasText(request.originalStory()) ? nullToDefault(series.originalStory(), "") : request.originalStory();
        String storySummary = request == null || !hasText(request.storySummary()) ? nullToDefault(series.storySummary(), "") : request.storySummary();

        if (question.isBlank()) {
            return new DramaStoryAssistantChatVo("请先输入你想让 AI 修改或分析的故事要求。", false, properties.getText().isReady(), currentStory, "reject");
        }
        boolean storyEditCommand = isStoryEditCommand(question);
        boolean storyReadOnlyQuestion = isStoryReadOnlyQuestion(question);
        if (isClearlyOutOfStoryScope(question)) {
            return new DramaStoryAssistantChatVo("我只能处理短剧故事、小说原文、剧情结构、角色塑造、冲突设计、爽点反转和文风润色相关内容，其它问题不处理。", false, properties.getText().isReady(), currentStory, "reject");
        }
        if (!hasText(currentStory)) {
            return new DramaStoryAssistantChatVo("故事原文为空，请先填写故事原文后再让 AI 修改。", false, properties.getText().isReady(), currentStory, "reject");
        }
        boolean planMode = request != null && "plan".equalsIgnoreCase(nullToEmpty(request.mode()));
        boolean planConfirmed = request != null && Boolean.TRUE.equals(request.planConfirmed());
        if (planMode && !planConfirmed) {
            String modelPlan = textGenerationClient.generate(buildStoryPlanPrompt(series, storySummary, currentStory, question, request.history()));
            if (isModelFallback(modelPlan)) {
                return new DramaStoryAssistantChatVo("当前模型未就绪或调用失败，暂时无法生成修改方案。", false, properties.getText().isReady(), null, "reject");
            }
            return new DramaStoryAssistantChatVo(modelPlan.trim(), true, properties.getText().isReady(), null, "plan");
        }
        if (planMode && planConfirmed) {
            String planContent = request == null ? "" : nullToEmpty(request.planContent()).trim();
            question = hasText(planContent) ? question + "\n\n用户已确认的修改方案：\n" + planContent : question;
            storyEditCommand = true;
        }
        if (storyReadOnlyQuestion && !storyEditCommand) {
            String modelAnswer = textGenerationClient.generate(buildStoryAnswerPrompt(series, storySummary, currentStory, question, request == null ? List.of() : request.history()));
            if (isModelFallback(modelAnswer)) {
                return new DramaStoryAssistantChatVo("当前模型未就绪或调用失败，暂时无法总结或分析故事原文。", false, properties.getText().isReady(), null, "reject");
            }
            return new DramaStoryAssistantChatVo(modelAnswer.trim(), true, properties.getText().isReady(), null, "answer");
        }

        String modelResult = textGenerationClient.generate(buildStoryRewritePrompt(series, storySummary, currentStory, question, request == null ? List.of() : request.history()));
        StoryRewriteResult rewriteResult = parseStoryRewriteResult(modelResult);
        if (isModelFallback(modelResult) || !hasText(rewriteResult.updatedStory())) {
            return new DramaStoryAssistantChatVo("当前模型未就绪或没有返回可用的故事正文，故事原文未修改。", false, properties.getText().isReady(), currentStory, "reject");
        }
        String changeSummary = hasText(rewriteResult.changeSummary()) ? rewriteResult.changeSummary() : "已按你的要求修改故事原文。";
        return new DramaStoryAssistantChatVo(changeSummary, true, properties.getText().isReady(), rewriteResult.updatedStory(), "rewrite");
    }

    @Transactional
    public DramaTaskVo generateStory(Long seriesId, GenerateRequest request) {
        DramaSeriesRecord series = findSeries(seriesId);
        ensureStoryNotExists(series);
        String instruction = request == null ? "" : nullToEmpty(request.instruction());
        StoryContent content = buildFallbackStoryContent(series, buildFallbackStoryBrief(series, instruction), instruction);
        seriesRepository.updateStory(seriesId, content.originalStory(), content.storySummary(), null, "READY");
        Long taskId = workflowRepository.createTask(seriesId, null, null, "STORY_GENERATE", "SUCCEEDED", "已生成故事原文和故事摘要占位稿");
        return task(taskId);
    }

    @Transactional
    public DramaTaskVo generateEpisodes(Long seriesId, GenerateRequest request) {
        DramaSeriesRecord series = findSeries(seriesId);
        List<DramaEpisodeRecord> existingEpisodes = workflowRepository.listEpisodes(seriesId);
        String regenerateReason = request == null ? "" : nullToEmpty(request.instruction()).trim();
        if (!existingEpisodes.isEmpty() && !hasText(regenerateReason)) {
            throw new BusinessException(400, "分集数据已存在，请填写重新拆分原因后再生成");
        }
        Long memoryId = hasText(regenerateReason)
                ? episodeBreakdownMemoryService.recordRunning(seriesId, regenerateReason)
                : null;
        try {
            return doGenerateEpisodes(series, existingEpisodes, request, regenerateReason, memoryId);
        } catch (RuntimeException ex) {
            episodeBreakdownMemoryService.markFailed(memoryId, ex.getMessage());
            throw ex;
        }
    }

    private DramaTaskVo doGenerateEpisodes(
            DramaSeriesRecord series,
            List<DramaEpisodeRecord> existingEpisodes,
            GenerateRequest request,
            String regenerateReason,
            Long memoryId
    ) {
        Long seriesId = series.id();
        if (!hasText(series.originalStory())) {
            throw new BusinessException(400, "请先生成或填写故事原文，再拆分分集");
        }
        if (!properties.getText().isReady()) {
            throw new BusinessException(400, "分集拆解失败：文本模型未配置，无法进行专业分集拆解");
        }
        List<String> historicalRequirements = episodeBreakdownMemoryService.listRecentRequirements(seriesId, 10);
        String modelResult = textGenerationClient.generate(buildEpisodeBreakdownPrompt(series, request, historicalRequirements));
        if (isModelFallback(modelResult)) {
            throw new BusinessException(500, "分集拆解失败：文本模型调用失败或未返回有效内容");
        }
        EpisodeBreakdown breakdown = parseEpisodeBreakdown(modelResult);
        if (!hasText(breakdown.storyOutline())) {
            throw new BusinessException(500, "分集拆解失败：模型没有返回内部故事大纲");
        }
        if (breakdown.episodes().isEmpty()) {
            throw new BusinessException(500, "分集拆解失败：模型没有返回可用的分集列表");
        }
        int maxEpisodes = resolveEpisodeUpperLimit(series, request);
        if (breakdown.episodes().size() > maxEpisodes) {
            throw new BusinessException(500, "分集拆解失败：模型返回集数超过项目建议上限，请缩短故事或调整项目总集数后重试");
        }
        if (!existingEpisodes.isEmpty()) {
            assetRepository.deleteEpisodeAssetsBySeries(seriesId);
            workflowRepository.deleteEpisodeWorkflowBySeries(seriesId);
        }
        seriesRepository.updateTotalEpisodes(series.id(), breakdown.episodes().size());
        seriesRepository.updateStory(series.id(), series.originalStory(), series.storySummary(), breakdown.storyOutline(), "READY");
        int episodeNo = 1;
        for (GeneratedEpisode episode : breakdown.episodes()) {
            workflowRepository.upsertEpisode(
                    seriesId,
                    episodeNo++,
                    episode.title(),
                    episode.summary(),
                    episode.hook(),
                    episode.cliffhanger()
            );
        }
        String taskMessage = existingEpisodes.isEmpty()
                ? "已按专业短剧结构拆分 " + breakdown.episodes().size() + " 集分集大纲"
                : "已根据原因重新拆分 " + breakdown.episodes().size() + " 集分集大纲；原因：" + regenerateReason;
        Long taskId = workflowRepository.createTask(seriesId, null, null, "EPISODES_GENERATE", "SUCCEEDED", taskMessage);
        episodeBreakdownMemoryService.markSucceeded(memoryId, breakdown.episodes().size());
        return task(taskId);
    }

    @Transactional
    public DramaEpisodeDetailVo generateNovelContent(Long episodeId, GenerateRequest request) {
        DramaEpisodeRecord episode = workflowRepository.findEpisode(episodeId)
                .orElseThrow(() -> new BusinessException(404, "分集不存在"));
        String regenerateReason = request == null ? "" : nullToEmpty(request.instruction()).trim();
        if (hasText(episode.novelContent()) && !hasText(regenerateReason)) {
            throw new BusinessException(400, "本集小说正文已存在，请填写重新生成原因后再生成");
        }
        if (!properties.getText().isReady()) {
            throw new BusinessException(400, "本集小说正文生成失败：文本模型未配置");
        }
        DramaSeriesRecord series = findSeries(episode.seriesId());
        List<DramaEpisodeRecord> episodes = workflowRepository.listEpisodes(series.id());
        List<DramaCharacterRecord> characters = characterRepository.listBySeries(series.id());
        String modelResult = textGenerationClient.generate(buildEpisodeNovelPrompt(series, episode, episodes, characters, regenerateReason));
        if (isModelFallback(modelResult)) {
            throw new BusinessException(500, "本集小说正文生成失败：文本模型调用失败或未返回有效内容");
        }
        String novelContent = parseEpisodeNovelContent(modelResult);
        if (!hasText(novelContent) || novelContent.length() < 160) {
            throw new BusinessException(500, "本集小说正文生成失败：模型返回的正文内容不完整");
        }
        workflowRepository.updateEpisodeNovelContent(episodeId, novelContent);
        workflowRepository.createTask(episode.seriesId(), episodeId, null, "EPISODE_NOVEL_GENERATE", "SUCCEEDED",
                hasText(regenerateReason) ? "已根据原因重新生成本集小说正文：" + regenerateReason : "已生成本集小说正文");
        return seriesService.episodeDetail(episodeId);
    }

    @Transactional
    public DramaTaskVo generateScript(Long episodeId, GenerateRequest request) {
        DramaEpisodeRecord episode = workflowRepository.findEpisode(episodeId)
                .orElseThrow(() -> new BusinessException(404, "分集不存在"));
        String regenerateReason = request == null ? "" : nullToEmpty(request.instruction()).trim();
        if (hasText(episode.script()) && !hasText(regenerateReason)) {
            throw new BusinessException(400, "单集剧本已存在，请填写重新生成原因后再生成");
        }
        if (!properties.getText().isReady()) {
            throw new BusinessException(400, "单集剧本生成失败：文本模型未配置");
        }
        if (!hasText(episode.novelContent())) {
            throw new BusinessException(400, "请先生成或填写本集小说正文，再生成单集剧本");
        }
        DramaSeriesRecord series = findSeries(episode.seriesId());
        List<DramaEpisodeRecord> episodes = workflowRepository.listEpisodes(series.id());
        List<DramaCharacterRecord> characters = characterRepository.listBySeries(series.id());
        String modelResult = textGenerationClient.generate(buildEpisodeScriptPrompt(series, episode, episodes, characters, regenerateReason));
        if (isModelFallback(modelResult)) {
            throw new BusinessException(500, "单集剧本生成失败：文本模型调用失败或未返回有效内容");
        }
        String script = parseEpisodeScript(modelResult);
        if (!hasText(script) || script.length() < 120) {
            throw new BusinessException(500, "单集剧本生成失败：模型返回的剧本内容不完整");
        }
        workflowRepository.updateEpisodeScript(episodeId, script, preserveEpisodeStatus(episode.status()));
        Long taskId = workflowRepository.createTask(episode.seriesId(), episodeId, null, "SCRIPT_GENERATE", "SUCCEEDED",
                hasText(regenerateReason) ? "已根据原因重新生成单集剧本：" + regenerateReason : "已生成单集剧本");
        return task(taskId);
    }

    @Transactional
    public DramaEpisodeDetailVo saveScript(Long episodeId, DramaEpisodeScriptSaveRequest request) {
        DramaEpisodeRecord episode = workflowRepository.findEpisode(episodeId)
                .orElseThrow(() -> new BusinessException(404, "分集不存在"));
        String script = request == null ? "" : nullToEmpty(request.script()).trim();
        if (!hasText(script)) {
            throw new BusinessException(400, "单集剧本不能为空");
        }
        workflowRepository.updateEpisodeScript(episodeId, script, preserveEpisodeStatus(episode.status()));
        workflowRepository.createTask(episode.seriesId(), episodeId, null, "SCRIPT_SAVE", "SUCCEEDED", "已保存用户编辑后的单集剧本");
        return seriesService.episodeDetail(episodeId);
    }

    @Transactional
    public DramaEpisodeDetailVo completeEpisodeStep(Long episodeId, DramaEpisodeStepCompleteRequest request) {
        DramaEpisodeRecord episode = workflowRepository.findEpisode(episodeId)
                .orElseThrow(() -> new BusinessException(404, "分集不存在"));
        String step = request == null ? "" : nullToEmpty(request.step()).trim().toUpperCase();
        if (!hasText(step)) {
            throw new BusinessException(400, "璇烽€夋嫨瑕佸畬鎴愮殑鍒朵綔姝ラ");
        }
        String nextStatus = switch (step) {
            case "NOVEL" -> {
                if (!hasText(episode.novelContent())) {
                    throw new BusinessException(400, "请先生成或填写本集小说正文，再完成正文步骤");
                }
                yield "NOVEL_READY";
            }
            case "SCRIPT" -> {
                ensureEpisodeStepReached(episode.status(), "NOVEL_READY", "请先完成本集小说正文步骤");
                if (!hasText(episode.script())) {
                    throw new BusinessException(400, "请先生成或填写单集剧本，再完成剧本步骤");
                }
                yield "SCRIPT_READY";
            }
            case "SCENE" -> {
                ensureEpisodeStepReached(episode.status(), "SCRIPT_READY", "请先完成单集剧本步骤");
                if (workflowRepository.listScenesByEpisode(episodeId).isEmpty()) {
                    throw new BusinessException(400, "请先完成场景拆分，再完成场景步骤");
                }
                yield "SCENE_READY";
            }
            case "SHOT" -> {
                ensureEpisodeStepReached(episode.status(), "SCENE_READY", "请先完成场景拆分步骤");
                if (workflowRepository.listShotsByEpisode(episodeId).isEmpty()) {
                    throw new BusinessException(400, "请先完成镜头拆分，再完成镜头步骤");
                }
                yield "SHOT_READY";
            }
            case "DIALOGUE" -> {
                ensureEpisodeStepReached(episode.status(), "SHOT_READY", "请先完成镜头拆分步骤");
                List<DramaShotRecord> shots = workflowRepository.listShotsByEpisode(episodeId);
                if (shots.isEmpty()) {
                    throw new BusinessException(400, "请先完成镜头拆分，再完成台词步骤");
                }
                boolean allReady = shots.stream().allMatch(shot -> isDialogueReady(shot.dialogue()));
                if (!allReady) {
                    throw new BusinessException(400, "请先生成所有镜头台词，再完成台词步骤");
                }
                yield "DIALOGUE_READY";
            }
            case "IMAGE" -> {
                ensureEpisodeStepReached(episode.status(), "DIALOGUE_READY", "请先完成台词生成步骤");
                List<DramaSceneRecord> scenes = workflowRepository.listScenesByEpisode(episodeId);
                List<DramaShotRecord> shots = workflowRepository.listShotsByEpisode(episodeId);
                boolean allSceneImagesReady = !scenes.isEmpty()
                        && scenes.stream().allMatch(scene -> assetRepository.existsBySceneAndAssetType(scene.id(), "SCENE_IMAGE"));
                boolean allShotImagesReady = !shots.isEmpty()
                        && shots.stream().allMatch(shot -> assetRepository.existsByShotAndAssetType(shot.id(), "SHOT_IMAGE"));
                if (!allSceneImagesReady || !allShotImagesReady) {
                    throw new BusinessException(400, "请先生成本集全部场景参考图和镜头参考图，再完成图片步骤");
                }
                yield "IMAGE_READY";
            }
            case "VIDEO" -> {
                ensureEpisodeStepReached(episode.status(), "IMAGE_READY", "请先完成图片生成步骤");
                boolean hasVideo = assetRepository.listByEpisode(episodeId, 100).stream()
                        .anyMatch(asset -> asset.assetType() != null && asset.assetType().contains("VIDEO"));
                if (!hasVideo) {
                    throw new BusinessException(400, "请先生成本集相关视频素材，再完成视频步骤");
                }
                yield "VIDEO_READY";
            }
            default -> throw new BusinessException(400, "不支持的制作步骤：" + step);
        };
        workflowRepository.updateEpisodeStatus(episodeId, nextStatus);
        workflowRepository.createTask(episode.seriesId(), episodeId, null, "EPISODE_STEP_COMPLETE", "SUCCEEDED", "已完成制作步骤：" + step);
        return seriesService.episodeDetail(episodeId);
    }

    @Transactional
    public DramaEpisodeDetailVo rollbackEpisodeStep(Long episodeId, DramaEpisodeStepRollbackRequest request) {
        DramaEpisodeRecord episode = workflowRepository.findEpisode(episodeId)
                .orElseThrow(() -> new BusinessException(404, "分集不存在"));
        String step = request == null ? "" : nullToEmpty(request.step()).trim().toUpperCase(Locale.ROOT);
        if (!hasText(step)) {
            throw new BusinessException(400, "请选择要回退到的制作步骤");
        }
        String targetStatus = switch (step) {
            case "OUTLINE" -> "OUTLINE_READY";
            case "NOVEL" -> "NOVEL_READY";
            case "SCRIPT" -> "SCRIPT_READY";
            case "SCENE" -> "SCENE_READY";
            case "SHOT" -> "SHOT_READY";
            case "DIALOGUE" -> "DIALOGUE_READY";
            case "IMAGE" -> "IMAGE_READY";
            default -> throw new BusinessException(400, "不支持回退到该步骤：" + step);
        };
        int currentRank = workflowStepRank(episode.status());
        int targetRank = workflowStepRank(targetStatus);
        if (targetRank >= currentRank) {
            throw new BusinessException(400, "只能回退到当前进度之前的步骤");
        }

        recycleAssetsAfterStep(episodeId, targetRank);
        workflowRepository.failActiveTasksByEpisode(episodeId, "制作进度已回退到 " + step + "，旧异步任务已作废");
        if (targetRank < workflowStepRank("SHOT_READY")) {
            workflowRepository.deleteShotsByEpisode(episodeId);
        } else if (targetRank < workflowStepRank("DIALOGUE_READY")) {
            workflowRepository.clearShotDialoguesByEpisode(episodeId);
        }
        if (targetRank < workflowStepRank("SCENE_READY")) {
            workflowRepository.deleteScenesByEpisode(episodeId);
        }
        workflowRepository.rollbackEpisodeContent(
                episodeId,
                targetRank < workflowStepRank("NOVEL_READY"),
                targetRank < workflowStepRank("SCRIPT_READY"),
                targetStatus
        );
        workflowRepository.createTask(episode.seriesId(), episodeId, null, "EPISODE_STEP_ROLLBACK", "SUCCEEDED",
                "已回退到步骤：" + step + "，后续内容已清理");
        return seriesService.episodeDetail(episodeId);
    }

    @Transactional
    public DramaTaskVo generateScenes(Long episodeId, GenerateRequest request) {
        DramaEpisodeRecord episode = workflowRepository.findEpisode(episodeId)
                .orElseThrow(() -> new BusinessException(404, "分集不存在"));
        ensureEpisodeStepReached(episode.status(), "SCRIPT_READY", "请先完成单集剧本步骤，再拆分场景");
        if (!hasText(episode.script())) {
            throw new BusinessException(400, "请先生成或填写单集剧本，再拆分场景");
        }
        String regenerateReason = request == null ? "" : nullToEmpty(request.instruction()).trim();
        boolean hasScenes = !workflowRepository.listScenesByEpisode(episodeId).isEmpty();
        if (hasScenes && !hasText(regenerateReason)) {
            throw new BusinessException(400, "场景数据已存在，请填写重新拆分原因后再生成");
        }
        if (!properties.getText().isReady()) {
            throw new BusinessException(400, "场景拆分失败：文本模型未配置");
        }
        DramaSeriesRecord series = findSeries(episode.seriesId());
        List<DramaCharacterRecord> characters = characterRepository.listBySeries(series.id());
        String modelResult = textGenerationClient.generate(buildEpisodeScenesPrompt(series, episode, characters, regenerateReason));
        if (isModelFallback(modelResult)) {
            throw new BusinessException(500, "场景拆分失败：文本模型调用失败或未返回有效内容");
        }
        List<GeneratedScene> scenes = limitGeneratedScenes(parseEpisodeScenes(modelResult), series);
        if (scenes.isEmpty()) {
            throw new BusinessException(500, "场景拆分失败：模型返回的场景结构不合格");
        }
        if (hasScenes) {
            workflowRepository.deleteShotsByEpisode(episodeId);
            workflowRepository.deleteScenesByEpisode(episodeId);
            workflowRepository.updateEpisodeStatus(episodeId, "SCRIPT_READY");
        }
        for (GeneratedScene scene : scenes) {
            workflowRepository.createScene(
                    episode.seriesId(),
                    episodeId,
                    scene.name(),
                    scene.location(),
                    scene.timeOfDay(),
                    scene.atmosphere(),
                    scene.plotPurpose()
            );
        }
        Long taskId = workflowRepository.createTask(episode.seriesId(), episodeId, null, "SCENES_GENERATE", "SUCCEEDED", "已生成单集场景拆分，共 " + scenes.size() + " 个场景");
        return task(taskId);
    }

    @Transactional
    public DramaTaskVo generateShots(Long episodeId, GenerateRequest request) {
        DramaEpisodeRecord episode = workflowRepository.findEpisode(episodeId)
                .orElseThrow(() -> new BusinessException(404, "分集不存在"));
        ensureEpisodeStepReached(episode.status(), "SCENE_READY", "请先完成场景拆分步骤，再拆分镜头");
        String regenerateReason = request == null ? "" : nullToEmpty(request.instruction()).trim();
        boolean hasShots = !workflowRepository.listShotsByEpisode(episodeId).isEmpty();
        if (hasShots && !hasText(regenerateReason)) {
            throw new BusinessException(400, "镜头数据已存在，请填写重新拆分原因后再生成");
        }
        List<DramaSceneRecord> scenes = workflowRepository.listScenesByEpisode(episodeId);
        if (scenes.isEmpty()) {
            throw new BusinessException(400, "请先完成场景拆分，再拆分镜头");
        }
        if (!properties.getText().isReady()) {
            throw new BusinessException(400, "镜头拆分失败：文本模型未配置");
        }
        DramaSeriesRecord series = findSeries(episode.seriesId());
        List<DramaCharacterRecord> characters = characterRepository.listBySeries(series.id());
        String modelResult = textGenerationClient.generate(buildEpisodeShotsPrompt(series, episode, scenes, characters, regenerateReason));
        if (isModelFallback(modelResult)) {
            throw new BusinessException(500, "镜头拆分失败：文本模型调用失败或未返回有效内容");
        }
        List<GeneratedShot> generatedShots = limitGeneratedShots(parseEpisodeShots(modelResult), series);
        if (generatedShots.isEmpty()) {
            throw new BusinessException(500, "镜头拆分失败：模型返回的镜头结构不合格");
        }
        if (hasShots) {
            workflowRepository.deleteShotsByEpisode(episodeId);
            workflowRepository.updateEpisodeStatus(episodeId, "SCENE_READY");
        }
        int shotNo = 1;
        for (GeneratedShot shot : generatedShots) {
            DramaSceneRecord scene = scenes.get(Math.max(0, Math.min(shot.sceneIndex() - 1, scenes.size() - 1)));
            workflowRepository.upsertShot(
                    episodeId,
                    scene.id(),
                    shotNo++,
                    shot.shotSize(),
                    shot.durationSeconds(),
                    shot.cameraMovement(),
                    shot.composition(),
                    shot.transitionType(),
                    shot.continuityType(),
                    shot.startState(),
                    shot.endState(),
                    shot.continuityNote(),
                    shot.soundEffect(),
                    shot.musicCue(),
                    shot.voiceOver(),
                    shot.action(),
                    "",
                    shot.imagePrompt(),
                    shot.videoPrompt()
            );
        }
        Long taskId = workflowRepository.createTask(episode.seriesId(), episodeId, null, "SHOTS_GENERATE", "SUCCEEDED", "已根据场景拆分生成镜头，共 " + generatedShots.size() + " 个镜头");
        return task(taskId);
    }

    @Transactional
    public DramaTaskVo generateDialogues(Long episodeId, GenerateRequest request) {
        DramaEpisodeRecord episode = workflowRepository.findEpisode(episodeId)
                .orElseThrow(() -> new BusinessException(404, "分集不存在"));
        ensureEpisodeStepReached(episode.status(), "SHOT_READY", "请先完成镜头拆分步骤，再生成台词");
        List<DramaShotRecord> shots = workflowRepository.listShotsByEpisode(episodeId);
        if (shots.isEmpty()) {
            throw new BusinessException(400, "请先完成镜头拆分，再生成台词");
        }
        String regenerateReason = request == null ? "" : nullToEmpty(request.instruction()).trim();
        boolean hasDialogues = shots.stream().anyMatch(shot -> isDialogueReady(shot.dialogue()));
        if (hasDialogues && !hasText(regenerateReason)) {
            throw new BusinessException(400, "台词数据已存在，请填写重新生成原因后再生成");
        }
        if (!properties.getText().isReady()) {
            throw new BusinessException(400, "台词生成失败：文本模型未配置");
        }
        DramaSeriesRecord series = findSeries(episode.seriesId());
        List<DramaSceneRecord> scenes = workflowRepository.listScenesByEpisode(episodeId);
        List<DramaCharacterRecord> characters = characterRepository.listBySeries(series.id());
        String modelResult = textGenerationClient.generate(buildEpisodeDialoguesPrompt(series, episode, scenes, shots, characters, regenerateReason));
        if (isModelFallback(modelResult)) {
            throw new BusinessException(500, "台词生成失败：文本模型调用失败或未返回有效内容");
        }
        List<GeneratedDialogue> dialogues = parseEpisodeDialogues(modelResult);
        if (dialogues.isEmpty()) {
            throw new BusinessException(500, "台词生成失败：模型返回的台词结构不合格");
        }
        String[] dialogueByShotNo = new String[shots.size() + 1];
        for (GeneratedDialogue dialogue : dialogues) {
            if (dialogue.shotNo() >= 1 && dialogue.shotNo() <= shots.size()) {
                dialogueByShotNo[dialogue.shotNo()] = dialogue.dialogue();
            }
        }
        List<Integer> missingShotNos = new ArrayList<>();
        for (int shotNo = 1; shotNo <= shots.size(); shotNo++) {
            if (!isDialogueReady(dialogueByShotNo[shotNo])) {
                missingShotNos.add(shotNo);
            }
        }
        if (!missingShotNos.isEmpty()) {
            throw new BusinessException(500, "台词生成失败：模型未覆盖全部镜头，缺少镜头 " + missingShotNos + " 的台词");
        }
        for (GeneratedDialogue dialogue : dialogues) {
            if (dialogue.shotNo() >= 1 && dialogue.shotNo() <= shots.size()) {
                DramaShotRecord shot = shots.get(dialogue.shotNo() - 1);
                workflowRepository.updateShotDialogue(shot.id(), dialogue.dialogue());
            }
        }
        Long taskId = workflowRepository.createTask(episode.seriesId(), episodeId, null, "DIALOGUES_GENERATE", "SUCCEEDED",
                hasText(regenerateReason) ? "已根据原因重新生成镜头台词：" + regenerateReason : "已生成镜头台词");
        return task(taskId);
    }

    public DramaTaskVo generateCharacterImage(Long characterId, GenerateRequest request) {
        String providerTaskId = imageGenerationClient.submitImageTask(request == null ? "" : request.instruction());
        Long taskId = workflowRepository.createTask(0L, null, null, "CHARACTER_IMAGE_GENERATE", "PENDING", "providerTaskId=" + providerTaskId);
        return task(taskId);
    }

    public DramaTaskVo generateSceneImage(Long sceneId, GenerateRequest request) {
        DramaSceneRecord scene = workflowRepository.findScene(sceneId)
                .orElseThrow(() -> new BusinessException(404, "场景不存在"));
        DramaEpisodeRecord episode = workflowRepository.findEpisode(scene.episodeId())
                .orElseThrow(() -> new BusinessException(404, "鍦烘櫙鎵€灞炲垎闆嗕笉瀛樺湪"));
        ensureEpisodeStepReached(episode.status(), "DIALOGUE_READY", "请先完成台词生成步骤，再生成场景参考图");
        if (assetRepository.existsBySceneAndAssetType(sceneId, "SCENE_IMAGE")
                || workflowRepository.existsNonFailedTaskByTargetAndTaskType("SCENE", sceneId, "SCENE_IMAGE_GENERATE")) {
            throw new BusinessException(400, "场景参考图已存在或正在生成，请删除后再生成");
        }
        DramaSeriesRecord series = findSeries(scene.seriesId());
        List<DramaCharacterRecord> characters = characterRepository.listBySeries(series.id());
        return imageGenerationService.submit(
                buildSceneImageContext(series, episode, scene),
                () -> buildSceneImagePrompt(series, episode, scene, characters, request == null ? "" : request.instruction())
        );
    }

    public DramaTaskVo generateShotImage(Long shotId, GenerateRequest request) {
        DramaShotRecord shot = workflowRepository.findShot(shotId)
                .orElseThrow(() -> new BusinessException(404, "镜头不存在"));
        DramaEpisodeRecord episode = workflowRepository.findEpisode(shot.episodeId())
                .orElseThrow(() -> new BusinessException(404, "镜头所属分集不存在"));
        ensureEpisodeStepReached(episode.status(), "DIALOGUE_READY", "请先完成台词生成步骤，再生成镜头参考图");
        if (assetRepository.existsByShotAndAssetType(shotId, "SHOT_IMAGE")
                || workflowRepository.existsNonFailedTaskByShotAndTaskType(shotId, "SHOT_IMAGE_GENERATE")) {
            throw new BusinessException(400, "镜头参考图已存在或正在生成，请删除后再生成");
        }
        DramaSeriesRecord series = findSeries(episode.seriesId());
        DramaSceneRecord scene = shot.sceneId() == null ? null : workflowRepository.findScene(shot.sceneId()).orElse(null);
        List<DramaCharacterRecord> characters = characterRepository.listBySeries(series.id());
        return imageGenerationService.submit(
                buildShotImageContext(series, episode, scene, shot, characters),
                () -> buildShotImagePrompt(series, episode, scene, shot, characters, request == null ? "" : request.instruction())
        );
    }

    public List<DramaTaskVo> generateEpisodeSceneImages(Long episodeId) {
        DramaEpisodeRecord episode = workflowRepository.findEpisode(episodeId)
                .orElseThrow(() -> new BusinessException(404, "分集不存在"));
        ensureEpisodeStepReached(episode.status(), "DIALOGUE_READY", "请先完成台词生成步骤，再生成场景参考图");
        List<DramaSceneRecord> scenes = workflowRepository.listScenesByEpisode(episodeId);
        if (scenes.isEmpty()) {
            throw new BusinessException(400, "请先完成场景拆分，再生成场景参考图");
        }
        DramaSeriesRecord series = findSeries(episode.seriesId());
        List<DramaCharacterRecord> characters = characterRepository.listBySeries(series.id());
        List<DramaTaskVo> tasks = new ArrayList<>();
        for (DramaSceneRecord scene : scenes) {
            if (assetRepository.existsBySceneAndAssetType(scene.id(), "SCENE_IMAGE")
                    || workflowRepository.existsNonFailedTaskByTargetAndTaskType("SCENE", scene.id(), "SCENE_IMAGE_GENERATE")) {
                continue;
            }
            tasks.add(imageGenerationService.submit(
                    buildSceneImageContext(series, episode, scene),
                    () -> buildSceneImagePrompt(series, episode, scene, characters, "")
            ));
        }
        if (tasks.isEmpty()) {
            throw new BusinessException(400, "本集场景参考图已全部存在或正在生成，无需重复生成");
        }
        return tasks;
    }

    public List<DramaTaskVo> generateEpisodeShotImages(Long episodeId) {
        DramaEpisodeRecord episode = workflowRepository.findEpisode(episodeId)
                .orElseThrow(() -> new BusinessException(404, "分集不存在"));
        ensureEpisodeStepReached(episode.status(), "DIALOGUE_READY", "请先完成台词生成步骤，再生成镜头参考图");
        List<DramaShotRecord> shots = workflowRepository.listShotsByEpisode(episodeId);
        if (shots.isEmpty()) {
            throw new BusinessException(400, "请先完成镜头拆分，再生成镜头参考图");
        }
        DramaSeriesRecord series = findSeries(episode.seriesId());
        List<DramaSceneRecord> scenes = workflowRepository.listScenesByEpisode(episodeId);
        List<DramaCharacterRecord> characters = characterRepository.listBySeries(series.id());
        List<DramaTaskVo> tasks = new ArrayList<>();
        for (DramaShotRecord shot : shots) {
            if (assetRepository.existsByShotAndAssetType(shot.id(), "SHOT_IMAGE")
                    || workflowRepository.existsNonFailedTaskByShotAndTaskType(shot.id(), "SHOT_IMAGE_GENERATE")) {
                continue;
            }
            DramaSceneRecord scene = scenes.stream()
                    .filter(item -> item.id().equals(shot.sceneId()))
                    .findFirst()
                    .orElse(null);
            tasks.add(imageGenerationService.submit(
                    buildShotImageContext(series, episode, scene, shot, characters),
                    () -> buildShotImagePrompt(series, episode, scene, shot, characters, "")
            ));
        }
        if (tasks.isEmpty()) {
            throw new BusinessException(400, "本集镜头参考图已全部存在或正在生成，无需重复生成");
        }
        return tasks;
    }

    public DramaTaskVo generateShotVideo(Long shotId, GenerateRequest request) {
        DramaShotRecord shot = workflowRepository.findShot(shotId)
                .orElseThrow(() -> new BusinessException(404, "镜头不存在"));
        DramaEpisodeRecord episode = workflowRepository.findEpisode(shot.episodeId())
                .orElseThrow(() -> new BusinessException(404, "镜头所属分集不存在"));
        ensureEpisodeStepReached(episode.status(), "IMAGE_READY", "请先完成图片步骤，再生成镜头视频");
        if (assetRepository.existsByShotAndAssetType(shotId, "SHOT_VIDEO")
                || workflowRepository.existsNonFailedTaskByShotAndTaskType(shotId, "SHOT_VIDEO_GENERATE")) {
            throw new BusinessException(400, "镜头视频已存在或正在生成，请删除后再生成");
        }
        DramaSeriesRecord series = findSeries(episode.seriesId());
        DramaSceneRecord scene = shot.sceneId() == null ? null : workflowRepository.findScene(shot.sceneId()).orElse(null);
        return videoGenerationService.submit(series, episode, scene, shot);
    }

    public List<DramaTaskVo> generateEpisodeShotVideos(Long episodeId) {
        DramaEpisodeRecord episode = workflowRepository.findEpisode(episodeId)
                .orElseThrow(() -> new BusinessException(404, "分集不存在"));
        ensureEpisodeStepReached(episode.status(), "IMAGE_READY", "请先完成图片步骤，再生成镜头视频");
        List<DramaShotRecord> shots = workflowRepository.listShotsByEpisode(episodeId);
        if (shots.isEmpty()) {
            throw new BusinessException(400, "请先完成镜头拆分，再生成视频");
        }
        DramaSeriesRecord series = findSeries(episode.seriesId());
        List<DramaSceneRecord> scenes = workflowRepository.listScenesByEpisode(episodeId);
        List<DramaTaskVo> tasks = new ArrayList<>();
        for (DramaShotRecord shot : shots) {
            if (assetRepository.existsByShotAndAssetType(shot.id(), "SHOT_VIDEO")
                    || workflowRepository.existsNonFailedTaskByShotAndTaskType(shot.id(), "SHOT_VIDEO_GENERATE")) {
                continue;
            }
            DramaSceneRecord scene = scenes.stream()
                    .filter(item -> item.id().equals(shot.sceneId()))
                    .findFirst()
                    .orElse(null);
            tasks.add(videoGenerationService.submit(series, episode, scene, shot));
        }
        if (tasks.isEmpty()) {
            throw new BusinessException(400, "本集镜头视频已全部存在或正在生成，无需重复生成");
        }
        return tasks;
    }

    public DramaTaskVo task(Long taskId) {
        return workflowRepository.findTask(taskId).stream()
                .findFirst()
                .map(record -> new DramaTaskVo(
                        record.id(),
                        record.seriesId(),
                        record.episodeId(),
                        record.shotId(),
                        record.characterId(),
                        record.assetId(),
                        record.targetType(),
                        record.targetId(),
                        record.assetType(),
                        record.assetSubType(),
                        record.taskType(),
                        record.providerTaskId(),
                        record.status(),
                        record.progress(),
                        record.stage(),
                        record.errorMessage(),
                        record.createdAt(),
                        record.updatedAt()
                ))
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));
    }

    private void ensureStoryNotExists(DramaSeriesRecord series) {
        if (hasText(series.originalStory()) || hasText(series.storySummary())) {
            throw new BusinessException(400, "数据已存在，请删除后再生成");
        }
    }


    private void recycleAssetsAfterStep(Long episodeId, int targetRank) {
        List<DramaAssetRecord> assets = assetRepository.listByEpisode(episodeId, 1000);
        for (DramaAssetRecord asset : assets) {
            if (!shouldRecycleAssetAfterRollback(asset, targetRank)) {
                continue;
            }
            assetService.moveAssetToRecycle(asset);
            assetRepository.deleteById(asset.id());
        }
    }

    private boolean shouldRecycleAssetAfterRollback(DramaAssetRecord asset, int targetRank) {
        String assetType = asset.assetType() == null ? "" : asset.assetType();
        if (targetRank < workflowStepRank("IMAGE_READY")) {
            return assetType.contains("IMAGE") || assetType.contains("VIDEO");
        }
        if (targetRank < workflowStepRank("VIDEO_READY")) {
            return assetType.contains("VIDEO");
        }
        return false;
    }

    private int workflowStepRank(String status) {
        return switch (status == null ? "" : status) {
            case "OUTLINE_READY" -> 0;
            case "NOVEL_READY" -> 1;
            case "SCRIPT_READY" -> 2;
            case "SCENE_READY" -> 3;
            case "SHOT_READY" -> 4;
            case "DIALOGUE_READY" -> 5;
            case "IMAGE_READY" -> 6;
            case "VIDEO_READY" -> 7;
            default -> 0;
        };
    }

    private DramaSeriesRecord ensureInternalStoryOutline(DramaSeriesRecord series) {
        if (hasText(series.fullStory())) {
            return series;
        }
        if (!hasText(series.originalStory()) && !hasText(series.storySummary())) {
            throw new BusinessException(400, "请先生成或填写故事原文和故事摘要，再生成分集大纲");
        }
        String modelOutline = textGenerationClient.generate(buildInternalStoryOutlinePrompt(series));
        String outline = isModelFallback(modelOutline) ? buildFallbackStoryOutline(series) : modelOutline.trim();
        seriesRepository.updateStory(series.id(), series.originalStory(), series.storySummary(), outline, "READY");
        workflowRepository.createTask(series.id(), null, null, "STORY_OUTLINE_GENERATE", "SUCCEEDED", "已在分集阶段自动生成内部完整故事大纲");
        return findSeries(series.id());
    }

    private DramaSeriesRecord findSeries(Long seriesId) {
        return seriesRepository.findById(seriesId)
                .orElseThrow(() -> new BusinessException(404, "短剧项目不存在"));
    }

    private DramaImageGenerationContext buildSceneImageContext(DramaSeriesRecord series, DramaEpisodeRecord episode, DramaSceneRecord scene) {
        try {
            Path saveDirectory = assetService.ensureSeriesRoot(series.id())
                    .resolve("场景图")
                    .resolve(safeFileName("第" + episode.episodeNo() + "集-" + episode.id()))
                    .resolve(safeFileName("鍦烘櫙-" + scene.id() + "-" + scene.name()))
                    .normalize();
            return new DramaImageGenerationContext(
                    series.id(),
                    episode.id(),
                    scene.id(),
                    null,
                    null,
                    "SCENE",
                    scene.id(),
                    "SCENE_IMAGE",
                    "SCENE_REFERENCE",
                    null,
                    List.of(),
                    "",
                    "SCENE-" + scene.id() + "-" + System.currentTimeMillis(),
                    "scene-reference-" + scene.id(),
                    "image/jpeg",
                    productionImageSize(series),
                    saveDirectory
            );
        } catch (IOException ex) {
            throw new BusinessException(500, "创建场景参考图目录失败：" + ex.getMessage());
        }
    }

    private DramaImageGenerationContext buildShotImageContext(
            DramaSeriesRecord series,
            DramaEpisodeRecord episode,
            DramaSceneRecord scene,
            DramaShotRecord shot,
            List<DramaCharacterRecord> characters
    ) {
        try {
            Path saveDirectory = assetService.ensureSeriesRoot(series.id())
                    .resolve(safeFileName("第" + episode.episodeNo() + "集-" + episode.id()))
                    .resolve("镜头")
                    .resolve(safeFileName("镜头-" + shot.shotNo() + "-" + shot.id()))
                    .normalize();
            return new DramaImageGenerationContext(
                    series.id(),
                    episode.id(),
                    scene == null ? shot.sceneId() : scene.id(),
                    shot.id(),
                    null,
                    "SHOT",
                    shot.id(),
                    "SHOT_IMAGE",
                    "SHOT_REFERENCE",
                    primaryShotReferenceAssetId(characters),
                    shotReferenceAssetIds(characters),
                    "",
                    "SHOT-" + shot.id() + "-" + System.currentTimeMillis(),
                    "shot-reference-" + shot.shotNo() + "-" + shot.id(),
                    "image/jpeg",
                    productionImageSize(series),
                    saveDirectory
            );
        } catch (IOException ex) {
            throw new BusinessException(500, "创建镜头参考图目录失败：" + ex.getMessage());
        }
    }

    private String buildSceneImagePrompt(
            DramaSeriesRecord series,
            DramaEpisodeRecord episode,
            DramaSceneRecord scene,
            List<DramaCharacterRecord> characters,
            String instruction
    ) {
        String productionBrief = """
                场景参考图生产资料：
                - 目标：生成短剧分集里的场景参考图，用于统一后续镜头首帧和视频片段的环境风格。
                - 项目设定：
                %s
                - 当前分集：第 %s 集《%s》
                - 分集摘要：%s
                - 场景名称：%s
                - 拍摄地点：%s
                - 时间：%s
                - 氛围：%s
                - 剧情目的：%s
                - 角色资料：%s
                - 用户额外要求：%s
                """.formatted(
                buildSeriesProductionContext(series),
                episode.episodeNo(),
                nullToDefault(episode.title(), "未命名"),
                nullToDefault(episode.summary(), "暂无"),
                nullToDefault(scene.name(), "未命名场景"),
                nullToDefault(scene.location(), "暂无"),
                nullToDefault(scene.timeOfDay(), "暂无"),
                nullToDefault(scene.atmosphere(), "暂无"),
                nullToDefault(scene.plotPurpose(), "暂无"),
                formatCharactersForPrompt(characters),
                nullToDefault(instruction, "暂无")
        );
        return buildEnglishVisualPromptByTextModel(productionBrief, """
                Generate one cinematic environment reference image for a short drama scene.
                Focus on location, time of day, atmosphere, production design, lighting and usable spatial layout.
                Environment only. Do not include main characters. Do not create a character portrait, close-up person, face, hero shot, or recognizable lead role.
                If people must appear for scale, keep them tiny, distant, anonymous silhouettes or background extras.
                The image must be useful as a stable visual reference for later shot-level first-frame generation.
                %s
                """.formatted(productionCompositionRequirement(series)));
    }

    private String buildShotImagePrompt(
            DramaSeriesRecord series,
            DramaEpisodeRecord episode,
            DramaSceneRecord scene,
            DramaShotRecord shot,
            List<DramaCharacterRecord> characters,
            String instruction
    ) {
        String productionBrief = """
                镜头首帧图生产资料：
                - 项目生产设定：
                %s
                - 当前分集：第 %s 集《%s》
                - 分集摘要：%s
                - 场景名称：%s
                - 场景地点：%s
                - 场景时间：%s
                - 场景氛围：%s
                - 镜头编号：%s
                - 景别：%s
                - 镜头动作：%s
                - 连续性类型：%s
                - 镜头开始状态：%s
                - 镜头结束状态：%s
                - 连续性说明：%s
                - 台词或旁白：%s
                - 原始图片提示词：%s
                - 角色资料：%s
                - 用户额外要求：%s
                """.formatted(
                buildSeriesProductionContext(series),
                episode.episodeNo(),
                nullToDefault(episode.title(), "未命名"),
                nullToDefault(episode.summary(), "暂无"),
                scene == null ? "暂无" : nullToDefault(scene.name(), "未命名场景"),
                scene == null ? "暂无" : nullToDefault(scene.location(), "暂无"),
                scene == null ? "暂无" : nullToDefault(scene.timeOfDay(), "暂无"),
                scene == null ? "暂无" : nullToDefault(scene.atmosphere(), "暂无"),
                shot.shotNo(),
                nullToDefault(shot.shotSize(), "未设置"),
                nullToDefault(shot.action(), "暂无"),
                nullToDefault(shot.continuityType(), "CUT"),
                nullToDefault(shot.startState(), "暂无"),
                nullToDefault(shot.endState(), "暂无"),
                nullToDefault(shot.continuityNote(), "暂无"),
                nullToDefault(shot.dialogue(), "暂无"),
                nullToDefault(shot.imagePrompt(), "暂无"),
                formatCharactersForPrompt(characters),
                nullToDefault(instruction, "暂无")
        );
        return buildEnglishVisualPromptByTextModel(productionBrief, """
                Generate one cinematic shot reference image / first-frame image for a short drama video shot.
                The image must clearly express the shot size, character action, emotion, location, lighting and composition.
                Use the provided reference character image(s) as strict visual identity references. Keep face, age, gender, body type, hairstyle, costume and temperament consistent.
                Do not invent unrelated characters. If a character is in the frame, it must match the reference image and role card.
                Make it production-ready for image-to-video generation: clear subject, readable action, cinematic realism.
                %s
                """.formatted(productionCompositionRequirement(series)));
    }

    private String buildEnglishVisualPromptByTextModel(String productionBrief, String imageRequirement) {
        String instruction = """
                OUTPUT LANGUAGE POLICY:
                Your final answer must be ASCII English only.
                Do not output Chinese, Japanese, Korean, Spanish, French, Russian, Arabic, emoji, full-width punctuation, Markdown, code fences, explanations, labels, or notes.
                If the source material is Chinese, rewrite its meaning into natural professional English image-prompt language.
                你是短剧视觉设定师和 AI 图片提示词工程师。请把下面的中文制作资料改写成英文图片生成提示词。
                这不是逐字翻译，而是生成图片模型容易执行的专业英文生产提示词。
                必须遵守：
                1. 只输出英文提示词，不要 Markdown，不要解释，不要输出中文。
                2. 不要丢失项目类型、题材、风格、场景地点、镜头动作、台词、角色关系等核心信息。
                3. 不要添加资料里没有的核心人物身份，不要改变角色性别、年龄感、服装和气质。
                4. 输出必须包含 subject, environment, composition, lighting, mood, camera/shot style, visual consistency rules.
                5. 禁止 text, subtitles, logo, watermark, modern objects that contradict the story world.
                6. 结尾必须加入图片类型要求里的构图比例要求，以及 cinematic realism, high quality production still, coherent visual identity, no text, no watermark.

                图片类型要求：
                %s

                中文制作资料：
                %s
                """.formatted(imageRequirement, productionBrief);
        String generatedPrompt = textGenerationClient.generate(instruction).trim();
        if (isUsableEnglishImagePrompt(generatedPrompt)) {
            return generatedPrompt;
        }
        throw new BusinessException(500, "图片英文提示词生成失败，文本模型返回不可用：" + limitText(generatedPrompt, 500));
    }

    private boolean isUsableEnglishImagePrompt(String value) {
        String text = nullToEmpty(value).trim();
        if (text.length() < 80) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (isModelFallback(text)
                || lower.contains("sorry")
                || lower.contains("cannot")
                || lower.contains("i can't")
                || lower.contains("无法")
                || lower.contains("不能")) {
            return false;
        }
        return isAsciiEnglishPrompt(text);
    }

    private boolean isAsciiEnglishPrompt(String text) {
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '\n' || ch == '\r' || ch == '\t') {
                continue;
            }
            if (ch < 32 || ch > 126) {
                return false;
            }
        }
        return true;
    }

    private String buildEpisodeScenesPrompt(DramaSeriesRecord series, DramaEpisodeRecord episode, List<DramaCharacterRecord> characters, String instruction) {
        return """
                你是专业短剧导演和制片统筹。请根据单集剧本拆分“场景”，不要拆镜头。

                场景拆分定义：
                - 场景是一个可拍摄的剧情空间或剧情段落。
                - 场景只描述地点、时间、氛围、剧情目的。
                - 不要输出景别、运镜、镜头序号、图片提示词、视频提示词。
                - 必须根据项目单集时长控制场景数量：%s

                %s

                当前分集：
                第 %s 集《%s》
                摘要：%s
                开场钩子：%s
                结尾悬念：%s

                角色资料：
                %s

                单集剧本：
                %s

                用户额外要求：
                %s

                输出要求：
                - 场景数量必须以“项目单集时长”和剧情自然段落为准，不要机械固定 3 到 8 个。
                - 每个场景必须服务剧情推进，地点、时间、冲突目标发生明显变化时才拆新场景。
                - 只输出如下结构，不要输出解释。
                [SCENE]
                name: 场景名称
                location: 拍摄地点
                timeOfDay: 时间
                atmosphere: 情绪氛围
                plotPurpose: 剧情目的
                [/SCENE]
                """.formatted(
                buildSceneCountGuidance(series.episodeDurationMinutes()),
                buildSeriesProductionContext(series),
                episode.episodeNo(),
                nullToDefault(episode.title(), "未命名分集"),
                nullToDefault(episode.summary(), "暂无"),
                nullToDefault(episode.hook(), "暂无"),
                nullToDefault(episode.cliffhanger(), "暂无"),
                formatCharactersForPrompt(characters),
                limitText(episode.script(), 12000),
                nullToDefault(instruction, "暂无")
        );
    }

    private String buildEpisodeShotsPrompt(DramaSeriesRecord series, DramaEpisodeRecord episode, List<DramaSceneRecord> scenes, List<DramaCharacterRecord> characters, String instruction) {
        return """
                你是专业短剧导演和分镜师。请根据已经拆分好的场景生成“镜头拆分”。

                镜头拆分定义：
                - 镜头是视频生成的最小生产单位。
                - 一个场景应拆成多个连续镜头，但不能为了凑数量硬拆。
                - 每个镜头必须明确景别、预计时长、运镜、构图、动作、图片提示词、视频提示词。
                - 不要生成台词，台词会在后续“台词生成”步骤单独处理。
                - 必须根据项目单集时长控制镜头数量：%s

                %s

                当前分集：
                第 %s 集《%s》
                摘要：%s
                开场钩子：%s
                结尾悬念：%s

                角色资料：
                %s

                已有场景列表：
                %s

                单集剧本：
                %s

                用户额外要求：
                %s

                输出要求：
                - 镜头总数必须优先服从项目单集时长，不能按每个场景固定数量机械扩张。
                - sceneIndex 必须对应已有场景序号。
                - imagePrompt 必须结合项目类型、题材、风格、角色资料、场景地点和当前镜头动作，不要生成通用模板提示词。
                - videoPrompt 必须结合项目单集时长控制单镜头节奏，写清动作、运镜、时长感和情绪变化。
                - durationSeconds 必须是 2 到 12 秒之间的整数，常规镜头建议 4 到 7 秒。
                - cameraMovement 必须写清固定镜头、缓慢推进、横移、跟拍、拉远、摇镜等具体运镜。
                - composition 必须写清主体位置、前景、背景、人物关系和画面重心。
                - transitionType、soundEffect、musicCue、voiceOver 用于后续视频剪辑和声音设计，不要留空；没有旁白时 voiceOver 写“无”。
                - 必须输出 continuityType/startState/endState/continuityNote。continuityType 只能是 CONTINUOUS、SAME_SCENE、CUT、TRANSITION。
                - 相邻镜头必须连贯：人物位置、身体朝向、视线方向、情绪、手部动作、空间关系、光线、服装和道具不能突然跳变。
                - startState 写清镜头第一帧状态，endState 写清镜头结束状态，continuityNote 写清本镜头如何接上一镜头以及如何引出下一镜头。
                - 镜头描述必须覆盖人物站位、表情、肢体、手部、镜头方向、环境细节、运动起止状态，禁止重复身体、重复头部、无原因瞬移、无原因换装。
                - 只输出如下结构，不要输出解释。
                [SHOT]
                sceneIndex: 1
                shotSize: 景别
                durationSeconds: 5
                cameraMovement: 运镜方式
                composition: 画面构图
                transitionType: 转场方式
                continuityType: CUT
                startState: 镜头开始时的人物位置、姿态、表情、视线、道具和环境状态
                endState: 镜头结束时的人物位置、姿态、表情、视线、道具和环境状态
                continuityNote: 本镜头与上一镜头、下一镜头的动作和情绪衔接说明
                soundEffect: 音效提示
                musicCue: 配乐情绪
                voiceOver: 旁白，没有则写无
                action: 画面动作
                imagePrompt: 图片提示词
                videoPrompt: 视频提示词
                [/SHOT]
                """.formatted(
                buildShotCountGuidance(series.episodeDurationMinutes()),
                buildSeriesProductionContext(series),
                episode.episodeNo(),
                nullToDefault(episode.title(), "未命名分集"),
                nullToDefault(episode.summary(), "暂无"),
                nullToDefault(episode.hook(), "暂无"),
                nullToDefault(episode.cliffhanger(), "暂无"),
                formatCharactersForPrompt(characters),
                formatScenesForPrompt(scenes),
                limitText(episode.script(), 12000),
                nullToDefault(instruction, "暂无")
        );
    }

    private String buildEpisodeDialoguesPrompt(DramaSeriesRecord series, DramaEpisodeRecord episode, List<DramaSceneRecord> scenes, List<DramaShotRecord> shots, List<DramaCharacterRecord> characters, String instruction) {
        return """
                你是专业短剧编剧，负责为已经拆好的镜头补全台词。

                台词生成定义：
                - 只生成镜头台词，不改场景、不改镜头动作、不改图片提示词和视频提示词。
                - 没有人说话的镜头可以写“无”，但不要大面积写“无”。
                - 台词要短剧化：短、狠、清楚，有冲突，有情绪，有推进。
                - 如果一个镜头适合旁白，可以写“旁白：...”。
                - 台词必须匹配项目类型、题材、风格和角色关系，不能写成通用对白。
                %s

                当前分集：
                第 %s 集《%s》
                摘要：%s
                开场钩子：%s
                结尾悬念：%s

                角色资料：
                %s

                场景列表：
                %s

                镜头列表：
                %s

                单集剧本：
                %s

                用户额外要求：
                %s

                输出要求：
                - 必须覆盖每一个镜头。
                - shotNo 必须对应镜头编号。
                - 只输出如下结构，不要输出解释。
                [DIALOGUE]
                shotNo: 1
                dialogue: 台词内容
                [/DIALOGUE]
                """.formatted(
                buildSeriesProductionContext(series),
                episode.episodeNo(),
                nullToDefault(episode.title(), "未命名分集"),
                nullToDefault(episode.summary(), "暂无"),
                nullToDefault(episode.hook(), "暂无"),
                nullToDefault(episode.cliffhanger(), "暂无"),
                formatCharactersForPrompt(characters),
                formatScenesForPrompt(scenes),
                formatShotsForPrompt(shots),
                limitText(episode.script(), 12000),
                nullToDefault(instruction, "暂无")
        );
    }

    private List<GeneratedScene> parseEpisodeScenes(String value) {
        List<GeneratedScene> scenes = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?s)\\[SCENE\\](.*?)\\[/SCENE\\]").matcher(nullToEmpty(value));
        while (matcher.find()) {
            String block = matcher.group(1);
            String name = extractField(block, "name");
            String location = extractField(block, "location");
            String timeOfDay = extractField(block, "timeOfDay");
            String atmosphere = extractField(block, "atmosphere");
            String plotPurpose = extractField(block, "plotPurpose");
            if (hasText(name) && hasText(plotPurpose)) {
                scenes.add(new GeneratedScene(
                        limitText(name, 120),
                        limitText(location, 120),
                        limitText(timeOfDay, 80),
                        limitText(atmosphere, 200),
                        limitText(plotPurpose, 500)
                ));
            }
        }
        return scenes;
    }

    private List<GeneratedShot> parseEpisodeShots(String value) {
        List<GeneratedShot> shots = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?s)\\[SHOT\\](.*?)\\[/SHOT\\]").matcher(nullToEmpty(value));
        while (matcher.find()) {
            String block = matcher.group(1);
            Integer sceneIndex = parsePositiveInt(extractField(block, "sceneIndex"));
            String shotSize = extractField(block, "shotSize");
            Integer durationSeconds = parsePositiveInt(extractField(block, "durationSeconds"));
            String cameraMovement = extractField(block, "cameraMovement");
            String composition = extractField(block, "composition");
            String transitionType = extractField(block, "transitionType");
            String continuityType = normalizeContinuityType(extractField(block, "continuityType"));
            String startState = extractField(block, "startState");
            String endState = extractField(block, "endState");
            String continuityNote = extractField(block, "continuityNote");
            String soundEffect = extractField(block, "soundEffect");
            String musicCue = extractField(block, "musicCue");
            String voiceOver = extractField(block, "voiceOver");
            String action = extractField(block, "action");
            String imagePrompt = extractField(block, "imagePrompt");
            String videoPrompt = extractField(block, "videoPrompt");
            if (sceneIndex != null && hasText(action) && hasText(imagePrompt) && hasText(videoPrompt)) {
                shots.add(new GeneratedShot(
                        sceneIndex,
                        limitText(shotSize, 80),
                        normalizeShotDuration(durationSeconds),
                        limitText(cameraMovement, 120),
                        limitText(composition, 300),
                        limitText(transitionType, 80),
                        continuityType,
                        limitText(startState, 800),
                        limitText(endState, 800),
                        limitText(continuityNote, 1000),
                        limitText(soundEffect, 200),
                        limitText(musicCue, 200),
                        limitText(voiceOver, 500),
                        limitText(action, 800),
                        "",
                        limitText(imagePrompt, 1200),
                        limitText(videoPrompt, 1200)
                ));
            }
        }
        return shots;
    }

    private List<GeneratedDialogue> parseEpisodeDialogues(String value) {
        List<GeneratedDialogue> dialogues = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?s)\\[DIALOGUE\\](.*?)\\[/DIALOGUE\\]").matcher(nullToEmpty(value));
        while (matcher.find()) {
            String block = matcher.group(1);
            Integer shotNo = parsePositiveInt(extractField(block, "shotNo"));
            String dialogue = extractField(block, "dialogue");
            if (shotNo != null && hasText(dialogue)) {
                dialogues.add(new GeneratedDialogue(shotNo, limitText(dialogue, 1000)));
            }
        }
        return dialogues;
    }

    private List<GeneratedScene> limitGeneratedScenes(List<GeneratedScene> scenes, DramaSeriesRecord series) {
        int max = sceneCountRange(series.episodeDurationMinutes())[1];
        return scenes.size() <= max ? scenes : scenes.subList(0, max);
    }

    private List<GeneratedShot> limitGeneratedShots(List<GeneratedShot> shots, DramaSeriesRecord series) {
        int max = shotCountRange(series.episodeDurationMinutes())[1];
        return shots.size() <= max ? shots : shots.subList(0, max);
    }

    private String buildSeriesProductionContext(DramaSeriesRecord series) {
        return """
                短剧项目生产设定：
                - 项目名称：%s
                - 类型：%s
                - 简介：%s
                - 题材：%s
                - 风格：%s
                - 当前总集数：%s
                - 单集目标时长：%s 分钟
                - 生产原则：所有场景、镜头、台词和图片/视频提示词都必须服务上述项目设定；不要脱离项目类型、题材、风格和时长约束自由扩写。
                """.formatted(
                nullToDefault(series.name(), "未命名短剧"),
                nullToDefault(series.type(), "未设置"),
                nullToDefault(series.intro(), "暂无"),
                nullToDefault(series.theme(), "暂无"),
                nullToDefault(series.style(), "未设置"),
                series.totalEpisodes(),
                safeDuration(series.episodeDurationMinutes())
        );
    }

    private String buildSceneCountGuidance(Integer durationMinutes) {
        int[] range = sceneCountRange(durationMinutes);
        return "单集约 " + safeDuration(durationMinutes) + " 分钟，建议拆 " + range[0] + " 到 " + range[1] + " 个场景；不要为了凑数量硬拆。";
    }

    private String buildShotCountGuidance(Integer durationMinutes) {
        int[] range = shotCountRange(durationMinutes);
        return "单集约 " + safeDuration(durationMinutes) + " 分钟，建议拆 " + range[0] + " 到 " + range[1] + " 个镜头；单镜头通常 4 到 7 秒，优先控制镜头数量和镜头目的。";
    }

    private int[] sceneCountRange(Integer durationMinutes) {
        int duration = safeDuration(durationMinutes);
        int min = Math.max(2, duration + 1);
        int max = Math.max(min, duration * 2 + 2);
        return new int[]{min, max};
    }

    private int[] shotCountRange(Integer durationMinutes) {
        int duration = safeDuration(durationMinutes);
        int min = Math.max(6, duration * 8);
        int max = Math.max(min, duration * 12);
        return new int[]{min, max};
    }

    private int safeDuration(Integer durationMinutes) {
        return Math.max(1, durationMinutes == null ? 1 : durationMinutes);
    }

    private String extractField(String block, String fieldName) {
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(fieldName) + "\\s*[:：]\\s*(.*?)\\s*$").matcher(nullToEmpty(block));
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private Integer parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(nullToEmpty(value).replaceAll("[^0-9]", ""));
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer normalizeShotDuration(Integer durationSeconds) {
        if (durationSeconds == null) {
            return 5;
        }
        return Math.max(2, Math.min(durationSeconds, 12));
    }

    private String normalizeContinuityType(String value) {
        String text = nullToEmpty(value).trim().toUpperCase();
        return switch (text) {
            case "CONTINUOUS", "SAME_SCENE", "CUT", "TRANSITION" -> text;
            default -> "CUT";
        };
    }

    private String buildStoryBriefPrompt(DramaSeriesRecord series, String requirement) {
        return """
                你是企业级短剧策划总监。请基于下面的短剧项目信息，生成一份给用户确认的故事雏形。
                只输出用户能直接阅读和编辑的故事策划内容，不要输出提示词。
                必须包含：核心故事一句话、主要角色、故事主线、核心冲突、爽点设计、反转悬念、生产方向。

                项目名称：%s
                类型：%s
                简介：%s
                题材：%s
                风格：%s
                总集数：%s
                单集时长：%s 分钟
                现有故事原文：%s
                用户补充要求：%s
                """.formatted(series.name(), series.type(), nullToDefault(series.intro(), "暂无"), nullToDefault(series.theme(), "暂无"), nullToDefault(series.style(), "快节奏、强冲突、强反转"), series.totalEpisodes(), series.episodeDurationMinutes(), nullToDefault(series.originalStory(), "暂无"), nullToDefault(requirement, "暂无"));
    }

    private String buildStoryGenerationPrompt(DramaSeriesRecord series, String storyBrief, String requirement) {
        return """
                你是企业级短剧编剧。请基于用户确认后的故事雏形和项目信息，只生成短剧项目的故事原文和故事摘要。
                当前是第一步创作页，不要生成完整故事大纲，不要生成分集大纲，不要生成每集摘要。
                输出格式必须严格如下，不要输出任何额外解释：
                <ORIGINAL_STORY>
                这里输出故事原文。它必须是小说式/故事正文，不是分集大纲，不是分场剧本，不是项目设定表。
                可以使用自然段或“第一章、第二章”这种小说章节，但严禁出现“第1集”“第01集”“第几集”“本集”“下一集”等分集字样。
                </ORIGINAL_STORY>
                <STORY_SUMMARY>
                这里输出整部短剧的总摘要，只能是 1 到 3 句话。不要写每集摘要，不要列分集。
                </STORY_SUMMARY>

                用户确认后的故事雏形：%s
                项目名称：%s
                类型：%s
                简介：%s
                题材：%s
                风格：%s
                总集数：%s
                单集时长：%s 分钟
                现有故事原文：%s
                用户补充要求：%s
                """.formatted(nullToDefault(storyBrief, "暂无"), series.name(), series.type(), nullToDefault(series.intro(), "暂无"), nullToDefault(series.theme(), "暂无"), nullToDefault(series.style(), "快节奏、强冲突、强反转"), series.totalEpisodes(), series.episodeDurationMinutes(), nullToDefault(series.originalStory(), "暂无"), nullToDefault(requirement, "暂无"));
    }

    private String buildStoryRewritePrompt(DramaSeriesRecord series, String storySummary, String originalStory, String question, List<DramaStoryAssistantChatRequest.Message> history) {
        return """
                你是专业短剧故事和小说原文修改助手。你必须直接改写故事原文全文，并返回修改后的完整故事原文。
                能力边界：只能处理短剧故事、小说原文、剧情结构、角色塑造、人物关系、冲突设计、爽点反转、悬念、节奏、文风润色和改写。
                如果用户要求无关内容，不要改写原文。
                不要解释太多，不要输出聊天废话。
                输出格式必须严格如下：
                <UPDATED_STORY>
                这里放修改后的完整故事原文。必须保留未要求删除的原剧情，不要只返回片段。
                </UPDATED_STORY>
                <CHANGE_SUMMARY>
                用 1 到 3 句话简要说明本次改了什么。
                </CHANGE_SUMMARY>

                项目名称：%s
                类型：%s
                风格：%s
                当前故事摘要：%s
                最近对话：%s
                用户修改要求：%s
                当前故事原文：%s
                """.formatted(series.name(), series.type(), nullToDefault(series.style(), "暂无"), limitText(storySummary, 1200), formatAssistantHistory(history), question, limitText(originalStory, 16000));
    }

    private String buildStoryPlanPrompt(DramaSeriesRecord series, String storySummary, String originalStory, String question, List<DramaStoryAssistantChatRequest.Message> history) {
        return """
                你是专业短剧故事和小说原文修改策划。当前处于计划模式：只能先给修改方案，不能改写故事原文全文。
                请基于用户要求和当前故事原文，输出一份用户确认前可阅读的修改方案。
                方案必须具体、可执行，包含：修改目标、保留内容、重点改动、预计对剧情/节奏/人物的影响、执行步骤。
                不要输出修改后的全文，不要直接改写原文。

                项目名称：%s
                类型：%s
                风格：%s
                当前故事摘要：%s
                最近对话：%s
                用户要求：%s
                当前故事原文：%s
                """.formatted(series.name(), series.type(), nullToDefault(series.style(), "暂无"), limitText(storySummary, 1200), formatAssistantHistory(history), question, limitText(originalStory, 16000));
    }

    private String buildStoryAnswerPrompt(DramaSeriesRecord series, String storySummary, String originalStory, String question, List<DramaStoryAssistantChatRequest.Message> history) {
        return """
                你是专业短剧故事和小说原文分析助手。用户现在不是要求你改写原文，而是让你基于当前故事原文回答问题。
                能力边界：只能回答短剧故事、小说原文、剧情结构、角色塑造、人物关系、冲突设计、爽点反转、悬念、节奏和文风相关问题。
                不要改写故事原文，不要输出修改后的全文。
                回答要直接、具体、简洁；如果用户问“总结下讲了什么”，请概括故事主线、主角处境、核心冲突和主要看点。

                项目名称：%s
                类型：%s
                风格：%s
                当前故事摘要：%s
                最近对话：%s
                用户问题：%s
                当前故事原文：%s
                """.formatted(series.name(), series.type(), nullToDefault(series.style(), "暂无"), limitText(storySummary, 1200), formatAssistantHistory(history), question, limitText(originalStory, 16000));
    }

    private String buildInternalStoryOutlinePrompt(DramaSeriesRecord series) {
        return """
                你是短剧制片流程里的故事统筹。请基于故事原文和故事摘要，生成一份内部完整故事大纲，用于下一步拆分分集。
                这是内部生产蓝图，不直接展示在第一步故事页。不要逐集列出第几集讲什么。
                大纲必须包含：主题表达、世界观/背景、核心角色和人物关系、主线目标、核心冲突、三幕或四幕结构、关键反转、结局方向、后续拆分分集时应遵守的原则。

                项目名称：%s
                类型：%s
                风格：%s
                故事摘要：%s
                故事原文：%s
                """.formatted(series.name(), series.type(), nullToDefault(series.style(), "快节奏、强冲突、强反转"), nullToDefault(series.storySummary(), "暂无"), nullToDefault(series.originalStory(), "暂无"));
    }
    private String buildEpisodeBreakdownPrompt(DramaSeriesRecord series, GenerateRequest request, List<String> historicalRequirements) {
        int maxEpisodes = resolveEpisodeUpperLimit(series, request);
        return """
                你是企业级短剧编剧导演和故事统筹。请基于整部故事原文、故事摘要和项目信息，专业拆分分集大纲。

                核心规则：
                1. 项目总集数只是建议上限，不是必须生成的目标。
                2. 如果故事原文体量和剧情容量不足以拆到建议集数，不要强行注水，不要硬拆。
                3. 每一集必须有独立的戏剧任务、情绪推进、人物关系变化或信息揭示。
                4. 每一集必须有短剧开场钩子和结尾悬念，适合后续继续生成单集正文、剧本、场景和镜头。
                5. 如果故事确实不适合拆分，请返回 1 到 2 集，不要为了凑数制造重复内容。
                6. 输出必须严格使用下面标签格式，不要输出 Markdown 表格，不要输出额外解释。
                7. 如果这是重新拆分，必须参考历史拆分要求记忆，避免重复违反用户已经提出过的要求。

                <STORY_OUTLINE>
                这里输出内部故事大纲，用于指导后续单集正文和剧本生成。包含：整部故事主线、主要角色关系、关键冲突、反转节点、结局方向、分集拆分原则。
                </STORY_OUTLINE>
                <EPISODES>
                <EPISODE>
                <TITLE>这里输出本集标题，不要包含“第几集”前缀</TITLE>
                <SUMMARY>这里输出本集剧情摘要，说明本集从哪里开始、推进了什么、解决或留下了什么</SUMMARY>
                <HOOK>这里输出本集开场钩子，适合前 3 到 8 秒抓住观众</HOOK>
                <CLIFFHANGER>这里输出本集结尾悬念或情绪钩子</CLIFFHANGER>
                </EPISODE>
                </EPISODES>

                项目名称：%s
                类型：%s
                简介：%s
                题材：%s
                风格：%s
                项目建议总集数上限：%s
                单集时长参考：%s 分钟
                本次拆分/重新拆分要求：%s
                历史拆分要求记忆：%s
                故事摘要：%s
                故事原文：%s
                """.formatted(
                series.name(),
                series.type(),
                nullToDefault(series.intro(), "暂无"),
                nullToDefault(series.theme(), "暂无"),
                nullToDefault(series.style(), "快节奏、强冲突、强反转"),
                maxEpisodes,
                series.episodeDurationMinutes(),
                request == null ? "暂无" : nullToDefault(request.instruction(), "暂无"),
                formatEpisodeBreakdownRequirementMemory(historicalRequirements),
                nullToDefault(series.storySummary(), "暂无"),
                limitText(series.originalStory(), 24000)
        );
    }
    private String buildEpisodeScriptPrompt(
            DramaSeriesRecord series,
            DramaEpisodeRecord episode,
            List<DramaEpisodeRecord> episodes,
            List<DramaCharacterRecord> characters,
            String regenerateReason
    ) {
        return """
                你是企业级短剧编剧、导演和制片统筹。请把“当前本集小说正文”改编成可拍摄的单集剧本。

                最高优先级规则：
                1. 当前本集小说正文是最高优先级素材，剧本必须围绕它改编，不能另写一个新故事。
                2. 如果本集小说正文与分集摘要、故事总纲、故事原文存在细节冲突，以“当前本集小说正文”为准。
                3. 项目故事原文、故事摘要、内部分集大纲只用于校验人物、世界观、前后集连续性，不能替代本集正文重新发明剧情。
                4. 重新生成原因只能调整表达方式、节奏、结构或指定细节，不能改变主线、人物身份、核心事件和本集结局。
                5. 不允许新增数据库资料之外的核心人物、核心设定、核心反转。
                6. 不允许把后续集剧情提前写进本集，不允许泄露后续核心反转。

                输出要求：
                1. 必须只输出下面标签格式，不要输出额外解释。
                2. 剧本必须是“单集可拍摄剧本”，不是故事摘要，不是分集大纲。
                3. 必须包含场次、地点、时间、出场人物、动作、对白、情绪节奏和结尾钩子。
                4. 当前集时长约 %s 分钟，请按短剧节奏写，开头 5 秒要有强冲突或强疑问。
                5. 每场戏都要能追溯到“当前本集小说正文”里的事件，不要脱离正文。

                <EPISODE_SCRIPT>
                这里输出完整单集剧本。
                </EPISODE_SCRIPT>

                项目信息：
                名称：%s
                类型：%s
                简介：%s
                题材：%s
                风格：%s
                计划总集数：%s
                单集时长：%s 分钟

                故事资料：
                故事摘要：%s
                内部故事大纲：%s
                故事原文：%s

                当前分集：
                第 %s 集
                标题：%s
                分集摘要：%s
                开场钩子：%s
                结尾悬念：%s

                当前本集小说正文（剧本必须以这部分为主）：
                %s

                全部分集列表（只用于连续性校验）：
                %s

                项目角色：
                %s

                用户重新生成原因：
                %s
                """.formatted(
                series.episodeDurationMinutes(),
                series.name(),
                series.type(),
                nullToDefault(series.intro(), "暂无"),
                nullToDefault(series.theme(), "暂无"),
                nullToDefault(series.style(), "快节奏、强冲突、强反转"),
                series.totalEpisodes(),
                series.episodeDurationMinutes(),
                nullToDefault(series.storySummary(), "暂无"),
                limitText(series.fullStory(), 5000),
                limitText(series.originalStory(), 18000),
                episode.episodeNo(),
                nullToDefault(episode.title(), "暂无"),
                nullToDefault(episode.summary(), "暂无"),
                nullToDefault(episode.hook(), "暂无"),
                nullToDefault(episode.cliffhanger(), "暂无"),
                limitText(episode.novelContent(), 14000),
                formatEpisodesForPrompt(episodes),
                formatCharactersForPrompt(characters),
                nullToDefault(regenerateReason, "暂无")
        );
    }

    private String buildEpisodeNovelPrompt(
            DramaSeriesRecord series,
            DramaEpisodeRecord episode,
            List<DramaEpisodeRecord> episodes,
            List<DramaCharacterRecord> characters,
            String regenerateReason
    ) {
        return """
                你是专业短剧小说改编编剧。请根据“项目故事原文”和“当前分集边界”，扩写当前这一集的小说正文。

                最高优先级规则：
                1. 必须写同一个故事里的当前分集，不能另起炉灶，不能换主角，不能换世界观。
                2. 项目故事原文是主线依据；当前分集标题、摘要、开场钩子、结尾悬念是本集边界。
                3. 本集正文必须只覆盖当前第 %s 集应该发生的剧情，不要提前写后续集核心内容。
                4. 重新生成原因只能调整本集表达、篇幅、节奏和重点；不能推翻项目故事原文、角色设定和分集主线。
                5. 如需补充细节，只能补充能服务于当前分集摘要的动作、心理、环境、冲突推进，不得新增核心设定。
                6. 不允许新增核心设定、核心人物或无关支线。

                输出要求：
                1. 必须只输出下面标签格式，不要输出额外解释。
                2. 正文必须是小说式叙事，不是分场剧本，不是对白脚本，不是镜头表。
                3. 可以自然分段，重点写人物行动、心理、情绪、冲突推进和关键反转。
                4. 正文需要为后续剧本、场景拆分、镜头拆分提供足够细节。
                5. 风格适合短剧/漫剧后续生产：冲突明确、节奏紧、画面感强。

                <EPISODE_NOVEL>
                这里输出本集小说正文。
                </EPISODE_NOVEL>

                项目信息：
                名称：%s
                类型：%s
                简介：%s
                题材：%s
                风格：%s
                计划总集数：%s
                单集时长：%s 分钟

                故事资料：
                故事摘要：%s
                内部故事大纲：%s
                项目故事原文（必须保持同一主线）：
                %s

                当前分集边界：
                第 %s 集
                标题：%s
                分集摘要：%s
                开场钩子：%s
                结尾悬念：%s

                全部分集列表（用于判断当前集在全剧位置）：
                %s

                项目角色：
                %s

                用户重新生成原因：
                %s
                """.formatted(
                episode.episodeNo(),
                series.name(),
                series.type(),
                nullToDefault(series.intro(), "暂无"),
                nullToDefault(series.theme(), "暂无"),
                nullToDefault(series.style(), "快节奏、强冲突、强反转"),
                series.totalEpisodes(),
                series.episodeDurationMinutes(),
                nullToDefault(series.storySummary(), "暂无"),
                limitText(series.fullStory(), 6000),
                limitText(series.originalStory(), 26000),
                episode.episodeNo(),
                nullToDefault(episode.title(), "暂无"),
                nullToDefault(episode.summary(), "暂无"),
                nullToDefault(episode.hook(), "暂无"),
                nullToDefault(episode.cliffhanger(), "暂无"),
                formatEpisodesForPrompt(episodes),
                formatCharactersForPrompt(characters),
                nullToDefault(regenerateReason, "暂无")
        );
    }

    private StoryContent parseStoryContent(String generatedText) {
        String text = nullToEmpty(generatedText);
        return new StoryContent(extractTag(text, "ORIGINAL_STORY"), extractTag(text, "STORY_SUMMARY"), "");
    }

    private String parseEpisodeScript(String generatedText) {
        String text = nullToEmpty(generatedText);
        String script = extractTagLenient(text, "EPISODE_SCRIPT", null);
        if (hasText(script)) {
            return script.trim();
        }
        String normalized = text.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        return normalized;
    }

    private String parseEpisodeNovelContent(String generatedText) {
        String text = nullToEmpty(generatedText);
        String novelContent = extractTagLenient(text, "EPISODE_NOVEL", null);
        if (hasText(novelContent)) {
            return novelContent.trim();
        }
        String normalized = text.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        return normalized;
    }

    private StoryRewriteResult parseStoryRewriteResult(String generatedText) {
        String text = nullToEmpty(generatedText);
        String updatedStory = extractTagLenient(text, "UPDATED_STORY", "CHANGE_SUMMARY");
        String changeSummary = extractTagLenient(text, "CHANGE_SUMMARY", null);
        if (!hasText(updatedStory)) {
            updatedStory = normalizeUntaggedRewriteText(text);
            if (hasText(updatedStory) && !hasText(changeSummary)) {
                changeSummary = "已根据你的要求修改故事原文；本次模型没有按标签返回，系统已按完整正文接收。";
            }
        }
        return new StoryRewriteResult(updatedStory, changeSummary);
    }

    private EpisodeBreakdown parseEpisodeBreakdown(String generatedText) {
        String text = nullToEmpty(generatedText);
        String storyOutline = extractTag(text, "STORY_OUTLINE");
        String episodesText = extractTag(text, "EPISODES");
        if (!hasText(episodesText)) {
            return new EpisodeBreakdown(storyOutline, List.of());
        }
        Matcher matcher = Pattern.compile("(?s)<EPISODE>(.*?)</EPISODE>").matcher(episodesText);
        List<GeneratedEpisode> episodes = new ArrayList<>();
        while (matcher.find()) {
            String block = matcher.group(1);
            String title = cleanupEpisodeField(extractTag(block, "TITLE"));
            String summary = cleanupEpisodeField(extractTag(block, "SUMMARY"));
            String hook = cleanupEpisodeField(extractTag(block, "HOOK"));
            String cliffhanger = cleanupEpisodeField(extractTag(block, "CLIFFHANGER"));
            if (!hasText(title) || !hasText(summary) || !hasText(hook) || !hasText(cliffhanger)) {
                throw new BusinessException(500, "分集拆解失败：模型返回的分集字段不完整");
            }
            episodes.add(new GeneratedEpisode(title, summary, hook, cliffhanger));
        }
        return new EpisodeBreakdown(storyOutline, episodes);
    }

    private String cleanupEpisodeField(String value) {
        return nullToEmpty(value)
                .replaceAll("(?i)^第\\s*\\d+\\s*集[：:、《\\s-]*", "")
                .trim();
    }

    private String extractTag(String text, String tag) {
        String startTag = "<" + tag + ">";
        String endTag = "</" + tag + ">";
        int start = text.indexOf(startTag);
        int end = text.indexOf(endTag);
        if (start < 0 || end <= start) {
            return "";
        }
        return text.substring(start + startTag.length(), end).trim();
    }

    private String extractTagLenient(String text, String tag, String nextTag) {
        String strictValue = extractTag(text, tag);
        if (hasText(strictValue)) {
            return strictValue;
        }
        String startTag = "<" + tag + ">";
        int start = text.indexOf(startTag);
        if (start < 0) {
            return "";
        }
        int contentStart = start + startTag.length();
        int end = text.length();
        if (hasText(nextTag)) {
            int nextTagStart = text.indexOf("<" + nextTag + ">", contentStart);
            if (nextTagStart > contentStart) {
                end = nextTagStart;
            }
        }
        return text.substring(contentStart, end).trim();
    }

    private String normalizeUntaggedRewriteText(String text) {
        String normalized = nullToEmpty(text).trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int summaryIndex = normalized.indexOf("<CHANGE_SUMMARY>");
        if (summaryIndex > 0) {
            normalized = normalized.substring(0, summaryIndex).trim();
        }
        if (!hasText(normalized) || normalized.length() < 80) {
            return "";
        }
        return normalized;
    }

    private StoryContent buildFallbackStoryContent(DramaSeriesRecord series, String storyBrief, String requirement) {
        String style = nullToDefault(series.style(), "快节奏、强冲突、强反转");
        String originalStory = "【故事原文】\n"
                + "第一章：命运的裂缝\n"
                + series.name() + " 的故事从一次突如其来的危机开始。主角原本只是困在普通生活里的人，却因为一场误会、一次背叛或一个被隐藏的秘密，被推到所有人目光的中心。对手不断施压，身边的人各怀心事，主角只能在屈辱和怀疑中寻找真相。\n\n"
                + "第二章：反击的线索\n"
                + "随着关键线索逐渐浮出水面，主角发现眼前的困境并不是偶然，而是长期被设计、被隐瞒、被操控的结果。亲密关系开始动摇，旧日恩怨重新出现，主角也从被动承受转向主动追问。\n\n"
                + "第三章：真相与选择\n"
                + "当反派继续加码，主角必须在情感、利益和尊严之间做出选择。最终，主角用证据、能力或身份完成反击，让被掩盖的真相暴露出来，也让人物关系完成一次彻底重排。\n\n"
                + "【用户确认后的故事雏形】\n" + nullToDefault(storyBrief, "暂无") + "\n\n"
                + "【项目风格】\n" + style + "\n\n"
                + "【补充要求】\n" + nullToDefault(requirement, "暂无");
        String storySummary = "《" + series.name() + "》围绕“" + series.type() + "”类型展开，讲述主角在强压处境中寻找真相、完成反击，并通过连续冲突和反转实现情绪兑现的故事。";
        return new StoryContent(originalStory, storySummary, "");
    }

    private String buildFallbackStoryOutline(DramaSeriesRecord series) {
        String style = nullToDefault(series.style(), "快节奏、强冲突、强反转");
        return "【内部完整故事大纲】\n"
                + "项目名称：" + series.name() + "\n"
                + "类型：" + series.type() + "\n"
                + "风格：" + style + "\n\n"
                + "一、主题表达：用高压困境和情绪反击，完成观众对公平、尊严和真相的情绪期待。\n"
                + "二、核心角色：主角承载代入和成长，反派制造持续压力，关键配角提供误会、帮助或反转。\n"
                + "三、故事主线：主角从被动卷入冲突开始，通过寻找线索、识破谎言、修正关系，最终揭开真相并完成反击。\n"
                + "四、核心冲突：外部压迫、人物误解、隐藏秘密和利益争夺共同推动故事。\n"
                + "五、结构设计：开端建立强困境，中段持续升级压力并释放线索，后段集中反转和情绪兑现。\n"
                + "六、反转策略：每个关键阶段只揭开一部分真相，保留身份、证据、动机和关系上的二次反转。\n"
                + "七、拆分原则：后续生成分集大纲时，再把这份完整故事大纲拆成具体集数、每集摘要、开场钩子和结尾悬念。";
    }

    private String buildFallbackStoryBrief(DramaSeriesRecord series, String requirement) {
        return "【核心故事一句话】\n"
                + "《" + series.name() + "》是一部 " + series.type() + " 短剧，讲述主角在低位困境中被误解、被压制，随后依靠隐藏能力、关键证据或身份反转完成反击的故事。\n\n"
                + "【主要角色】\n主角：处在情绪低谷但具备反击潜力的人物。\n关键配角：既能提供帮助，也可能制造误会或隐藏信息。\n主要反派：持续给主角施压，推动冲突升级。\n\n"
                + "【故事主线】\n故事从一次高压事件切入，主角被迫卷入核心矛盾。前期用误会和压迫制造代入，中段通过线索推进和人物关系反转提升爽点，后段完成身份、真相或情感的集中兑现。\n\n"
                + "【用户补充要求】\n" + nullToDefault(requirement, "暂无");
    }

    private String formatAssistantHistory(List<DramaStoryAssistantChatRequest.Message> history) {
        if (history == null || history.isEmpty()) {
            return "暂无";
        }
        return history.stream()
                .filter(message -> message != null && hasText(message.content()))
                .limit(8)
                .map(message -> nullToDefault(message.role(), "user") + "：" + limitText(message.content(), 500))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("暂无");
    }

    private String formatEpisodeBreakdownRequirementMemory(List<String> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return "暂无";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = requirements.size() - 1; i >= 0; i--) {
            String requirement = requirements.get(i);
            if (hasText(requirement)) {
                builder.append(requirements.size() - i).append(". ").append(limitText(requirement, 500)).append("\n");
            }
        }
        return builder.isEmpty() ? "暂无" : builder.toString().trim();
    }

    private String formatEpisodesForPrompt(List<DramaEpisodeRecord> episodes) {
        if (episodes == null || episodes.isEmpty()) {
            return "暂无";
        }
        return episodes.stream()
                .map(episode -> "第 %s 集《%s》：%s；开场：%s；结尾：%s".formatted(
                        episode.episodeNo(),
                        nullToDefault(episode.title(), "未命名"),
                        limitText(episode.summary(), 500),
                        limitText(episode.hook(), 240),
                        limitText(episode.cliffhanger(), 240)
                ))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("暂无");
    }

    private String formatCharactersForPrompt(List<DramaCharacterRecord> characters) {
        if (characters == null || characters.isEmpty()) {
            return "暂无";
        }
        return characters.stream()
                .map(character -> """
                        角色：%s
                        人设：%s
                        外貌：%s
                        服装：%s
                        性格：%s
                        关系：%s
                        """.formatted(
                        character.name(),
                        nullToDefault(character.profile(), "暂无"),
                        nullToDefault(character.appearance(), "暂无"),
                        nullToDefault(character.costume(), "暂无"),
                        nullToDefault(character.personality(), "暂无"),
                        nullToDefault(character.relationship(), "暂无")
                ).trim())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("暂无");
    }

    private String formatScenesForPrompt(List<DramaSceneRecord> scenes) {
        if (scenes == null || scenes.isEmpty()) {
            return "暂无";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < scenes.size(); i++) {
            DramaSceneRecord scene = scenes.get(i);
            builder.append("场景").append(i + 1).append("：")
                    .append(nullToDefault(scene.name(), "未命名场景"))
                    .append("\n地点：").append(nullToDefault(scene.location(), "未设置"))
                    .append("\n时间：").append(nullToDefault(scene.timeOfDay(), "未设置"))
                    .append("\n氛围：").append(nullToDefault(scene.atmosphere(), "未设置"))
                    .append("\n剧情目的：").append(nullToDefault(scene.plotPurpose(), "未设置"));
            if (i < scenes.size() - 1) {
                builder.append("\n\n");
            }
        }
        return builder.toString();
    }

    private String formatShotsForPrompt(List<DramaShotRecord> shots) {
        if (shots == null || shots.isEmpty()) {
            return "暂无";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < shots.size(); i++) {
            DramaShotRecord shot = shots.get(i);
            builder.append("镜头").append(shot.shotNo())
                    .append("\n场景ID：").append(shot.sceneId() == null ? "未设置" : shot.sceneId())
                    .append("\n景别：").append(nullToDefault(shot.shotSize(), "未设置"))
                    .append("\n预计时长：").append(shot.durationSeconds() == null ? "未设置" : shot.durationSeconds() + "秒")
                    .append("\n运镜：").append(nullToDefault(shot.cameraMovement(), "未设置"))
                    .append("\n构图：").append(nullToDefault(shot.composition(), "未设置"))
                    .append("\n转场：").append(nullToDefault(shot.transitionType(), "未设置"))
                    .append("\nContinuity type: ").append(nullToDefault(shot.continuityType(), "CUT"))
                    .append("\nShot start state: ").append(nullToDefault(shot.startState(), "none"))
                    .append("\nShot end state: ").append(nullToDefault(shot.endState(), "none"))
                    .append("\nContinuity note: ").append(nullToDefault(shot.continuityNote(), "none"))
                    .append("\n音效：").append(nullToDefault(shot.soundEffect(), "未设置"))
                    .append("\n配乐：").append(nullToDefault(shot.musicCue(), "未设置"))
                    .append("\n旁白：").append(nullToDefault(shot.voiceOver(), "无"))
                    .append("\n动作：").append(nullToDefault(shot.action(), "未设置"))
                    .append("\n当前台词：").append(nullToDefault(shot.dialogue(), "暂无"))
                    .append("\n图片提示词：").append(nullToDefault(shot.imagePrompt(), "暂无"))
                    .append("\n视频提示词：").append(nullToDefault(shot.videoPrompt(), "暂无"));
            if (i < shots.size() - 1) {
                builder.append("\n\n");
            }
        }
        return builder.toString();
    }

    private boolean isDialogueReady(String dialogue) {
        String text = nullToEmpty(dialogue).trim();
        return hasText(text) && !"暂无".equals(text);
    }

    private boolean isStoryAssistantQuestion(String question) {
        String text = question.toLowerCase();
        if (isStoryEditCommand(text)) {
            return true;
        }
        String[] keywords = {
                "故事", "小说", "短剧", "剧情", "剧本", "原文", "摘要", "章节", "开头", "结尾", "主线", "支线",
                "角色", "人物", "主角", "反派", "配角", "人设", "关系", "动机", "成长", "冲突", "矛盾",
                "爽点", "反转", "悬念", "节奏", "情绪", "钩子", "伏笔", "高潮", "对白", "台词", "文风",
                "润色", "改写", "修改", "优化", "扩写", "续写", "压缩", "删减", "场景", "镜头", "分集", "大纲"
        };
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isClearlyOutOfStoryScope(String question) {
        String text = question.toLowerCase();
        String[] outOfScopeKeywords = {
                "java", "spring", "mysql", "redis", "sql", "linux", "docker", "kubernetes", "nacos", "gateway",
                "代码", "接口", "数据库", "服务器", "部署", "报错", "bug", "股票", "基金", "天气", "新闻", "数学题",
                "翻译成英文", "写周报", "写简历", "做表格", "excel", "ppt"
        };
        for (String keyword : outOfScopeKeywords) {
            if (text.contains(keyword)) {
                return !containsStoryContext(text);
            }
        }
        return false;
    }

    private boolean containsStoryContext(String text) {
        String[] storyContextKeywords = {
                "故事", "小说", "短剧", "剧情", "剧本", "原文", "角色", "人物", "主角", "反派", "爽点", "反转", "悬念", "章节"
        };
        for (String keyword : storyContextKeywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStoryReadOnlyQuestion(String question) {
        String text = question.toLowerCase();
        String[] readActions = {
                "总结", "概括", "讲了什么", "说了什么", "主要内容", "核心内容", "大概内容", "分析", "评价", "点评", "看一下", "帮我看",
                "哪里有问题", "有什么问题", "好不好", "合理吗", "主线是什么", "冲突是什么", "主角是谁", "人物关系", "爽点", "反转", "悬念", "节奏"
        };
        for (String action : readActions) {
            if (text.contains(action)) {
                return true;
            }
        }
        return text.matches(".*(这个故事|这篇|这段).*(讲|说|写).*(什么|啥).*");
    }

    private boolean isStoryEditCommand(String text) {
        String[] editActions = {
                "改", "改成", "改到", "修改", "改写", "优化", "润色", "扩写", "续写", "缩写", "压缩", "精简", "删减", "调整", "整理",
                "加", "增加", "添加", "补充", "补", "减少", "删", "不用那么复杂", "简单点", "复杂点"
        };
        String[] lengthTargets = {
                "字", "千字", "万字", "百字", "两百字", "一百字", "长度", "篇幅", "短一点", "长一点", "详细一点", "简短一点",
                "控制在", "不少于", "不超过", "左右", "简单点", "复杂点", "不用那么复杂"
        };
        boolean hasEditAction = false;
        for (String action : editActions) {
            if (text.contains(action)) {
                hasEditAction = true;
                break;
            }
        }
        if (!hasEditAction) {
            return false;
        }
        for (String target : lengthTargets) {
            if (text.contains(target)) {
                return true;
            }
        }
        return text.matches(".*\\d+\\s*(字|个字|千字|万字).*");
    }

    private boolean isModelFallback(String value) {
        String text = nullToEmpty(value);
        return text.isBlank()
                || text.contains("妯″瀷閰嶇疆")
                || text.contains("模型调用失败")
                || text.contains("待接入真实文本模型")
                || text.contains("DRAMA_TEXT_BASE_URL");
    }

    private String preserveEpisodeStatus(String status) {
        return nullToDefault(status, "OUTLINE_READY");
    }

    private void ensureEpisodeStepReached(String currentStatus, String requiredStatus, String message) {
        if (stepRank(currentStatus) < stepRank(requiredStatus)) {
            throw new BusinessException(400, message);
        }
    }

    private int stepRank(String status) {
        return switch (nullToDefault(status, "OUTLINE_READY")) {
            case "NOVEL_READY" -> 1;
            case "SCRIPT_READY" -> 2;
            case "SCENE_READY" -> 3;
            case "SHOT_READY" -> 4;
            case "DIALOGUE_READY" -> 5;
            case "IMAGE_READY" -> 6;
            case "VIDEO_READY" -> 7;
            default -> 0;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String limitText(String value, int maxLength) {
        String text = nullToEmpty(value).trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...（已截断）";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String nullToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String safeFileName(String value) {
        String safe = value == null ? "未命名" : value.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return safe.isBlank() ? "未命名" : safe;
    }

    private String productionImageSize(DramaSeriesRecord series) {
        return isLandscape(series) ? "1792x1024" : "1024x1792";
    }

    private String productionCompositionRequirement(DramaSeriesRecord series) {
        if (isLandscape(series)) {
            return "Horizontal manhua/comic-drama frame, 16:9 landscape composition, 1792x1024 aspect ratio, suitable for comic-panel storytelling and later video pan/zoom editing.";
        }
        return "Vertical short-drama frame, 9:16 portrait composition, 1024x1792 aspect ratio, suitable for Douyin full-screen mobile viewing.";
    }

    private boolean isLandscape(DramaSeriesRecord series) {
        return series != null && "LANDSCAPE_16_9".equalsIgnoreCase(nullToEmpty(series.aspectRatio()));
    }

    private Long primaryShotReferenceAssetId(List<DramaCharacterRecord> characters) {
        List<Long> ids = shotReferenceAssetIds(characters);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private List<Long> shotReferenceAssetIds(List<DramaCharacterRecord> characters) {
        if (characters == null || characters.isEmpty()) {
            return List.of();
        }
        return characters.stream()
                .map(character -> character.primaryReferenceAssetId() == null ? character.avatarAssetId() : character.primaryReferenceAssetId())
                .filter(id -> id != null)
                .distinct()
                .limit(4)
                .toList();
    }

    private int resolveEpisodeUpperLimit(DramaSeriesRecord series, GenerateRequest request) {
        int configuredCount = request == null || request.count() == null || request.count() <= 0
                ? series.totalEpisodes()
                : request.count();
        return Math.max(1, Math.min(configuredCount, 100));
    }

    private record StoryContent(String originalStory, String storySummary, String fullStory) {
        private boolean hasMissingUserVisibleContent() {
            return originalStory.isBlank() || storySummary.isBlank();
        }
    }

    private record StoryRewriteResult(String updatedStory, String changeSummary) {
    }

    private record EpisodeBreakdown(String storyOutline, List<GeneratedEpisode> episodes) {
    }

    private record GeneratedEpisode(String title, String summary, String hook, String cliffhanger) {
    }

    private record GeneratedScene(String name, String location, String timeOfDay, String atmosphere, String plotPurpose) {
    }

    private record GeneratedShot(
            Integer sceneIndex,
            String shotSize,
            Integer durationSeconds,
            String cameraMovement,
            String composition,
            String transitionType,
            String continuityType,
            String startState,
            String endState,
            String continuityNote,
            String soundEffect,
            String musicCue,
            String voiceOver,
            String action,
            String dialogue,
            String imagePrompt,
            String videoPrompt
    ) {
    }

    private record GeneratedDialogue(Integer shotNo, String dialogue) {
    }
}





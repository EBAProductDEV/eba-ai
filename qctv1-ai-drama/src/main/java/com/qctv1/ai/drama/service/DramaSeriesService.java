package com.qctv1.ai.drama.service;

import com.qctv1.ai.drama.domain.DramaAssetRecord;
import com.qctv1.ai.drama.domain.DramaCharacterRecord;
import com.qctv1.ai.drama.domain.DramaEpisodeRecord;
import com.qctv1.ai.drama.domain.DramaImageGenerationContext;
import com.qctv1.ai.drama.domain.DramaSceneRecord;
import com.qctv1.ai.drama.domain.DramaSeriesRecord;
import com.qctv1.ai.drama.domain.DramaTaskRecord;
import com.qctv1.ai.drama.dto.DramaCharacterImageGenerateRequest;
import com.qctv1.ai.drama.dto.DramaCharacterSaveRequest;
import com.qctv1.ai.drama.dto.DramaSeriesCreateRequest;
import com.qctv1.ai.drama.dto.DramaStorySaveRequest;
import com.qctv1.ai.drama.provider.TextGenerationClient;
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
import com.qctv1.ai.drama.vo.DramaShotVo;
import com.qctv1.ai.drama.vo.DramaTaskVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DramaSeriesService {

    private static final Long DEFAULT_USER_ID = 1L;
    private static final Map<String, String> CHARACTER_IMAGE_TYPE_NAMES = Map.of(
            "AVATAR", "头像",
            "PORTRAIT", "定妆图",
            "THREE_VIEW", "三视图",
            "EXPRESSION", "表情参考图",
            "COSTUME", "服装版本图"
    );

    private final DramaSeriesRepository seriesRepository;
    private final DramaWorkflowRepository workflowRepository;
    private final DramaAssetRepository assetRepository;
    private final DramaCharacterRepository characterRepository;
    private final DramaAssetService assetService;
    private final DramaImageGenerationService imageGenerationService;
    private final TextGenerationClient textGenerationClient;

    public DramaSeriesService(
            DramaSeriesRepository seriesRepository,
            DramaWorkflowRepository workflowRepository,
            DramaAssetRepository assetRepository,
            DramaCharacterRepository characterRepository,
            DramaAssetService assetService,
            DramaImageGenerationService imageGenerationService,
            TextGenerationClient textGenerationClient
    ) {
        this.seriesRepository = seriesRepository;
        this.workflowRepository = workflowRepository;
        this.assetRepository = assetRepository;
        this.characterRepository = characterRepository;
        this.assetService = assetService;
        this.imageGenerationService = imageGenerationService;
        this.textGenerationClient = textGenerationClient;
    }

    @Transactional
    public DramaSeriesSummaryVo create(DramaSeriesCreateRequest request) {
        DramaSeriesRecord record = seriesRepository.create(
                DEFAULT_USER_ID,
                request.name(),
                request.aspectRatio(),
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
                series.id(), series.name(), series.aspectRatio(), series.type(), series.intro(), series.theme(), series.style(),
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
        String storyStatus = originalStory.isBlank() && storySummary.isBlank() ? "NOT_STARTED" : "READY";
        // 第一阶段只保存用户可见的故事原文和摘要。完整大纲在分集阶段重新生成，避免沿用旧大纲。
        seriesRepository.updateStory(id, originalStory, storySummary, null, storyStatus);
        return detail(id);
    }

    public List<DramaCharacterVo> listCharacters(Long seriesId) {
        ensureSeriesExists(seriesId);
        return characterRepository.listBySeries(seriesId).stream().map(this::toCharacterVo).toList();
    }

    public DramaCharacterVo characterDetail(Long seriesId, Long characterId) {
        ensureSeriesExists(seriesId);
        return characterRepository.findBySeriesAndId(seriesId, characterId)
                .map(this::toCharacterVo)
                .orElseThrow(() -> new BusinessException(404, "角色不存在或不属于该短剧项目"));
    }

    public List<DramaAssetVo> listCharacterAssets(Long seriesId, Long characterId) {
        ensureCharacterExists(seriesId, characterId);
        return assetRepository.listByCharacter(characterId, 50).stream().map(this::toAssetVo).toList();
    }

    @Transactional
    public DramaTaskVo generateCharacterImage(Long seriesId, Long characterId, DramaCharacterImageGenerateRequest request) {
        DramaCharacterRecord character = ensureCharacterExists(seriesId, characterId);
        String imageType = assetService.normalizeCharacterImageType(request == null ? null : request.imageType());
        if (!"PORTRAIT".equals(imageType) && character.primaryReferenceAssetId() == null) {
            throw new BusinessException(400, "请先生成角色定妆图。定妆图是后续头像、三视图、表情参考图和服装版本图的人物一致性基准。");
        }
        if (assetRepository.findByCharacterAndSubType(characterId, imageType).isPresent()) {
            throw new BusinessException(400, "数据已存在，请删除后再生成");
        }
        Long referenceAssetId = "PORTRAIT".equals(imageType) ? null : character.primaryReferenceAssetId();
        return imageGenerationService.submit(
                buildCharacterImageContext(character, imageType, referenceAssetId),
                () -> buildCharacterImagePrompt(character, imageType, referenceAssetId)
        );
    }

    @Transactional
    public List<DramaTaskVo> generateCharacterAuxiliaryImages(Long seriesId, Long characterId) {
        DramaCharacterRecord character = ensureCharacterExists(seriesId, characterId);
        if (character.primaryReferenceAssetId() == null) {
            throw new BusinessException(400, "请先生成角色全身定妆图，再生成辅助图。");
        }

        List<String> auxiliaryTypes = List.of("AVATAR", "THREE_VIEW", "EXPRESSION", "COSTUME");
        if (auxiliaryTypes.stream().allMatch(imageType -> assetRepository.findByCharacterAndSubType(characterId, imageType).isPresent())) {
            throw new BusinessException(400, "数据已存在，请删除后再生成");
        }
        List<DramaTaskVo> tasks = new ArrayList<>();
        for (String imageType : auxiliaryTypes) {
            if (assetRepository.findByCharacterAndSubType(characterId, imageType).isPresent()) {
                continue;
            }
            tasks.add(imageGenerationService.submit(
                    buildCharacterImageContext(character, imageType, character.primaryReferenceAssetId()),
                    () -> buildCharacterImagePrompt(character, imageType, character.primaryReferenceAssetId())
            ));
        }
        return tasks;
    }

    @Transactional
    public void deleteCharacterAsset(Long seriesId, Long characterId, Long assetId) {
        DramaCharacterRecord character = ensureCharacterExists(seriesId, characterId);
        DramaAssetRecord asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException(404, "角色图片不存在"));
        if (!seriesId.equals(asset.seriesId()) || !characterId.equals(asset.characterId())) {
            throw new BusinessException(404, "角色图片不存在或不属于该角色");
        }
        assetService.moveAssetToRecycle(asset);
        if (!assetRepository.deleteById(assetId)) {
            throw new BusinessException(404, "角色图片不存在");
        }
        characterRepository.clearImageReference(
                seriesId,
                characterId,
                assetId.equals(character.avatarAssetId()),
                assetId.equals(character.primaryReferenceAssetId())
        );
    }

    @Transactional
    public List<DramaCharacterVo> generateCharacters(Long seriesId) {
        DramaSeriesRecord series = seriesRepository.findByIdAndUserId(seriesId, DEFAULT_USER_ID)
                .orElseThrow(() -> new BusinessException(404, "短剧项目不存在"));
        if (isBlank(series.originalStory()) && isBlank(series.storySummary())) {
            throw new BusinessException(400, "请先完成第一步故事原文，再生成项目角色");
        }
        if (!characterRepository.listBySeries(seriesId).isEmpty()) {
            throw new BusinessException(400, "数据已存在，请删除后再生成");
        }

        List<GeneratedCharacter> generatedCharacters = parseGeneratedCharacters(
                textGenerationClient.generate(buildCharacterGeneratePrompt(series))
        );
        if (generatedCharacters.isEmpty()) {
            generatedCharacters = buildFallbackCharacters(series);
        }

        Set<String> existingNames = new LinkedHashSet<>();
        characterRepository.listBySeries(seriesId).forEach(character -> existingNames.add(normalizeName(character.name())));
        List<DramaCharacterVo> createdCharacters = new ArrayList<>();
        for (GeneratedCharacter character : generatedCharacters) {
            String name = nullToEmpty(character.name()).trim();
            if (name.isBlank() || existingNames.contains(normalizeName(name))) {
                continue;
            }
            existingNames.add(normalizeName(name));
            createdCharacters.add(toCharacterVo(characterRepository.create(
                    seriesId,
                    name,
                    character.profile(),
                    character.appearance(),
                    character.costume(),
                    character.personality(),
                    character.relationship()
            )));
        }
        workflowRepository.createTask(seriesId, null, null, "CHARACTERS_AI_GENERATE", "SUCCEEDED", "已根据故事原文生成项目角色 " + createdCharacters.size() + " 个");
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
    public DramaCharacterVo updateCharacter(Long seriesId, Long characterId, DramaCharacterSaveRequest request) {
        ensureCharacterExists(seriesId, characterId);
        boolean updated = characterRepository.update(
                seriesId,
                characterId,
                nullToEmpty(request.name()).trim(),
                request.profile(),
                request.appearance(),
                request.costume(),
                request.personality(),
                request.relationship()
        );
        if (!updated) {
            throw new BusinessException(404, "角色不存在或不属于该短剧项目");
        }
        return characterDetail(seriesId, characterId);
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
        List<DramaAssetVo> assets = assetRepository.listByEpisode(episodeId, 200).stream().map(this::toAssetVo).toList();
        // 图片生成会按“场景 + 镜头”批量创建任务，一集很容易超过 20 个。
        // 这里返回足够多的任务，避免前端轮询时看不到较早创建但仍在执行的任务，导致提前停止刷新。
        List<DramaTaskVo> tasks = workflowRepository.listTasksByEpisode(episodeId, 500).stream().map(this::toTaskVo).toList();
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
                record.id(), record.name(), record.aspectRatio(), record.type(), record.intro(), record.style(),
                record.totalEpisodes(), record.episodeDurationMinutes(), record.status(), record.createdAt()
        );
    }

    private DramaEpisodeVo toEpisodeVo(DramaEpisodeRecord record) {
        return new DramaEpisodeVo(
                record.id(), record.episodeNo(), record.title(), record.summary(), record.novelContent(), record.hook(),
                record.cliffhanger(), record.script(), record.status()
        );
    }

    private DramaSceneVo toSceneVo(DramaSceneRecord record) {
        return new DramaSceneVo(record.id(), record.name(), record.location(), record.timeOfDay(), record.atmosphere(), record.plotPurpose());
    }

    private DramaCharacterVo toCharacterVo(DramaCharacterRecord record) {
        return new DramaCharacterVo(
                record.id(), record.seriesId(), record.name(), record.profile(), record.appearance(),
                record.costume(), record.personality(), record.relationship(), record.visualProfile(),
                record.primaryReferenceAssetId(), record.avatarAssetId(),
                resolveAssetAccessUrl(record.primaryReferenceAssetId()),
                resolveAssetAccessUrl(record.avatarAssetId()),
                record.imageSeed(),
                record.createdAt(), record.updatedAt()
        );
    }

    private String resolveAssetAccessUrl(Long assetId) {
        if (assetId == null) {
            return null;
        }
        return assetRepository.findById(assetId)
                .map(DramaAssetRecord::accessUrl)
                .orElse(null);
    }

    private DramaShotVo toShotVo(com.qctv1.ai.drama.domain.DramaShotRecord record) {
        return new DramaShotVo(
                record.id(), record.episodeId(), record.sceneId(), record.shotNo(), record.shotSize(),
                record.durationSeconds(), record.cameraMovement(), record.composition(), record.transitionType(),
                record.soundEffect(), record.musicCue(), record.voiceOver(), record.action(),
                record.dialogue(), record.imagePrompt(), record.videoPrompt(), record.status()
        );
    }

    private DramaAssetVo toAssetVo(DramaAssetRecord record) {
        return new DramaAssetVo(
                record.id(), record.episodeId(), record.sceneId(), record.shotId(), record.assetType(), record.assetSubType(), record.characterId(),
                record.fileName(), record.contentType(), record.accessUrl(), record.status(), record.createdAt()
        );
    }

    private DramaTaskVo toTaskVo(DramaTaskRecord record) {
        return imageGenerationService.toTaskVo(record);
    }

    private DramaImageGenerationContext buildCharacterImageContext(DramaCharacterRecord character, String imageType, Long referenceAssetId) {
        try {
            Path saveDirectory = assetService.ensureSeriesRoot(character.seriesId())
                    .resolve("角色图")
                    .resolve(safeFileName("角色-" + character.id() + "-" + character.name()))
                    .resolve(CHARACTER_IMAGE_TYPE_NAMES.getOrDefault(imageType, imageType))
                    .normalize();
            String seed = character.imageSeed() == null || character.imageSeed().isBlank()
                    ? "CHAR-" + character.id() + "-" + System.currentTimeMillis()
                    : character.imageSeed();
            return new DramaImageGenerationContext(
                    character.seriesId(),
                    null,
                    null,
                    null,
                    character.id(),
                    "CHARACTER",
                    character.id(),
                    "CHARACTER_IMAGE",
                    imageType,
                    referenceAssetId,
                    referenceAssetId == null ? List.of() : List.of(referenceAssetId),
                    "",
                    seed,
                    imageType.toLowerCase(Locale.ROOT),
                    "image/svg+xml",
                    "1024x1792",
                    saveDirectory
            );
        } catch (IOException ex) {
            throw new BusinessException(500, "创建角色图片目录失败：" + ex.getMessage());
        }
    }

    private String buildCharacterImagePrompt(DramaCharacterRecord character, String imageType, Long referenceAssetId) {
        String typeRequirement = characterImageTypeRequirement(imageType);
        String roleCard = buildCharacterRoleCard(character);
        String englishRolePrompt = buildEnglishImagePromptByTextModel(roleCard, typeRequirement);
        return """
                Professional short-drama character image generation. Use the following production prompt as the highest priority. Do not create a generic character.

                Production prompt:
                %s

                Image type requirement:
                %s

                Hard rules:
                - Keep the character gender, age, era, costume and temperament from the production prompt.
                - If the production prompt describes a female character, generate female only. Never change her into a male.
                - If the production prompt describes a young girl or young woman, do not generate a middle-aged or elderly person.
                - Make this image usable as a stable production reference for later images and videos.
                - Clean white background, complete readable design, high quality film character concept art, realistic cinematic texture.
                """.formatted(
                englishRolePrompt,
                typeRequirement
        );
    }

    private String buildCharacterRoleCard(DramaCharacterRecord character) {
        return """
                角色名称：%s
                人设定位：%s
                外貌特点：%s
                常用服装：%s
                性格特点：%s
                人物关系：%s
                """.formatted(
                nullToDefault(character.name(), "未命名角色"),
                nullToDefault(character.profile(), "待补充"),
                nullToDefault(character.appearance(), "待补充"),
                nullToDefault(character.costume(), "待补充"),
                nullToDefault(character.personality(), "待补充"),
                nullToDefault(character.relationship(), "待补充")
        );
    }

    private String buildEnglishImagePromptByTextModel(String roleCard, String typeRequirement) {
        String instruction = """
                OUTPUT LANGUAGE POLICY:
                Your final answer must be ASCII English only.
                Do not output Chinese, Japanese, Korean, Spanish, French, Russian, Arabic, emoji, full-width punctuation, Markdown, code fences, explanations, labels, or notes.
                If the source material is Chinese, rewrite its meaning into natural professional English image-prompt language.
                你是短剧视觉设定师和 AI 图片提示词工程师。
                请把中文角色资料改写成英文图片生成提示词，不是逐字翻译，而是生成图片模型能稳定理解的英文生产提示词。

                必须遵守：
                1. 只输出英文提示词，不要 Markdown，不要解释。
                2. 不要添加角色资料里没有的核心身份。
                3. 不要把配角改成主角，不要把凡人改成仙人，不要把普通人改成圣女。
                4. 不要改变性别、年龄感、职业、时代背景、服装材质、人物气质。
                5. 如果资料描述的是群像或一类人，请选择一个最具代表性的可视化人物形象。
                6. 输出中必须包含角色身份、年龄感/性别、脸部与发型、服装、气质、禁用项。
                7. 结尾加入：clean white background, high quality film character concept art, realistic cinematic texture.

                图片类型要求：
                %s

                中文角色资料：
                %s
                """.formatted(typeRequirement, roleCard);
        String generatedPrompt = textGenerationClient.generate(instruction).trim();
        if (isUsableEnglishImagePrompt(generatedPrompt)) {
            return generatedPrompt;
        }
        throw new BusinessException(500, "角色图片英文提示词生成失败，请检查文本模型配置或稍后重新生成");
    }

    private boolean isUsableEnglishImagePrompt(String value) {
        String text = nullToEmpty(value).trim();
        if (text.length() < 80) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("模型配置未完成") || lower.contains("模型调用失败") || lower.contains("待接入")) {
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

    private String characterImageTypeRequirement(String imageType) {
        if ("AVATAR".equals(imageType)) {
            return "Create a clear headshot portrait. The face must be readable. Show head and shoulders only.";
        }
        if ("THREE_VIEW".equals(imageType)) {
            return "Create a front-side-back three-view character sheet. Full body must be visible in all views.";
        }
        if ("EXPRESSION".equals(imageType)) {
            return "Create a facial expression reference sheet, including calm, worried, angry and determined expressions.";
        }
        if ("COSTUME".equals(imageType)) {
            return "Create a costume reference sheet. Emphasize fabric, colors, accessories, waist items and sleeve details.";
        }
        return "Create a full-body standing character design. The entire outfit must be visible from head to feet. Neutral pose.";
    }

    private String characterGenreRule(String source) {
        String normalized = source.toLowerCase(Locale.ROOT);
        if (isXianxiaOrAncientCharacter(normalized)) {
            return """
                    - This is an ancient Chinese xianxia / costume drama character, not a modern urban fashion character.
                    - Use hanfu, robe, linen or gauze costume details if compatible with the role card.
                    - Forbidden: modern leather jacket, streetwear, cargo pants, sneakers, jeans, western suit, cyberpunk, modern fashion photography.
                    """;
        }
        return "- Do not contradict the role card's era, profession or clothing description.";
    }

    private boolean isXianxiaOrAncientCharacter(String source) {
        return source.contains("仙")
                || source.contains("圣")
                || source.contains("药铺")
                || source.contains("药篓")
                || source.contains("药女")
                || source.contains("古装")
                || source.contains("素衣")
                || source.contains("前世")
                || source.contains("转世")
                || source.contains("三界")
                || source.contains("汉服")
                || source.contains("长裙");
    }
    private String safeFileName(String value) {
        String safe = value == null ? "未命名" : value.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return safe.isBlank() ? "未命名" : safe;
    }

    private void ensureSeriesExists(Long seriesId) {
        seriesRepository.findByIdAndUserId(seriesId, DEFAULT_USER_ID)
                .orElseThrow(() -> new BusinessException(404, "短剧项目不存在"));
    }

    private DramaCharacterRecord ensureCharacterExists(Long seriesId, Long characterId) {
        ensureSeriesExists(seriesId);
        return characterRepository.findBySeriesAndId(seriesId, characterId)
                .orElseThrow(() -> new BusinessException(404, "角色不存在或不属于该短剧项目"));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }


    private String compactText(String value, int maxLength) {
        String text = nullToEmpty(value)
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.isBlank()) {
            return "待补充";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeName(String value) {
        return nullToEmpty(value).trim().toLowerCase();
    }

    private String buildCharacterGeneratePrompt(DramaSeriesRecord series) {
        return """
                你是短剧角色统筹。请根据下面的短剧项目和小说式故事原文，提取并生成项目级角色。
                角色属于整个短剧项目，不属于某一集。请覆盖主角、反派、关键配角、重要关系人物。
                每个角色必须包含：角色名称、人设定位、外貌特点、常用服装、性格特点、人物关系。
                只输出下面格式，不要输出额外解释：

                <CHARACTER>
                <NAME>角色名称</NAME>
                <PROFILE>身份、人设、目标、秘密或剧情功能</PROFILE>
                <APPEARANCE>年龄感、发型、五官、体态、气质、可用于图片生成的视觉特点</APPEARANCE>
                <COSTUME>常用服装、颜色、材质、时代/职业特征</COSTUME>
                <PERSONALITY>性格关键词和行为特点</PERSONALITY>
                <RELATIONSHIP>与主角、反派、配角之间的关系和冲突</RELATIONSHIP>
                </CHARACTER>

                项目名称：%s
                类型：%s
                简介：%s
                风格：%s
                故事摘要：%s
                故事原文：%s
                """.formatted(
                series.name(),
                series.type(),
                nullToDefault(series.intro(), "暂无"),
                nullToDefault(series.style(), "暂无"),
                nullToDefault(series.storySummary(), "暂无"),
                limitText(series.originalStory(), 20000)
        );
    }

    private List<GeneratedCharacter> parseGeneratedCharacters(String generatedText) {
        String text = nullToEmpty(generatedText);
        List<GeneratedCharacter> characters = new ArrayList<>();
        int cursor = 0;
        while (true) {
            int start = text.indexOf("<CHARACTER>", cursor);
            int end = text.indexOf("</CHARACTER>", start);
            if (start < 0 || end <= start) {
                break;
            }
            String block = text.substring(start + "<CHARACTER>".length(), end);
            characters.add(new GeneratedCharacter(
                    extractTag(block, "NAME"),
                    extractTag(block, "PROFILE"),
                    extractTag(block, "APPEARANCE"),
                    extractTag(block, "COSTUME"),
                    extractTag(block, "PERSONALITY"),
                    extractTag(block, "RELATIONSHIP")
            ));
            cursor = end + "</CHARACTER>".length();
        }
        return characters.stream().filter(character -> !isBlank(character.name())).limit(12).toList();
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

    private List<GeneratedCharacter> buildFallbackCharacters(DramaSeriesRecord series) {
        String projectName = nullToDefault(series.name(), "短剧项目");
        return List.of(
                new GeneratedCharacter(
                        projectName + "主角",
                        "故事核心人物，承担观众代入、成长、反击和情绪兑现功能。",
                        "形象需要清晰、有记忆点，适合作为角色图和视频人物的一致性基准。",
                        "服装应贴合项目类型和时代背景，颜色保持稳定，便于后续生成统一视觉。",
                        "有目标、有弱点，面对压力时逐步完成从被动到主动的转变。",
                        "与反派形成主要冲突，与关键配角形成帮助、误会或情感牵引。"
                ),
                new GeneratedCharacter(
                        projectName + "反派",
                        "推动外部压力和核心冲突的人物，负责制造阻碍、误会和反转。",
                        "气质压迫感强，表情、姿态和造型应体现控制欲或危险感。",
                        "服装更利落、强势，适合与主角形成视觉对比。",
                        "精明、强势、目标明确，行为上持续给主角施压。",
                        "与主角存在利益、身份、情感或秘密上的直接冲突。"
                ),
                new GeneratedCharacter(
                        projectName + "关键配角",
                        "连接主线信息和人物关系的辅助角色，可承担线索、误会、帮助或背叛功能。",
                        "视觉上需要和主角、反派区分明显，便于观众快速识别。",
                        "服装可以更生活化或职业化，突出其剧情功能。",
                        "立场可能摇摆，既能提供帮助，也能制造新的问题。",
                        "与主角关系密切，是推动剧情转折的重要人物。"
                )
        );
    }

    private String limitText(String value, int maxLength) {
        String text = nullToEmpty(value).trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...（已截断）";
    }

    private String nullToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record GeneratedCharacter(
            String name,
            String profile,
            String appearance,
            String costume,
            String personality,
            String relationship
    ) {
    }
}



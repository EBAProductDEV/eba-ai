package com.qctv1.ai.drama.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qctv1.ai.drama.config.DramaProperties;
import com.qctv1.ai.drama.domain.DramaAssetRecord;
import com.qctv1.ai.drama.domain.DramaEpisodeRecord;
import com.qctv1.ai.drama.domain.DramaExportPackageRecord;
import com.qctv1.ai.drama.domain.DramaShotRecord;
import com.qctv1.ai.drama.domain.DramaTaskRecord;
import com.qctv1.ai.drama.provider.VisionAnalysisClient;
import com.qctv1.ai.drama.repository.DramaAssetRepository;
import com.qctv1.ai.drama.repository.DramaJianyingDraftRepository;
import com.qctv1.ai.drama.repository.DramaWorkflowRepository;
import com.qctv1.ai.drama.support.BusinessException;
import com.qctv1.ai.drama.vo.DramaJianyingDraftVo;
import com.qctv1.ai.drama.vo.DramaTaskVo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DramaJianyingDraftService {

    private final DramaProperties properties;
    private final DramaWorkflowRepository workflowRepository;
    private final DramaAssetRepository assetRepository;
    private final DramaJianyingDraftRepository draftRepository;
    private final VisionAnalysisClient visionAnalysisClient;
    private final ObjectMapper objectMapper;
    private final TaskExecutor draftTaskExecutor;

    public DramaJianyingDraftService(
            DramaProperties properties,
            DramaWorkflowRepository workflowRepository,
            DramaAssetRepository assetRepository,
            DramaJianyingDraftRepository draftRepository,
            VisionAnalysisClient visionAnalysisClient,
            ObjectMapper objectMapper,
            @Qualifier("dramaDraftTaskExecutor") TaskExecutor draftTaskExecutor
    ) {
        this.properties = properties;
        this.workflowRepository = workflowRepository;
        this.assetRepository = assetRepository;
        this.draftRepository = draftRepository;
        this.visionAnalysisClient = visionAnalysisClient;
        this.objectMapper = objectMapper;
        this.draftTaskExecutor = draftTaskExecutor;
    }

    public DramaTaskVo submit(Long episodeId) {
        DramaEpisodeRecord episode = workflowRepository.findEpisode(episodeId)
                .orElseThrow(() -> new BusinessException(404, "分集不存在"));
        Long taskId = workflowRepository.createTask(
                episode.seriesId(),
                episodeId,
                null,
                null,
                null,
                "JIANYING_DRAFT_GENERATE",
                null,
                "PENDING",
                0,
                "QUEUED",
                "等待生成剪映初稿"
        );
        Long editTaskId = draftRepository.createEditTask(taskId, episode.seriesId(), episodeId, null);
        Path root = draftRoot(episodeId, editTaskId);
        draftRepository.updateEditTaskRootPath(editTaskId, root.toString());
        draftTaskExecutor.execute(() -> runDraftTask(taskId, editTaskId, episode));
        return task(taskId);
    }

    public Optional<DramaJianyingDraftVo> latestPackage(Long episodeId) {
        return draftRepository.findLatestPackage(episodeId).map(this::toDraftVo);
    }

    public Path packagePath(Long packageId) {
        DramaExportPackageRecord record = draftRepository.findPackage(packageId)
                .orElseThrow(() -> new BusinessException(404, "剪映素材包不存在"));
        Path path = Path.of(record.packageZipPath()).normalize();
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(404, "剪映素材包文件不存在");
        }
        return path;
    }

    public Path referenceVideoPath(Long packageId) {
        DramaExportPackageRecord record = draftRepository.findPackage(packageId)
                .orElseThrow(() -> new BusinessException(404, "剪映素材包不存在"));
        Path path = Path.of(record.referenceVideoPath()).normalize();
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(404, "参考成片文件不存在");
        }
        return path;
    }

    private void runDraftTask(Long taskId, Long editTaskId, DramaEpisodeRecord episode) {
        try {
            update(taskId, editTaskId, "RUNNING", 5, "QUEUED", "准备素材目录");
            DraftDirectories dirs = ensureDirectories(editTaskId, episode.id());
            bootstrapMaterialLibrary();

            update(taskId, editTaskId, "RUNNING", 12, "COMPOSITING", "合成干净视频");
            List<ShotClip> clips = resolveShotClips(episode.id());
            double totalDuration = clips.stream().mapToDouble(ShotClip::duration).sum();
            Path cleanVideo = dirs.cleanVideoDir().resolve("干净视频.mp4");
            concatCleanVideo(clips, cleanVideo, dirs.logsDir().resolve("ffmpeg-clean-video.log"));

            update(taskId, editTaskId, "RUNNING", 28, "ANALYZING", "抽帧并分析镜头动作");
            List<ShotAnalysis> analyses = analyzeShots(editTaskId, episode.id(), clips, dirs.framesDir(), dirs.analysisDir());

            update(taskId, editTaskId, "RUNNING", 45, "RECOMMENDING", "从本地素材库推荐 BGM 和音效");
            RecommendedMaterials materials = recommendMaterials(analyses);

            update(taskId, editTaskId, "RUNNING", 58, "DUBBING", "生成配音并对齐台词时间轴");
            List<DialogueCue> cues = buildDialogueTimeline(editTaskId, episode.id(), analyses, dirs.voiceClipsDir());
            Path voiceMix = dirs.audioDir().resolve("配音混音.wav");
            mixVoiceTrack(cues, totalDuration, voiceMix, dirs.logsDir().resolve("ffmpeg-voice-mix.log"));

            Path bgmSfx = dirs.audioDir().resolve("BGM音效.wav");
            buildBgmSfxTrack(materials, totalDuration, bgmSfx, dirs.logsDir().resolve("ffmpeg-bgm-sfx.log"));

            Path subtitleSrt = dirs.subtitlesDir().resolve("台词字幕.srt");
            Path danmakuCsv = dirs.subtitlesDir().resolve("弹幕时间表.csv");
            writeSubtitleFiles(cues, subtitleSrt, danmakuCsv);

            update(taskId, editTaskId, "RUNNING", 76, "COMPOSITING", "生成参考成片");
            Path referenceVideo = dirs.finalDir().resolve("参考成片.mp4");
            muxReferenceVideo(cleanVideo, voiceMix, bgmSfx, subtitleSrt, referenceVideo, dirs.logsDir().resolve("ffmpeg-reference-video.log"));

            update(taskId, editTaskId, "RUNNING", 88, "PACKAGING", "生成剪映素材包");
            Path manifest = dirs.packageDir().resolve("manifest.json");
            Path zip = dirs.packageDir().resolve("剪映素材包.zip");
            writeManifest(episode, cues, analyses, materials, cleanVideo, voiceMix, bgmSfx, subtitleSrt, danmakuCsv, referenceVideo, manifest);
            zipPackage(dirs, referenceVideo, cleanVideo, voiceMix, bgmSfx, subtitleSrt, danmakuCsv, manifest, zip);

            Long packageId = draftRepository.createExportPackage(
                    editTaskId,
                    episode.id(),
                    referenceVideo.toString(),
                    cleanVideo.toString(),
                    voiceMix.toString(),
                    dirs.voiceClipsDir().toString(),
                    bgmSfx.toString(),
                    subtitleSrt.toString(),
                    danmakuCsv.toString(),
                    zip.toString(),
                    manifest.toString()
            );
            Long assetId = assetRepository.create(
                    episode.seriesId(),
                    episode.id(),
                    null,
                    null,
                    null,
                    "JIANYING_DRAFT",
                    "PACKAGE",
                    null,
                    zip.getFileName().toString(),
                    "application/zip",
                    zip.toString(),
                    "剪映初稿素材包 #" + packageId,
                    null,
                    "READY"
            );
            workflowRepository.completeTask(taskId, assetId, "packageId=" + packageId, "剪映初稿已生成");
            draftRepository.updateEditTask(editTaskId, "SUCCEEDED", 100, "DONE", null);
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            workflowRepository.updateTaskProgress(taskId, "FAILED", 100, "FAILED", message);
            draftRepository.updateEditTask(editTaskId, "FAILED", 100, "FAILED", message);
        }
    }

    private DraftDirectories ensureDirectories(Long editTaskId, Long episodeId) throws IOException {
        Path root = draftRoot(episodeId, editTaskId);
        DraftDirectories dirs = new DraftDirectories(
                root,
                root.resolve("source-shots"),
                root.resolve("frames"),
                root.resolve("analysis"),
                root.resolve("clean-video"),
                root.resolve("audio"),
                root.resolve("audio").resolve("配音单句"),
                root.resolve("subtitles"),
                root.resolve("final"),
                root.resolve("jianying-package"),
                root.resolve("logs")
        );
        for (Path path : dirs.all()) {
            Files.createDirectories(path);
        }
        return dirs;
    }

    private Path materialRoot() {
        return Path.of(properties.getMaterialRoot()).normalize();
    }

    private Path episodeRoot(Long episodeId) {
        return materialRoot().resolve("episodes").resolve(String.valueOf(episodeId)).normalize();
    }

    private Path draftRoot(Long episodeId, Long editTaskId) {
        return episodeRoot(episodeId).resolve("draft-" + editTaskId).normalize();
    }

    private void bootstrapMaterialLibrary() throws IOException {
        for (String dir : List.of("bgm", "sfx", "voices", "licenses", "episodes")) {
            Files.createDirectories(materialRoot().resolve(dir));
        }
        Path logsRoot = Path.of(System.getProperty("user.dir")).resolve("logs");
        if (!Files.isDirectory(logsRoot)) {
            logsRoot = Path.of("D:/Code/qctv1/logs");
        }
        if (!Files.isDirectory(logsRoot)) {
            return;
        }
        Path latestFreeAssets = findLatestFreeAssetsDir(logsRoot);
        if (latestFreeAssets == null) {
            return;
        }
        Path onlineAssets = latestFreeAssets.resolve("online-assets");
        if (!Files.isDirectory(onlineAssets)) {
            return;
        }
        Path licenseRecord = materialRoot().resolve("licenses").resolve("mixkit-free-license.json");
        if (!Files.exists(licenseRecord)) {
            Files.writeString(licenseRecord, """
                    {
                      "provider": "Mixkit",
                      "license": "Mixkit Stock Music and Sound Effects Free License",
                      "licenseUrl": "https://mixkit.co/license/"
                    }
                    """, StandardCharsets.UTF_8);
        }
        Long licenseId = draftRepository.upsertLicense(
                "Mixkit",
                "Mixkit Stock Music and Sound Effects Free License",
                "https://mixkit.co/license/",
                "https://mixkit.co/",
                licenseRecord.toString()
        );
        try (Stream<Path> stream = Files.list(onlineAssets)) {
            for (Path source : stream.filter(Files::isRegularFile).toList()) {
                String fileName = source.getFileName().toString();
                String category = fileName.startsWith("bgm-") ? "bgm" : "sfx";
                Path target = materialRoot().resolve(category).resolve(fileName);
                if (!Files.exists(target)) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
                String hash = DigestUtils.md5DigestAsHex(Files.readAllBytes(target));
                draftRepository.upsertMaterialAsset(
                        category,
                        readableName(fileName),
                        "Mixkit",
                        category.equals("bgm") ? "https://mixkit.co/free-stock-music/" : "https://mixkit.co/free-sound-effects/",
                        licenseId,
                        Map.of("use", category.equals("bgm") ? "background music" : "sound effect", "file", fileName),
                        probeDuration(target).orElse(null),
                        hash,
                        target.toString()
                );
            }
        }
    }

    private Path findLatestFreeAssetsDir(Path logsRoot) throws IOException {
        try (Stream<Path> stream = Files.list(logsRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("episode1-free-assets-"))
                    .max(Comparator.comparing(path -> path.getFileName().toString()))
                    .orElse(null);
        }
    }

    private List<ShotClip> resolveShotClips(Long episodeId) {
        List<DramaShotRecord> shots = workflowRepository.listShotsByEpisode(episodeId);
        List<DramaAssetRecord> assets = assetRepository.listByEpisode(episodeId, 1000);
        List<ShotClip> clips = new ArrayList<>();
        double cursor = 0.0;
        for (DramaShotRecord shot : shots) {
            Optional<DramaAssetRecord> video = assets.stream()
                    .filter(asset -> "SHOT_VIDEO".equals(asset.assetType()))
                    .filter(asset -> shot.id().equals(asset.shotId()))
                    .filter(asset -> asset.localPath() != null && Files.isRegularFile(Path.of(asset.localPath())))
                    .findFirst();
            if (video.isEmpty()) {
                continue;
            }
            Path path = Path.of(video.get().localPath()).normalize();
            double duration = probeDuration(path).orElseGet(() -> shot.durationSeconds() == null ? 3.0 : shot.durationSeconds().doubleValue());
            clips.add(new ShotClip(shot, path, cursor, cursor + duration, duration));
            cursor += duration;
        }
        if (clips.isEmpty()) {
            throw new BusinessException(400, "没有找到可用镜头视频，请先完成镜头视频生成");
        }
        return clips;
    }

    private void concatCleanVideo(List<ShotClip> clips, Path output, Path logPath) throws IOException {
        Path concatFile = output.getParent().resolve("concat.txt");
        List<String> lines = clips.stream()
                .map(clip -> "file '" + clip.path().toString().replace("\\", "/").replace("'", "'\\''") + "'")
                .toList();
        Files.write(concatFile, lines, StandardCharsets.UTF_8);
        List<String> copyCommand = List.of(ffmpeg(), "-y", "-f", "concat", "-safe", "0", "-i", concatFile.toString(), "-an", "-c:v", "copy", output.toString());
        try {
            run(copyCommand, logPath);
        } catch (IllegalStateException ex) {
            run(List.of(ffmpeg(), "-y", "-f", "concat", "-safe", "0", "-i", concatFile.toString(), "-an", "-vf", "fps=30,format=yuv420p", "-c:v", "libx264", "-preset", "medium", "-crf", "20", output.toString()), logPath);
        }
    }

    private List<ShotAnalysis> analyzeShots(Long editTaskId, Long episodeId, List<ShotClip> clips, Path framesDir, Path analysisDir) {
        List<ShotAnalysis> analyses = new ArrayList<>();
        for (ShotClip clip : clips) {
            Path shotFrameDir = framesDir.resolve("shot-" + pad(clip.shot().shotNo()));
            try {
                Files.createDirectories(shotFrameDir);
                extractFrames(clip.path(), shotFrameDir, properties.getVision().getMaxFramesPerShot() == null ? 3 : properties.getVision().getMaxFramesPerShot());
                List<Path> frames = listFramePaths(shotFrameDir);
                String modelName = properties.getVision().getAnalysisModel();
                String raw = analyzeWithVisionModel(clip.shot(), frames, modelName);
                Map<String, Object> parsed = parseAnalysis(raw, clip.shot());
                Double confidence = number(parsed.get("confidence")).orElse(0.65);
                Path rawPath = analysisDir.resolve("shot-" + pad(clip.shot().shotNo()) + ".json");
                Files.writeString(rawPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed), StandardCharsets.UTF_8);
                draftRepository.insertShotAnalysis(
                        editTaskId,
                        episodeId,
                        clip.shot().id(),
                        clip.shot().shotNo(),
                        clip.start(),
                        clip.end(),
                        frames.stream().map(Path::toString).toList(),
                        parsed,
                        modelName,
                        confidence,
                        raw
                );
                analyses.add(new ShotAnalysis(clip, parsed, frames, raw));
            } catch (Exception ex) {
                Map<String, Object> fallback = fallbackAnalysis(clip.shot());
                draftRepository.insertShotAnalysis(
                        editTaskId,
                        episodeId,
                        clip.shot().id(),
                        clip.shot().shotNo(),
                        clip.start(),
                        clip.end(),
                        List.of(),
                        fallback,
                        "fallback-shot-metadata",
                        0.45,
                        ex.getMessage()
                );
                analyses.add(new ShotAnalysis(clip, fallback, List.of(), ex.getMessage()));
            }
        }
        return analyses;
    }

    private void extractFrames(Path video, Path outputDir, int maxFrames) throws IOException {
        String pattern = outputDir.resolve("frame-%03d.jpg").toString();
        run(List.of(ffmpeg(), "-y", "-i", video.toString(), "-vf", "fps=1,scale=768:-1", "-frames:v", String.valueOf(Math.max(1, maxFrames)), pattern), outputDir.resolve("ffmpeg-frames.log"));
    }

    private List<Path> listFramePaths(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jpg"))
                    .sorted()
                    .toList();
        }
    }

    private String analyzeWithVisionModel(DramaShotRecord shot, List<Path> frames, String modelName) {
        if (frames.isEmpty()) {
            return objectToJson(fallbackAnalysis(shot));
        }
        String prompt = """
                请分析这些连续关键帧和镜头元数据，返回 JSON，不要 Markdown：
                {
                  "characters": [],
                  "visibleActions": [],
                  "objects": [],
                  "emotion": "",
                  "inferredAction": "",
                  "dialogueAnchors": [],
                  "confidence": 0.0
                }
                要求：
                - visibleActions 只写画面能直接看到的动作
                - inferredAction 可以结合镜头元数据做轻度推断
                - dialogueAnchors 写适合承接的台词动作锚点

                镜头元数据：
                """ + objectToJson(Map.of(
                "shotNo", shot.shotNo(),
                "action", nullToEmpty(shot.action()),
                "dialogue", nullToEmpty(shot.dialogue()),
                "voiceOver", nullToEmpty(shot.voiceOver()),
                "startState", nullToEmpty(shot.startState()),
                "endState", nullToEmpty(shot.endState()),
                "musicCue", nullToEmpty(shot.musicCue()),
                "soundEffect", nullToEmpty(shot.soundEffect())
        ));
        return visionAnalysisClient.analyzeShotFrames(prompt, frames, modelName);
    }

    private Map<String, Object> parseAnalysis(String raw, DramaShotRecord shot) {
        if (raw != null) {
            String normalized = raw.trim();
            if (normalized.startsWith("```")) {
                normalized = normalized.replaceFirst("^```json\\s*", "").replaceFirst("^```\\s*", "").replaceFirst("\\s*```$", "");
            }
            try {
                return objectMapper.readValue(normalized, new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception ignored) {
                // 降级到镜头元数据。
            }
        }
        return fallbackAnalysis(shot);
    }

    private Map<String, Object> fallbackAnalysis(DramaShotRecord shot) {
        String text = String.join(" ", nullToEmpty(shot.action()), nullToEmpty(shot.startState()), nullToEmpty(shot.endState()), nullToEmpty(shot.dialogue()));
        List<String> anchors = new ArrayList<>();
        if (text.contains("魔纹")) anchors.add("发现魔纹");
        if (text.contains("伤") || text.contains("血") || text.contains("气")) anchors.add("检查伤势");
        if (text.contains("醒")) anchors.add("唤醒");
        if (text.contains("救")) anchors.add("救人");
        if (anchors.isEmpty()) anchors.add("承接镜头动作");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("characters", extractCharacters(text));
        result.put("visibleActions", splitKeywords(shot.action()));
        result.put("objects", extractObjects(text));
        result.put("emotion", guessEmotion(text));
        result.put("inferredAction", shot.action() == null || shot.action().isBlank() ? "依据镜头元数据承接剧情动作" : shot.action());
        result.put("dialogueAnchors", anchors);
        result.put("confidence", 0.45);
        return result;
    }

    private RecommendedMaterials recommendMaterials(List<ShotAnalysis> analyses) {
        List<Map<String, Object>> bgm = draftRepository.listReadyMaterials("bgm", 3);
        List<Map<String, Object>> sfx = draftRepository.listReadyMaterials("sfx", 8);
        return new RecommendedMaterials(bgm, sfx);
    }

    private List<DialogueCue> buildDialogueTimeline(Long editTaskId, Long episodeId, List<ShotAnalysis> analyses, Path clipsDir) throws IOException {
        Files.createDirectories(clipsDir);
        List<DialogueCue> cues = new ArrayList<>();
        AtomicInteger cueNo = new AtomicInteger(1);
        for (ShotAnalysis analysis : analyses) {
            String dialogue = firstNonBlank(analysis.clip().shot().dialogue(), analysis.clip().shot().voiceOver());
            if (dialogue.isBlank() || "无".equals(dialogue.trim())) {
                continue;
            }
            String text = cleanDialogueText(dialogue);
            if (text.isBlank()) {
                continue;
            }
            String speaker = inferSpeaker(dialogue, text, analysis.clip().shot(), analysis.analysis());
            int no = cueNo.getAndIncrement();
            String voice = chooseVoice(speaker, text);
            Path clipPath = clipsDir.resolve("voice-" + pad(no) + "-" + safeFileName(speaker.isBlank() ? "旁白" : speaker) + ".mp3");
            double start = analysis.clip().start();
            double end = analysis.clip().end();
            synthesizeVoice(text, voice, clipPath, Math.max(0.8, end - start));
            String anchor = String.join("、", stringList(analysis.analysis().get("dialogueAnchors")));
            draftRepository.insertDialogueTimeline(
                    editTaskId,
                    episodeId,
                    analysis.clip().shot().id(),
                    no,
                    speaker,
                    text,
                    start,
                    end,
                    anchor,
                    voice,
                    clipPath.toString()
            );
            cues.add(new DialogueCue(no, analysis.clip().shot().id(), speaker, text, start, end, anchor, voice, clipPath));
        }
        return cues;
    }

    private void synthesizeVoice(String text, String voice, Path output, double fallbackDuration) throws IOException {
        try {
            run(List.of("python", "-m", "edge_tts", "--voice", voice, "--text", text, "--write-media", output.toString()), output.getParent().resolve(output.getFileName() + ".log"));
        } catch (Exception ex) {
            run(List.of(ffmpeg(), "-y", "-f", "lavfi", "-t", format(fallbackDuration), "-i", "anullsrc=r=44100:cl=stereo", "-q:a", "9", output.toString()), output.getParent().resolve(output.getFileName() + ".fallback.log"));
        }
    }

    private void mixVoiceTrack(List<DialogueCue> cues, double duration, Path output, Path logPath) throws IOException {
        if (cues.isEmpty()) {
            createSilentAudio(output, duration, logPath);
            return;
        }
        List<String> command = new ArrayList<>();
        command.add(ffmpeg());
        command.add("-y");
        command.add("-f");
        command.add("lavfi");
        command.add("-t");
        command.add(format(duration));
        command.add("-i");
        command.add("anullsrc=r=44100:cl=stereo");
        for (DialogueCue cue : cues) {
            command.add("-i");
            command.add(cue.clipPath().toString());
        }
        List<String> filters = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        filters.add("[0:a]volume=0.0[base]");
        labels.add("[base]");
        for (int i = 0; i < cues.size(); i++) {
            DialogueCue cue = cues.get(i);
            int input = i + 1;
            double window = Math.max(0.35, cue.end() - cue.start());
            double voiceDuration = probeDuration(cue.clipPath()).orElse(window);
            double tempo = voiceDuration > window * 0.96 ? Math.min(1.85, Math.max(1.02, voiceDuration / (window * 0.92))) : 1.0;
            int delayMs = (int) Math.round(cue.start() * 1000);
            String label = "[v" + input + "]";
            filters.add("[" + input + ":a]aformat=sample_rates=44100:channel_layouts=stereo,atempo=" + format(tempo) + ",adelay=" + delayMs + "|" + delayMs + label);
            labels.add(label);
        }
        filters.add(String.join("", labels) + "amix=inputs=" + labels.size() + ":duration=longest:normalize=0[aout]");
        command.add("-filter_complex");
        command.add(String.join(";", filters));
        command.add("-map");
        command.add("[aout]");
        command.add("-t");
        command.add(format(duration));
        command.add("-ac");
        command.add("2");
        command.add("-ar");
        command.add("44100");
        command.add(output.toString());
        run(command, logPath);
    }

    private void buildBgmSfxTrack(RecommendedMaterials materials, double duration, Path output, Path logPath) throws IOException {
        Optional<Path> bgm = materials.bgm().stream()
                .map(item -> item.get("local_path"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(Path::of)
                .filter(Files::isRegularFile)
                .findFirst();
        if (bgm.isEmpty()) {
            createSilentAudio(output, duration, logPath);
            return;
        }
        run(List.of(ffmpeg(), "-y", "-stream_loop", "-1", "-i", bgm.get().toString(), "-t", format(duration),
                "-filter:a", "volume=0.32", "-ac", "2", "-ar", "44100", output.toString()), logPath);
    }

    private void muxReferenceVideo(Path cleanVideo, Path voiceMix, Path bgmSfx, Path subtitleSrt, Path output, Path logPath) throws IOException {
        boolean hasSubtitles = Files.isRegularFile(subtitleSrt) && Files.size(subtitleSrt) > 0;
        if (!hasSubtitles) {
            run(List.of(ffmpeg(), "-y", "-i", cleanVideo.toString(), "-i", voiceMix.toString(), "-i", bgmSfx.toString(),
                    "-filter_complex", "[1:a][2:a]amix=inputs=2:duration=longest:normalize=0[a]",
                    "-map", "0:v:0", "-map", "[a]", "-c:v", "copy", "-c:a", "aac", "-shortest", output.toString()), logPath);
            return;
        }
        String subtitleFilter = "subtitles=filename='" + ffmpegFilterPath(subtitleSrt) + "':charenc=UTF-8:"
                + "force_style='FontName=Microsoft YaHei,FontSize=18,PrimaryColour=&H00FFFFFF,"
                + "OutlineColour=&H00111111,BorderStyle=1,Outline=2,Shadow=1,Alignment=2,MarginV=48'";
        run(List.of(ffmpeg(), "-y", "-i", cleanVideo.toString(), "-i", voiceMix.toString(), "-i", bgmSfx.toString(),
                "-filter_complex", "[0:v]" + subtitleFilter + "[v];[1:a][2:a]amix=inputs=2:duration=longest:normalize=0[a]",
                "-map", "[v]", "-map", "[a]", "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
                "-c:a", "aac", "-movflags", "+faststart", "-shortest", output.toString()), logPath);
    }

    private void createSilentAudio(Path output, double duration, Path logPath) throws IOException {
        run(List.of(ffmpeg(), "-y", "-f", "lavfi", "-t", format(duration), "-i", "anullsrc=r=44100:cl=stereo", "-ac", "2", "-ar", "44100", output.toString()), logPath);
    }

    private void writeSubtitleFiles(List<DialogueCue> cues, Path srt, Path csv) throws IOException {
        List<String> srtLines = new ArrayList<>();
        List<String> csvLines = new ArrayList<>();
        csvLines.add("start,end,text,style_hint,position_hint");
        for (DialogueCue cue : cues) {
            srtLines.add(String.valueOf(cue.no()));
            srtLines.add(srtTime(cue.start()) + " --> " + srtTime(cue.end()));
            srtLines.add((cue.speaker().isBlank() ? "" : cue.speaker() + "：") + cue.text());
            srtLines.add("");
            csvLines.add(format(cue.start()) + "," + format(cue.end()) + ",\"" + cue.text().replace("\"", "\"\"") + "\",弹幕,顶部滚动");
        }
        Files.write(srt, srtLines, StandardCharsets.UTF_8);
        Files.write(csv, csvLines, StandardCharsets.UTF_8);
    }

    private void writeManifest(
            DramaEpisodeRecord episode,
            List<DialogueCue> cues,
            List<ShotAnalysis> analyses,
            RecommendedMaterials materials,
            Path cleanVideo,
            Path voiceMix,
            Path bgmSfx,
            Path subtitleSrt,
            Path danmakuCsv,
            Path referenceVideo,
            Path manifest
    ) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("episodeId", episode.id());
        data.put("title", episode.title());
        data.put("createdAt", LocalDateTime.now().toString());
        data.put("referenceVideo", referenceVideo.toString());
        data.put("cleanVideo", cleanVideo.toString());
        data.put("voiceMix", voiceMix.toString());
        data.put("bgmSfx", bgmSfx.toString());
        data.put("subtitleSrt", subtitleSrt.toString());
        data.put("danmakuCsv", danmakuCsv.toString());
        data.put("dialogues", cues);
        data.put("shotAnalyses", analyses.stream().map(ShotAnalysis::analysis).toList());
        data.put("materials", materials);
        Files.writeString(manifest, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data), StandardCharsets.UTF_8);
    }

    private void zipPackage(DraftDirectories dirs, Path referenceVideo, Path cleanVideo, Path voiceMix, Path bgmSfx, Path subtitleSrt, Path danmakuCsv, Path manifest, Path zip) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip), StandardCharsets.UTF_8)) {
            addZip(out, referenceVideo, "参考成片.mp4");
            addZip(out, cleanVideo, "干净视频.mp4");
            addZip(out, voiceMix, "audio/配音混音.wav");
            addZip(out, bgmSfx, "audio/BGM音效.wav");
            addZip(out, subtitleSrt, "subtitles/台词字幕.srt");
            addZip(out, danmakuCsv, "subtitles/弹幕时间表.csv");
            addZip(out, manifest, "manifest.json");
            try (Stream<Path> stream = Files.list(dirs.voiceClipsDir())) {
                for (Path clip : stream.filter(Files::isRegularFile).toList()) {
                    addZip(out, clip, "audio/配音单句/" + clip.getFileName());
                }
            }
        }
    }

    private void addZip(ZipOutputStream out, Path file, String name) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        out.putNextEntry(new ZipEntry(name.replace("\\", "/")));
        Files.copy(file, out);
        out.closeEntry();
    }

    private void update(Long taskId, Long editTaskId, String status, int progress, String stage, String message) {
        workflowRepository.updateTaskProgress(taskId, status, progress, stage, message);
        draftRepository.updateEditTask(editTaskId, status, progress, stage, message);
    }

    private Optional<Double> probeDuration(Path path) {
        try {
            ProcessResult result = runCapture(List.of(ffprobe(), "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", path.toString()));
            String value = result.output().trim();
            if (value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(Double.parseDouble(value));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private void run(List<String> command, Path logPath) throws IOException {
        ProcessResult result = runCapture(command);
        Files.createDirectories(logPath.getParent());
        Files.writeString(logPath, result.output(), StandardCharsets.UTF_8);
        if (result.exitCode() != 0) {
            throw new IllegalStateException(command.get(0) + " failed with exit code " + result.exitCode() + ". Log: " + logPath);
        }
    }

    private ProcessResult runCapture(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = process.waitFor();
            return new ProcessResult(code, output);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("命令被中断：" + command, ex);
        }
    }

    private String ffmpeg() {
        return properties.getFfmpegPath() == null || properties.getFfmpegPath().isBlank() ? "ffmpeg" : properties.getFfmpegPath();
    }

    private String ffprobe() {
        return properties.getFfprobePath() == null || properties.getFfprobePath().isBlank() ? "ffprobe" : properties.getFfprobePath();
    }

    private DramaTaskVo task(Long taskId) {
        DramaTaskRecord record = workflowRepository.findTask(taskId).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "任务不存在"));
        return new DramaTaskVo(
                record.id(), record.seriesId(), record.episodeId(), record.shotId(), record.characterId(), record.assetId(),
                record.targetType(), record.targetId(), record.assetType(), record.assetSubType(), record.taskType(),
                record.providerTaskId(), record.status(), record.progress(), record.stage(), record.errorMessage(),
                record.createdAt(), record.updatedAt()
        );
    }

    private DramaJianyingDraftVo toDraftVo(DramaExportPackageRecord record) {
        return new DramaJianyingDraftVo(
                record.id(),
                record.editTaskId(),
                record.episodeId(),
                record.referenceVideoPath(),
                "/api/ai/drama/jianying/packages/" + record.id() + "/reference",
                record.cleanVideoPath(),
                record.voiceMixPath(),
                record.voiceClipsDir(),
                record.bgmSfxPath(),
                record.subtitleSrtPath(),
                record.danmakuCsvPath(),
                record.packageZipPath(),
                "/api/ai/drama/jianying/packages/" + record.id() + "/download",
                record.manifestPath(),
                record.createdAt()
        );
    }

    private String srtTime(double seconds) {
        int millis = (int) Math.round(seconds * 1000);
        int hours = millis / 3_600_000;
        millis %= 3_600_000;
        int minutes = millis / 60_000;
        millis %= 60_000;
        int secs = millis / 1000;
        int ms = millis % 1000;
        return "%02d:%02d:%02d,%03d".formatted(hours, minutes, secs, ms);
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private String pad(int value) {
        return "%03d".formatted(value);
    }

    private String readableName(String fileName) {
        String name = fileName.replaceFirst("\\.[^.]+$", "").replace('-', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private String safeFileName(String value) {
        String safe = value == null ? "untitled" : value.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_").trim();
        return safe.isBlank() ? "untitled" : safe;
    }

    private String extractSpeaker(String dialogue) {
        String text = nullToEmpty(dialogue).trim();
        int idx = text.indexOf('：');
        if (idx <= 0) {
            idx = text.indexOf(':');
        }
        return idx > 0 ? text.substring(0, idx).trim() : "";
    }

    private String cleanDialogueText(String dialogue) {
        String text = nullToEmpty(dialogue).replace("\r", "\n").replaceAll("\\n+", " ").trim();
        int idx = text.indexOf('：');
        if (idx <= 0) {
            idx = text.indexOf(':');
        }
        return idx > 0 ? text.substring(idx + 1).trim() : text;
    }

    private String inferSpeaker(String dialogue, String text, DramaShotRecord shot, Map<String, Object> analysis) {
        String explicit = extractSpeaker(dialogue);
        if (!explicit.isBlank()) {
            return explicit;
        }
        String textOnly = nullToEmpty(text);
        String context = nullToEmpty(shot.action()) + " "
                + nullToEmpty(shot.dialogue()) + " "
                + nullToEmpty(shot.voiceOver()) + " "
                + objectToJson(analysis);
        if (isNarrationText(text, shot)) {
            return "旁白";
        }
        if (looksLikeMaleLine(textOnly)) {
            return "玄烬";
        }
        if (looksLikeFemaleLine(textOnly)) {
            return "云璃";
        }
        if (looksLikeFemaleLine(context)) {
            return "云璃";
        }
        if (looksLikeMaleLine(context)) {
            return "玄烬";
        }
        return "云璃";
    }

    private String chooseVoice(String speaker, String text) {
        String speakerText = nullToEmpty(speaker);
        String dialogueText = nullToEmpty(text);
        if (speakerText.contains("旁白") || speakerText.contains("叙述")) {
            return "zh-CN-YunyangNeural";
        }
        if (speakerText.contains("云璃") || speakerText.contains("女主") || speakerText.contains("少女")) {
            return "zh-CN-XiaoyiNeural";
        }
        if (speakerText.contains("玄烬") || speakerText.contains("男主") || speakerText.contains("男")) {
            return "zh-CN-YunxiNeural";
        }
        if (looksLikeMaleLine(dialogueText)) {
            return "zh-CN-YunxiNeural";
        }
        return "zh-CN-XiaoyiNeural";
    }

    private boolean isNarrationText(String text, DramaShotRecord shot) {
        String cleaned = nullToEmpty(text);
        String dialogue = cleanDialogueText(shot.dialogue());
        String voiceOver = cleanDialogueText(shot.voiceOver());
        return !voiceOver.isBlank()
                && (dialogue.isBlank() || "无".equals(dialogue))
                && voiceOver.equals(cleaned);
    }

    private boolean looksLikeFemaleLine(String text) {
        String merged = nullToEmpty(text);
        return merged.contains("云璃")
                || merged.contains("女主")
                || merged.contains("她")
                || merged.contains("药铺")
                || merged.contains("煎药")
                || merged.contains("救了你")
                || merged.contains("救人")
                || merged.contains("落到我手里")
                || merged.contains("我碰他")
                || merged.contains("别死")
                || merged.contains("还有气")
                || merged.contains("醒醒")
                || merged.contains("魔纹吞");
    }

    private boolean looksLikeMaleLine(String text) {
        String merged = nullToEmpty(text);
        return merged.contains("玄烬")
                || merged.contains("男主")
                || merged.contains("魔君")
                || merged.contains("本座")
                || merged.contains("吾")
                || merged.contains("我找了你")
                || merged.contains("三百年")
                || merged.contains("血债")
                || merged.contains("抵了吗");
    }

    private String ffmpegFilterPath(Path path) {
        String value = path.toAbsolutePath().normalize().toString().replace('\\', '/');
        value = value.replace(":", "\\:");
        value = value.replace("'", "\\'");
        return value;
    }

    private List<String> extractCharacters(String text) {
        List<String> characters = new ArrayList<>();
        if (text.contains("云璃") || text.contains("女主") || text.contains("她")) characters.add("女主");
        if (text.contains("玄烬") || text.contains("男主") || text.contains("他")) characters.add("男主");
        return characters.isEmpty() ? List.of("未明确人物") : characters;
    }

    private List<String> extractObjects(String text) {
        List<String> objects = new ArrayList<>();
        if (text.contains("血")) objects.add("血迹");
        if (text.contains("魔纹")) objects.add("魔纹");
        if (text.contains("药")) objects.add("药");
        if (text.contains("手")) objects.add("手/手腕");
        return objects;
    }

    private String guessEmotion(String text) {
        if (text.contains("魔纹") || text.contains("血") || text.contains("死")) return "紧张";
        if (text.contains("救") || text.contains("守")) return "守护";
        if (text.contains("债") || text.contains("仇")) return "压迫";
        return "剧情推进";
    }

    private List<String> splitKeywords(String value) {
        String text = nullToEmpty(value).trim();
        if (text.isBlank()) {
            return List.of();
        }
        return Stream.of(text.split("[，。,；;、\\s]+"))
                .filter(item -> !item.isBlank())
                .limit(8)
                .toList();
    }

    private Optional<Double> number(Object value) {
        if (value instanceof Number number) {
            return Optional.of(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Optional.of(Double.parseDouble(text));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        if (value == null) {
            return List.of();
        }
        return List.of(value.toString());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"无".equals(value.trim())) {
                return value;
            }
        }
        return "";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String objectToJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private record DraftDirectories(
            Path root,
            Path sourceShotsDir,
            Path framesDir,
            Path analysisDir,
            Path cleanVideoDir,
            Path audioDir,
            Path voiceClipsDir,
            Path subtitlesDir,
            Path finalDir,
            Path packageDir,
            Path logsDir
    ) {
        List<Path> all() {
            return List.of(root, sourceShotsDir, framesDir, analysisDir, cleanVideoDir, audioDir, voiceClipsDir, subtitlesDir, finalDir, packageDir, logsDir);
        }
    }

    private record ShotClip(DramaShotRecord shot, Path path, double start, double end, double duration) {
    }

    private record ShotAnalysis(ShotClip clip, Map<String, Object> analysis, List<Path> frames, String raw) {
    }

    private record DialogueCue(int no, Long shotId, String speaker, String text, double start, double end, String actionAnchor, String voiceModel, Path clipPath) {
    }

    private record RecommendedMaterials(List<Map<String, Object>> bgm, List<Map<String, Object>> sfx) {
    }

    private record ProcessResult(int exitCode, String output) {
    }
}

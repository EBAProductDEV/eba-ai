package com.qctv1.ai.drama.service;

import com.qctv1.ai.drama.domain.DramaAssetRecord;
import com.qctv1.ai.drama.domain.DramaCharacterRecord;
import com.qctv1.ai.drama.domain.DramaSeriesRecord;
import com.qctv1.ai.drama.domain.DramaTaskRecord;
import com.qctv1.ai.drama.repository.DramaAssetRepository;
import com.qctv1.ai.drama.repository.DramaCharacterRepository;
import com.qctv1.ai.drama.repository.DramaSeriesRepository;
import com.qctv1.ai.drama.repository.DramaWorkflowRepository;
import com.qctv1.ai.drama.support.BusinessException;
import com.qctv1.ai.drama.vo.DramaTaskCenterItemVo;
import com.qctv1.ai.drama.vo.DramaTaskCenterVo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DramaTaskCenterService {

    private static final List<String> IMAGE_STEPS = List.of("提示词翻译组装中", "AI生图中", "图片下载组装中", "保存素材记录");
    private static final List<String> GENERAL_STEPS = List.of("排队中", "执行中", "保存结果", "完成");

    private static final Map<String, String> IMAGE_TYPE_LABELS = Map.of(
            "PORTRAIT", "全身定妆照",
            "AVATAR", "头像",
            "THREE_VIEW", "三视图",
            "EXPRESSION", "表情参考图",
            "COSTUME", "服装版本图"
    );

    private final DramaWorkflowRepository workflowRepository;
    private final DramaSeriesRepository seriesRepository;
    private final DramaCharacterRepository characterRepository;
    private final DramaAssetRepository assetRepository;

    public DramaTaskCenterService(
            DramaWorkflowRepository workflowRepository,
            DramaSeriesRepository seriesRepository,
            DramaCharacterRepository characterRepository,
            DramaAssetRepository assetRepository
    ) {
        this.workflowRepository = workflowRepository;
        this.seriesRepository = seriesRepository;
        this.characterRepository = characterRepository;
        this.assetRepository = assetRepository;
    }

    public DramaTaskCenterVo listTasks(int limit) {
        List<DramaTaskCenterItemVo> tasks = workflowRepository.listRecentTasks(limit).stream()
                .map(this::toCenterItem)
                .toList();
        return new DramaTaskCenterVo(workflowRepository.countActiveTasks(), tasks.size(), tasks);
    }

    public DramaTaskCenterVo cancelTask(Long taskId) {
        DramaTaskRecord task = workflowRepository.findTask(taskId).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "异步任务不存在"));
        if (!"PENDING".equals(task.status()) && !"RUNNING".equals(task.status())) {
            throw new BusinessException(400, "只能终止排队中或执行中的异步任务");
        }
        boolean cancelled = workflowRepository.cancelActiveTask(taskId, "用户已终止该任务");
        if (!cancelled) {
            throw new BusinessException(409, "任务状态已变化，请刷新任务中心后重试");
        }
        return listTasks(80);
    }

    private DramaTaskCenterItemVo toCenterItem(DramaTaskRecord task) {
        String seriesName = resolveSeriesName(task.seriesId());
        String characterName = task.characterId() == null
                ? null
                : characterRepository.findById(task.characterId()).map(DramaCharacterRecord::name).orElse("未知角色");
        String assetSubType = resolveAssetSubType(task);
        boolean imageTask = isImageTask(task, assetSubType);
        List<String> steps = imageTask ? IMAGE_STEPS : GENERAL_STEPS;
        return new DramaTaskCenterItemVo(
                task.id(),
                task.seriesId(),
                task.episodeId(),
                task.shotId(),
                task.characterId(),
                task.assetId(),
                seriesName,
                characterName,
                task.taskType(),
                task.targetType(),
                task.targetId(),
                task.assetType(),
                assetSubType,
                buildTitle(task, assetSubType),
                buildDescription(task, seriesName, characterName, assetSubType),
                task.status(),
                task.progress(),
                task.stage(),
                stageText(task.stage(), task.status(), imageTask),
                currentStep(task.stage(), task.status(), imageTask),
                steps,
                task.errorMessage(),
                task.createdAt(),
                task.updatedAt()
        );
    }

    private String resolveSeriesName(Long seriesId) {
        if (seriesId == null || seriesId <= 0) {
            return "未归属项目";
        }
        return seriesRepository.findById(seriesId).map(DramaSeriesRecord::name).orElse("未知项目");
    }

    private String resolveAssetSubType(DramaTaskRecord task) {
        if (task.assetSubType() != null && !task.assetSubType().isBlank()) {
            return task.assetSubType();
        }
        if (task.assetId() == null) {
            return null;
        }
        return assetRepository.findById(task.assetId()).map(DramaAssetRecord::assetSubType).orElse(null);
    }

    private boolean isImageTask(DramaTaskRecord task, String assetSubType) {
        return "CHARACTER_IMAGE".equals(task.assetType())
                || "SCENE_IMAGE".equals(task.assetType())
                || "SHOT_IMAGE".equals(task.assetType())
                || task.taskType().contains("IMAGE")
                || assetSubType != null;
    }

    private String buildTitle(DramaTaskRecord task, String assetSubType) {
        if (isImageTask(task, assetSubType)) {
            return "图片生成任务 #" + task.id();
        }
        if (task.taskType().contains("STORY")) {
            return "故事生成任务 #" + task.id();
        }
        if (task.taskType().contains("EPISODE")) {
            return "分集生成任务 #" + task.id();
        }
        return "异步任务 #" + task.id();
    }

    private String buildDescription(DramaTaskRecord task, String seriesName, String characterName, String assetSubType) {
        String imageType = imageTypeLabel(assetSubType);
        if (task.characterId() != null) {
            return "项目「" + seriesName + "」 · 角色「" + characterName + "」 · " + imageType;
        }
        if (task.seriesId() != null && task.seriesId() > 0) {
            return "项目「" + seriesName + "」 · " + readableTaskType(task.taskType());
        }
        return readableTaskType(task.taskType());
    }

    private String imageTypeLabel(String assetSubType) {
        if (assetSubType == null || assetSubType.isBlank()) {
            return "图片";
        }
        return IMAGE_TYPE_LABELS.getOrDefault(assetSubType, assetSubType);
    }

    private String readableTaskType(String taskType) {
        if (taskType == null) {
            return "未知任务";
        }
        if (taskType.contains("CHARACTER")) {
            return "角色相关任务";
        }
        if (taskType.contains("IMAGE")) {
            return "图片生成任务";
        }
        if (taskType.contains("VIDEO")) {
            return "视频生成任务";
        }
        if (taskType.contains("STORY")) {
            return "故事生成任务";
        }
        if (taskType.contains("EPISODE")) {
            return "分集生成任务";
        }
        return taskType;
    }

    private String stageText(String stage, String status, boolean imageTask) {
        if ("FAILED".equals(status) || "FAILED".equals(stage)) {
            return "任务失败";
        }
        if ("SUCCEEDED".equals(status) || "DONE".equals(stage)) {
            return "任务完成";
        }
        if (!imageTask) {
            return switch (nullToEmpty(stage)) {
                case "QUEUED" -> "排队中";
                case "GENERATING", "RUNNING" -> "执行中";
                case "SAVING" -> "保存结果";
                default -> "执行中";
            };
        }
        return switch (nullToEmpty(stage)) {
            case "PROMPTING", "QUEUED" -> "提示词翻译组装中";
            case "GENERATING" -> "AI生图中";
            case "DOWNLOADING" -> "图片下载组装中";
            case "SAVING" -> "保存素材记录";
            default -> "执行中";
        };
    }

    private Integer currentStep(String stage, String status, boolean imageTask) {
        if ("FAILED".equals(status) || "FAILED".equals(stage)) {
            return imageTask ? 1 : 0;
        }
        if ("SUCCEEDED".equals(status) || "DONE".equals(stage)) {
            return imageTask ? 3 : 3;
        }
        if (!imageTask) {
            return switch (nullToEmpty(stage)) {
                case "QUEUED" -> 0;
                case "SAVING" -> 2;
                default -> 1;
            };
        }
        return switch (nullToEmpty(stage)) {
            case "PROMPTING", "QUEUED" -> 0;
            case "GENERATING" -> 1;
            case "DOWNLOADING" -> 2;
            case "SAVING" -> 3;
            default -> 1;
        };
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

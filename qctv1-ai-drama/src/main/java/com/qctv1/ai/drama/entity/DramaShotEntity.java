package com.qctv1.ai.drama.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短剧镜头实体。
 *
 * <p>对应 ai_drama_shot。AI 视频生成通常按镜头逐条提交任务，
 * 所以镜头是图片提示词、视频提示词和后续素材任务的核心承载对象。</p>
 */
@Data
@TableName("ai_drama_shot")
public class DramaShotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long episodeId;
    private Long sceneId;
    private Integer shotNo;
    private String shotSize;
    private Integer durationSeconds;
    private String cameraMovement;
    private String composition;
    private String transitionType;
    private String continuityType;
    private String startState;
    private String endState;
    private String continuityNote;
    private String soundEffect;
    private String musicCue;
    private String voiceOver;
    private String action;
    private String dialogue;
    private String imagePrompt;
    private String videoPrompt;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

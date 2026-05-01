package com.qctv1.ai.drama.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短剧异步任务实体。
 *
 * <p>对应 ai_drama_task。文本生成、图片生成、视频生成都可以抽象成任务，
 * 后续接入真实大模型后，providerTaskId 用于保存模型厂商返回的异步任务 ID。</p>
 */
@Data
@TableName("ai_drama_task")
public class DramaTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long seriesId;
    private Long episodeId;
    private Long shotId;
    private Long characterId;
    private Long assetId;
    private String targetType;
    private Long targetId;
    private String assetType;
    private String assetSubType;
    private String taskType;
    private String providerTaskId;
    private String status;
    private Integer progress;
    private String stage;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

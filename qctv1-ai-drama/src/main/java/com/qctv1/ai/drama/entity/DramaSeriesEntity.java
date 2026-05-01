package com.qctv1.ai.drama.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短剧项目主表实体。
 *
 * <p>对应 ai_drama_series，一条记录表示一个短剧项目/系列。
 * 项目级信息包括：基础资料、整个故事剧本、分集规划参数和软删除状态。</p>
 */
@Data
@TableName("ai_drama_series")
public class DramaSeriesEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String name;
    private String aspectRatio;
    private String type;
    private String intro;
    private String theme;
    private String style;
    private String originalStory;
    private String storySummary;
    private String fullStory;
    private String storyStatus;
    private Integer totalEpisodes;
    private Integer episodeDurationMinutes;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;
}

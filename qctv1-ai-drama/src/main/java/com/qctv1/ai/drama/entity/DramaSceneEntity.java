package com.qctv1.ai.drama.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短剧场景实体。
 *
 * <p>对应 ai_drama_scene。当前生产流程中，场景挂在分集下面，镜头再挂在场景下面。</p>
 */
@Data
@TableName("ai_drama_scene")
public class DramaSceneEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long seriesId;
    private Long episodeId;
    private String name;
    private String location;
    private String timeOfDay;
    private String atmosphere;
    private String plotPurpose;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

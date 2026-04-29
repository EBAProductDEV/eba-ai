package com.qctv1.ai.drama.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短剧角色实体。
 *
 * <p>对应 ai_drama_character。角色属于短剧项目，不属于单集。
 * 同一个角色会贯穿多集，因此角色设定、外貌、服装和人物关系都按项目维度管理。</p>
 */
@Data
@TableName("ai_drama_character")
public class DramaCharacterEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long seriesId;
    private String name;
    private String profile;
    private String appearance;
    private String costume;
    private String personality;
    private String relationship;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

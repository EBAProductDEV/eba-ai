package com.qctv1.ai.drama.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短剧素材实体。
 *
 * <p>对应 ai_drama_asset。素材只保存数据库元数据，真实图片、视频、知识资料文件保存在 D:/AI视频 下。</p>
 */
@Data
@TableName("ai_drama_asset")
public class DramaAssetEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long seriesId;
    private Long episodeId;
    private Long shotId;
    private String assetType;
    private String fileName;
    private String contentType;
    private String localPath;
    private String accessUrl;
    private LocalDateTime createdAt;
}

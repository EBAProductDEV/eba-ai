package com.qctv1.ai.drama.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短剧分集实体。
 *
 * <p>对应 ai_drama_episode，一条记录表示某个短剧项目下的一集。
 * 企业级短剧生产流程中，分集大纲由整个故事剧本拆分而来，随后再生成单集脚本、场景和镜头。</p>
 */
@Data
@TableName("ai_drama_episode")
public class DramaEpisodeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long seriesId;
    private Integer episodeNo;
    private String title;
    private String summary;
    private String novelContent;
    private String hook;
    private String cliffhanger;
    private String script;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

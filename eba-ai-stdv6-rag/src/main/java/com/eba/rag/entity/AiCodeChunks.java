package com.eba.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;


/**
 * <p>
 * 代码片段向量知识库
 * </p>
 *
 * @author 粪豆儿
 * @since 2025-07-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("eba_ai_code_chunks")
@ApiModel(value="EbaAiCodeChunks对象", description="代码片段向量知识库")
public class AiCodeChunks implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键")
    private Long id;

    @ApiModelProperty(value = "代码片段内容")
    private String chunkText;

    @ApiModelProperty(value = "向量数据（1536维，float数组，JSON格式）")
    private String embedding;

    @ApiModelProperty(value = "代码所在文件的完整路径")
    private String filePath;

    @ApiModelProperty(value = "文件名")
    private String fileName;

    @ApiModelProperty(value = "方法名或函数名（可选）")
    private String methodName;

    @ApiModelProperty(value = "语言（如 Java / JS / HTML / Vue 等）")
    private String codeLanguage;

    @ApiModelProperty(value = "代码类型（frontend / backend）")
    private String codeType;

    @ApiModelProperty(value = "所属模块/包名（可选）")
    private String repoModule;

    @ApiModelProperty(value = "在文件中的分片顺序")
    private Integer chunkIndex;

    @ApiModelProperty(value = "该 chunk 的 token 数量")
    private Integer tokenSize;

    @ApiModelProperty(value = "创建人")
    private String createdUser;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createdTime;

    @ApiModelProperty(value = "更新人")
    private String updatedUser;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updatedTime;


}

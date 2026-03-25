package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 知识库实体类
 *
 * @author xiaozhi
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"startTime", "endTime", "start", "limit"})
@Schema(description = "知识库信息")
public class SysKnowledgeBase extends Base<SysKnowledgeBase> {

    /**
     * 知识库ID
     */
    @Schema(description = "知识库ID")
    private Long knowledgeBaseId;

    /**
     * 知识库名称
     */
    @Schema(description = "知识库名称")
    private String knowledgeBaseName;

    /**
     * 知识库描述
     */
    @Schema(description = "知识库描述")
    private String description;

    /**
     * 向量模型配置ID
     */
    @Schema(description = "向量模型配置ID")
    private Integer embeddingConfigId;

    /**
     * 向量模型名称（非数据库字段，查询时通过 JOIN 填充）
     */
    @Schema(description = "向量模型名称")
    private String embeddingModelName;

    /**
     * 状态：1-有效，0-删除
     */
    @Schema(description = "状态")
    private String state;
}

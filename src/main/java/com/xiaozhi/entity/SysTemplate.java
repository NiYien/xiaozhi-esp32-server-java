package com.xiaozhi.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 提示词模板实体类
 *
 *
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "提示词模板信息")
public class SysTemplate extends Base<SysTemplate> {

    /**
     * 模板ID
     */
    @Schema(description = "模板ID")
    private Integer templateId;

    /**
     * 模板名称
     */
    @Schema(description = "模板名称")
    private String templateName;

    /**
     * 模板描述
     */
    @Schema(description = "模板描述")
    private String templateDesc;

    /**
     * 模板内容
     */
    @Schema(description = "模板内容")
    private String templateContent;

    /**
     * 模板分类
     */
    @Schema(description = "模板分类")
    private String category;

    /**
     * 是否默认模板(1是 0否)
     */
    @Schema(description = "是否默认模板(1是 0否)")
    private String isDefault;

    /**
     * 状态(1启用 0禁用)
     */
    @Schema(description = "状态(1启用 0禁用)")
    private String state;
}
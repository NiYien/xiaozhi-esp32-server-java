package com.xiaozhi.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 提示词模板响应DTO
 *
 */
@Data
@Schema(description = "提示词模板信息")
public class TemplateDTO {

    @Schema(description = "模板ID", example = "1")
    private Integer templateId;

    @Schema(description = "模板名称", example = "客服助手模板")
    private String templateName;

    @Schema(description = "模板内容", example = "你是一个专业的客服助手...")
    private String templateContent;

    @Schema(description = "模板描述", example = "用于客服场景的提示词模板")
    private String templateDesc;

    @Schema(description = "模板分类", example = "客服")
    private String category;

    @Schema(description = "是否默认模板(1是 0否)", example = "0", allowableValues = {"0", "1"})
    private String isDefault;

    @Schema(description = "状态(1启用 0禁用)", example = "1", allowableValues = {"0", "1"})
    private String state;

    @Schema(description = "用户ID", example = "1")
    private Integer userId;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间", example = "2024-01-01 00:00:00")
    private Date createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间", example = "2024-12-01 10:30:00")
    private Date updateTime;
}

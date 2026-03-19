package com.xiaozhi.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 添加提示词模板请求参数
 *
 */
@Data
@Schema(description = "添加提示词模板请求参数")
public class TemplateAddParam {

    @Schema(description = "模板名称", example = "客服助手模板", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    @Schema(description = "模板内容", example = "你是一个专业的客服助手...", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板内容不能为空")
    private String templateContent;

    @Schema(description = "模板描述", example = "用于客服场景的提示词模板")
    private String templateDesc;
}

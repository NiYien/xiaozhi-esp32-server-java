package com.xiaozhi.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新提示词模板请求参数
 *
 */
@Data
@Schema(description = "更新提示词模板请求参数")
public class TemplateUpdateParam {

    @Schema(description = "模板名称", example = "客服助手模板")
    private String templateName;

    @Schema(description = "模板内容", example = "你是一个专业的客服助手...")
    private String templateContent;

    @Schema(description = "模板描述", example = "用于客服场景的提示词模板")
    private String templateDesc;
}

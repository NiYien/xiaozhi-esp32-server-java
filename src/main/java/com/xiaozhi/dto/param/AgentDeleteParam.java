package com.xiaozhi.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 删除智能体请求参数
 *
 */
@Data
@Schema(description = "删除智能体请求参数")
public class AgentDeleteParam {

    @Schema(description = "智能体ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "智能体ID不能为空")
    private Integer agentId;
}

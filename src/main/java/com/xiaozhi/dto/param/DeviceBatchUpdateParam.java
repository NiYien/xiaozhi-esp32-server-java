package com.xiaozhi.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 批量更新设备请求参数
 *
 */
@Data
@Schema(description = "批量更新设备请求参数")
public class DeviceBatchUpdateParam {

    @Schema(description = "设备ID列表，以逗号分隔", example = "ESP32_001,ESP32_002,ESP32_003", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "设备ID不能为空")
    private String deviceIds;

    @Schema(description = "角色ID", example = "1")
    private Integer roleId;
}

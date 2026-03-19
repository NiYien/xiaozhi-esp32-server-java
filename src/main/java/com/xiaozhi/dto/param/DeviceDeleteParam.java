package com.xiaozhi.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 删除设备请求参数
 *
 */
@Data
@Schema(description = "删除设备请求参数")
public class DeviceDeleteParam {

    @Schema(description = "设备ID", example = "ESP32_001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "设备ID不能为空")
    private String deviceId;
}

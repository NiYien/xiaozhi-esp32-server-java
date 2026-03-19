package com.xiaozhi.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * OTA请求DTO - 封装设备上报的OTA请求字段
 *
 * @author Joey
 */
@Data
@Schema(description = "OTA请求参数")
public class OtaRequestDto {

    @Schema(description = "设备ID（MAC地址）")
    private String deviceId;

    @Schema(description = "芯片型号")
    private String chipModelName;

    @Schema(description = "当前固件版本")
    private String version;

    @Schema(description = "WiFi名称")
    private String wifiName;

    @Schema(description = "设备类型")
    private String type;

    @Schema(description = "设备IP地址")
    private String ip;

    @Schema(description = "设备地理位置")
    private String location;
}

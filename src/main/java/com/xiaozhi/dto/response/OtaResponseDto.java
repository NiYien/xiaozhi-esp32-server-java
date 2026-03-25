package com.xiaozhi.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * OTA响应DTO - 封装OTA响应结构
 *
 */
@Data
@Schema(description = "OTA响应")
public class OtaResponseDto {

    @Schema(description = "固件信息")
    private Map<String, Object> firmware;

    @Schema(description = "激活信息（设备未绑定时）")
    private Map<String, Object> activation;

    @Schema(description = "WebSocket连接信息（设备已绑定时）")
    private Map<String, Object> websocket;

    @Schema(description = "MQTT连接信息（设备已绑定且MQTT启用时）")
    private Map<String, Object> mqtt;

    @Schema(description = "服务器时间信息")
    private Map<String, Object> serverTime;

    @Schema(description = "错误信息")
    private String error;
}

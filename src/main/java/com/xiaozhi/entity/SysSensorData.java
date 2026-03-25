package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 设备传感器数据表
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"startTime", "endTime", "start", "limit"})
@Schema(description = "设备传感器数据")
public class SysSensorData extends Base<SysSensorData> {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "设备ID", example = "ESP32_001")
    private String deviceId;

    @Schema(description = "温度（摄氏度）", example = "42.5")
    private Float temperature;

    @Schema(description = "电量百分比（0-100）", example = "85")
    private Integer battery;

    @Schema(description = "剩余堆内存（字节）", example = "120000")
    private Integer freeHeap;

    @Schema(description = "WiFi信号强度（dBm）", example = "-45")
    private Integer wifiRssi;

    @Schema(description = "运行时长（秒）", example = "86400")
    private Long uptime;
}

package com.xiaozhi.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 设备信息响应DTO
 *
 */
@Data
@Schema(description = "设备信息")
public class DeviceDTO {

    @Schema(description = "设备唯一标识ID", example = "ESP32_001")
    private String deviceId;

    @Schema(description = "当前会话ID", example = "session_123456")
    private String sessionId;

    @Schema(description = "设备名称/别名", example = "客厅小智音箱")
    private String deviceName;

    @Schema(description = "设备状态：0-离线，1-在线已激活对话，2-在线未激活对话", example = "1", allowableValues = {"0", "1", "2"})
    private String state;

    @Schema(description = "设备累计对话次数", example = "50")
    private Integer totalMessage;

    @Schema(description = "WiFi网络名称", example = "MyHome-WiFi")
    private String wifiName;

    @Schema(description = "设备IP地址", example = "192.168.1.101")
    private String ip;

    @Schema(description = "芯片型号名称", example = "ESP32-S3")
    private String chipModelName;

    @Schema(description = "芯片类型", example = "ESP32")
    private String type;

    @Schema(description = "设备固件版本号", example = "v3.0.0")
    private String version;

    @Schema(description = "可用的全局function名称列表，逗号分隔", example = "weather,time,alarm")
    private String functionNames;

    @Schema(description = "设备所在地理位置", example = "北京市朝阳区")
    private String location;

    @Schema(description = "角色ID", example = "1")
    private Integer roleId;

    @Schema(description = "角色名称", example = "默认助手")
    private String roleName;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间", example = "2024-01-01 00:00:00")
    private Date createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间", example = "2024-12-01 10:30:00")
    private Date updateTime;
}

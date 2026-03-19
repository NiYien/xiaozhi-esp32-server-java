package com.xiaozhi.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新设备请求参数
 *
 */
@Data
@Schema(description = "更新设备请求参数")
public class DeviceUpdateParam {

    @Schema(description = "设备名称", example = "客厅小智")
    private String deviceName;

    @Schema(description = "角色ID", example = "1")
    private Integer roleId;

    @Schema(description = "可用function列表", example = "weather,time")
    private String functionNames;

    @Schema(description = "地理位置", example = "北京市朝阳区")
    private String location;
}

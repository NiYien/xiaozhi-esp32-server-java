package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 设备分组成员表（多对多关系）
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"startTime", "endTime", "start", "limit"})
@Schema(description = "设备分组成员信息")
public class SysDeviceGroupMember extends Base<SysDeviceGroupMember> {

    @Schema(description = "主键ID")
    private Integer id;

    @Schema(description = "分组ID", example = "1")
    private Integer groupId;

    @Schema(description = "设备ID", example = "ESP32_001")
    private String deviceId;
}

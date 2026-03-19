package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 设备分组表
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"startTime", "endTime", "start", "limit"})
@Schema(description = "设备分组信息")
public class SysDeviceGroup extends Base<SysDeviceGroup> {

    @Schema(description = "分组ID", example = "1")
    private Integer groupId;

    @Schema(description = "分组名称", example = "客厅设备组")
    private String groupName;

    @Schema(description = "分组描述", example = "客厅里的所有智能设备")
    private String description;

    @Schema(description = "状态：1-启用，0-禁用", example = "1")
    private String state;

    /**
     * 分组包含的设备列表（查询时填充）
     */
    @Schema(description = "分组内设备列表")
    private List<SysDevice> devices;

    /**
     * 分组内设备数量（查询时填充）
     */
    @Schema(description = "分组内设备数量")
    private Integer deviceCount;
}

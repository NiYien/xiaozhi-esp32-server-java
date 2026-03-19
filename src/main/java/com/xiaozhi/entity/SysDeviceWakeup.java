package com.xiaozhi.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * 设备唤醒词-角色映射实体
 */
@Data
@Accessors(chain = true)
@Schema(description = "设备唤醒词-角色映射")
public class SysDeviceWakeup {

    @Schema(description = "主键ID")
    private Integer id;

    @Schema(description = "设备唯一标识")
    private String deviceId;

    @Schema(description = "唤醒词文本")
    private String wakeupWord;

    @Schema(description = "关联角色ID")
    private Integer roleId;

    @Schema(description = "优先级，0为默认角色")
    private Integer priority;

    @Schema(description = "创建时间")
    private Date createTime;
}

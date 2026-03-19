package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 提醒闹钟实体
 */
@Data
@Accessors(chain = true)
@Schema(description = "提醒闹钟")
public class SysReminder implements Serializable {

    @Schema(description = "提醒ID")
    private Integer reminderId;

    @Schema(description = "用户ID")
    private Integer userId;

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "提醒内容")
    private String message;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "下次触发时间")
    private LocalDateTime triggerTime;

    @Schema(description = "重复类型：once-一次性，daily-每天，weekly-每周")
    private String repeatType;

    @Schema(description = "周重复的星期几列表，如 1,3,5 表示周一三五")
    private String repeatDays;

    @Schema(description = "状态：active-活跃，triggered-已触发，cancelled-已取消")
    private String status;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;

/**
 * 每日用量统计预聚合表实体
 */
@Data
public class SysUsageDaily implements Serializable {

    /** 主键ID */
    private Long id;

    /** 统计日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate statDate;

    /** 用户ID */
    private Integer userId;

    /** 设备ID */
    private String deviceId;

    /** 角色ID */
    private Integer roleId;

    /** 服务提供商 */
    private String provider;

    /** 服务类型：llm、stt、tts */
    private String serviceType;

    /** 调用次数 */
    private Integer requestCount;

    /** LLM prompt token 数 */
    private Long promptTokens;

    /** LLM completion token 数 */
    private Long completionTokens;

    /** LLM 总 token 数 */
    private Long totalTokens;

    /** 平均延迟（毫秒） */
    private Integer avgLatencyMs;

    /** 错误次数 */
    private Integer errorCount;

    /** 创建时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}

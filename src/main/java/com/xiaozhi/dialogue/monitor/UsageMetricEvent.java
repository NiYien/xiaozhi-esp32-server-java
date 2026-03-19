package com.xiaozhi.dialogue.monitor;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 用量指标事件数据类
 * 用于在内存缓冲区中暂存采集到的指标数据
 */
@Data
@Builder
public class UsageMetricEvent {

    /** 统计日期 */
    @Builder.Default
    private LocalDate statDate = LocalDate.now();

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
    @Builder.Default
    private int requestCount = 1;

    /** LLM prompt token 数 */
    @Builder.Default
    private long promptTokens = 0;

    /** LLM completion token 数 */
    @Builder.Default
    private long completionTokens = 0;

    /** LLM 总 token 数 */
    @Builder.Default
    private long totalTokens = 0;

    /** 本次调用延迟（毫秒） */
    @Builder.Default
    private long latencyMs = 0;

    /** 是否为错误调用 */
    @Builder.Default
    private boolean error = false;
}

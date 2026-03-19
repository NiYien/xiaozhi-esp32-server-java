package com.xiaozhi.dialogue.monitor;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.entity.SysDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.LocalDate;

/**
 * 用量指标采集辅助类
 * 为非 Spring 管理的对象（如 Persona、FileSynthesizer）提供指标记录能力。
 * 通过 Spring 上下文获取单例后，在调用点手动记录指标。
 */
@Component
public class UsageMetricsHelper {

    private static final Logger logger = LoggerFactory.getLogger(UsageMetricsHelper.class);

    /** 静态单例引用，供非 Spring 管理的对象调用 */
    private static UsageMetricsHelper instance;

    @Resource
    private UsageMetricsCollector metricsCollector;

    @jakarta.annotation.PostConstruct
    public void init() {
        instance = this;
    }

    /**
     * 获取全局实例
     */
    public static UsageMetricsHelper getInstance() {
        return instance;
    }

    /**
     * 记录 LLM 调用指标
     *
     * @param session         会话
     * @param provider        LLM 提供商名称
     * @param usage           Token 使用量（可为 null）
     * @param latencyMs       延迟毫秒
     * @param isError         是否为错误调用
     */
    public void recordLlm(ChatSession session, String provider, Usage usage, long latencyMs, boolean isError) {
        try {
            SysDevice device = session != null ? session.getSysDevice() : null;
            UsageMetricEvent.UsageMetricEventBuilder builder = UsageMetricEvent.builder()
                    .statDate(LocalDate.now())
                    .userId(device != null ? device.getUserId() : null)
                    .deviceId(device != null ? device.getDeviceId() : null)
                    .roleId(device != null ? device.getRoleId() : null)
                    .provider(provider != null ? provider : "unknown")
                    .serviceType("llm")
                    .latencyMs(latencyMs)
                    .error(isError);

            if (usage != null) {
                builder.promptTokens(usage.getPromptTokens() != null ? usage.getPromptTokens() : 0)
                       .completionTokens(usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0)
                       .totalTokens(usage.getTotalTokens() != null ? usage.getTotalTokens() : 0);
            }

            metricsCollector.record(builder.build());
        } catch (Exception e) {
            logger.debug("记录LLM指标失败", e);
        }
    }

    /**
     * 记录 STT 调用指标
     *
     * @param session         会话
     * @param provider        STT 提供商名称
     * @param latencyMs       延迟毫秒
     * @param isError         是否为错误调用
     */
    public void recordStt(ChatSession session, String provider, long latencyMs, boolean isError) {
        try {
            SysDevice device = session != null ? session.getSysDevice() : null;
            UsageMetricEvent event = UsageMetricEvent.builder()
                    .statDate(LocalDate.now())
                    .userId(device != null ? device.getUserId() : null)
                    .deviceId(device != null ? device.getDeviceId() : null)
                    .roleId(device != null ? device.getRoleId() : null)
                    .provider(provider != null ? provider : "unknown")
                    .serviceType("stt")
                    .latencyMs(latencyMs)
                    .error(isError)
                    .build();
            metricsCollector.record(event);
        } catch (Exception e) {
            logger.debug("记录STT指标失败", e);
        }
    }

    /**
     * 记录 TTS 调用指标
     *
     * @param session         会话
     * @param provider        TTS 提供商名称
     * @param latencyMs       延迟毫秒
     * @param isError         是否为错误调用
     */
    public void recordTts(ChatSession session, String provider, long latencyMs, boolean isError) {
        try {
            SysDevice device = session != null ? session.getSysDevice() : null;
            UsageMetricEvent event = UsageMetricEvent.builder()
                    .statDate(LocalDate.now())
                    .userId(device != null ? device.getUserId() : null)
                    .deviceId(device != null ? device.getDeviceId() : null)
                    .roleId(device != null ? device.getRoleId() : null)
                    .provider(provider != null ? provider : "unknown")
                    .serviceType("tts")
                    .latencyMs(latencyMs)
                    .error(isError)
                    .build();
            metricsCollector.record(event);
        } catch (Exception e) {
            logger.debug("记录TTS指标失败", e);
        }
    }
}

package com.xiaozhi.dialogue.monitor;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.service.Persona;
import com.xiaozhi.entity.SysDevice;
import jakarta.annotation.Resource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

/**
 * 用量指标采集 AOP 切面
 * 拦截 LLM/STT/TTS 关键调用点，异步记录指标到内存缓冲区
 *
 * 由于 SttService 和 TtsService 实例不由 Spring 容器管理，
 * 对它们的指标采集通过 UsageMetricsHelper 在调用点手动记录。
 * 本切面主要拦截 Spring 管理的服务层方法。
 */
@Aspect
@Component
public class UsageMetricsAspect {

    private static final Logger logger = LoggerFactory.getLogger(UsageMetricsAspect.class);

    @Resource
    private UsageMetricsCollector metricsCollector;

    /**
     * 拦截 ChatModelFactory.takeChatModel，记录 LLM 调用上下文
     * 实际的 token 使用量通过 ChatResponse 的聚合回调在 Persona 中获取
     */
    @Around("execution(* com.xiaozhi.dialogue.llm.factory.ChatModelFactory.takeChatModel(..))")
    public Object trackChatModelCreation(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            long latencyMs = System.currentTimeMillis() - startTime;
            // 获取 ChatSession 参数
            Object[] args = joinPoint.getArgs();
            if (args.length > 0 && args[0] instanceof ChatSession session) {
                recordLlmError(session, latencyMs);
            }
            throw t;
        }
    }

    /**
     * 记录 LLM 错误指标
     */
    private void recordLlmError(ChatSession session, long latencyMs) {
        try {
            SysDevice device = session.getSysDevice();
            UsageMetricEvent event = UsageMetricEvent.builder()
                    .statDate(LocalDate.now())
                    .userId(device != null ? device.getUserId() : null)
                    .deviceId(device != null ? device.getDeviceId() : null)
                    .roleId(device != null ? device.getRoleId() : null)
                    .provider("unknown")
                    .serviceType("llm")
                    .latencyMs(latencyMs)
                    .error(true)
                    .build();
            metricsCollector.record(event);
        } catch (Exception e) {
            logger.debug("记录LLM错误指标失败", e);
        }
    }
}

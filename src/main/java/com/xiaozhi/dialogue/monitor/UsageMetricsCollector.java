package com.xiaozhi.dialogue.monitor;

import com.xiaozhi.dao.UsageDailyMapper;
import com.xiaozhi.entity.SysUsageDaily;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 用量指标采集器
 * 使用 ConcurrentLinkedQueue 内存缓冲区暂存指标事件，
 * 定时（每5分钟）批量聚合写入 sys_usage_daily 表
 */
@Service
public class UsageMetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(UsageMetricsCollector.class);

    /** 缓冲区最大容量 */
    private static final int MAX_QUEUE_SIZE = 10000;

    /** 内存缓冲区 */
    private final ConcurrentLinkedQueue<UsageMetricEvent> queue = new ConcurrentLinkedQueue<>();

    @Resource
    private UsageDailyMapper usageDailyMapper;

    /**
     * 记录一条用量指标事件到缓冲区
     *
     * @param event 指标事件
     */
    public void record(UsageMetricEvent event) {
        if (event == null) {
            return;
        }
        // 超过上限时丢弃最旧的记录
        if (queue.size() >= MAX_QUEUE_SIZE) {
            queue.poll();
            logger.warn("指标缓冲区已满（{}条），丢弃最旧的记录", MAX_QUEUE_SIZE);
        }
        queue.offer(event);
    }

    /**
     * 批量取出所有待聚合事件
     *
     * @return 事件列表
     */
    public List<UsageMetricEvent> drain() {
        List<UsageMetricEvent> events = new ArrayList<>();
        UsageMetricEvent event;
        while ((event = queue.poll()) != null) {
            events.add(event);
        }
        return events;
    }

    /**
     * 定时聚合写入任务（每5分钟执行一次）
     * 将缓冲区中的事件按维度聚合后写入数据库
     */
    @Scheduled(fixedRate = 300000)
    public void flushMetrics() {
        List<UsageMetricEvent> events = drain();
        if (events.isEmpty()) {
            return;
        }

        logger.info("开始聚合写入用量指标，事件数: {}", events.size());

        // 按 (statDate, userId, deviceId, roleId, provider, serviceType) 分组聚合
        Map<String, AggregatedMetric> aggregated = new HashMap<>();
        for (UsageMetricEvent event : events) {
            String key = buildAggregationKey(event);
            aggregated.computeIfAbsent(key, k -> new AggregatedMetric(event))
                    .merge(event);
        }

        // 写入数据库
        int successCount = 0;
        for (AggregatedMetric metric : aggregated.values()) {
            try {
                SysUsageDaily record = metric.toEntity();
                usageDailyMapper.insertOrUpdate(record);
                successCount++;
            } catch (Exception e) {
                logger.error("写入用量统计失败: {}", e.getMessage(), e);
            }
        }

        logger.info("用量指标聚合写入完成，聚合组数: {}，成功: {}", aggregated.size(), successCount);
    }

    /**
     * 构建聚合键
     */
    private String buildAggregationKey(UsageMetricEvent event) {
        return String.join("|",
                String.valueOf(event.getStatDate()),
                String.valueOf(event.getUserId()),
                String.valueOf(event.getDeviceId()),
                String.valueOf(event.getRoleId()),
                String.valueOf(event.getProvider()),
                event.getServiceType()
        );
    }

    /**
     * 聚合指标内部类
     */
    private static class AggregatedMetric {
        LocalDate statDate;
        Integer userId;
        String deviceId;
        Integer roleId;
        String provider;
        String serviceType;

        int requestCount = 0;
        long promptTokens = 0;
        long completionTokens = 0;
        long totalTokens = 0;
        long totalLatencyMs = 0;
        int errorCount = 0;

        AggregatedMetric(UsageMetricEvent event) {
            this.statDate = event.getStatDate();
            this.userId = event.getUserId();
            this.deviceId = event.getDeviceId();
            this.roleId = event.getRoleId();
            this.provider = event.getProvider();
            this.serviceType = event.getServiceType();
        }

        void merge(UsageMetricEvent event) {
            this.requestCount += event.getRequestCount();
            this.promptTokens += event.getPromptTokens();
            this.completionTokens += event.getCompletionTokens();
            this.totalTokens += event.getTotalTokens();
            this.totalLatencyMs += event.getLatencyMs();
            if (event.isError()) {
                this.errorCount++;
            }
        }

        SysUsageDaily toEntity() {
            SysUsageDaily record = new SysUsageDaily();
            record.setStatDate(statDate);
            record.setUserId(userId);
            record.setDeviceId(deviceId);
            record.setRoleId(roleId);
            record.setProvider(provider);
            record.setServiceType(serviceType);
            record.setRequestCount(requestCount);
            record.setPromptTokens(promptTokens);
            record.setCompletionTokens(completionTokens);
            record.setTotalTokens(totalTokens);
            // 加权平均延迟
            record.setAvgLatencyMs(requestCount > 0 ? (int) (totalLatencyMs / requestCount) : 0);
            record.setErrorCount(errorCount);
            return record;
        }
    }
}

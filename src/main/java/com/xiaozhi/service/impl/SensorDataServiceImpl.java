package com.xiaozhi.service.impl;

import com.xiaozhi.dao.SensorDataMapper;
import com.xiaozhi.entity.SysSensorData;
import com.xiaozhi.service.SensorDataService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 传感器数据服务实现
 * 仅在 MQTT 启用时注册
 */
@Service
@ConditionalOnProperty(name = "xiaozhi.mqtt.enabled", havingValue = "true")
public class SensorDataServiceImpl implements SensorDataService {
    private static final Logger logger = LoggerFactory.getLogger(SensorDataServiceImpl.class);

    /**
     * 默认数据保留天数
     */
    private static final int DEFAULT_RETENTION_DAYS = 7;

    @Resource
    private SensorDataMapper sensorDataMapper;

    @Override
    public int save(SysSensorData sensorData) {
        return sensorDataMapper.insert(sensorData);
    }

    @Override
    public SysSensorData getLatest(String deviceId) {
        return sensorDataMapper.selectLatestByDeviceId(deviceId);
    }

    @Override
    public List<SysSensorData> getHistory(String deviceId, Date startTime, Date endTime) {
        return sensorDataMapper.selectByDeviceId(deviceId, startTime, endTime);
    }

    @Override
    public Map<String, SysSensorData> getLatestByDeviceIds(List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysSensorData> list = sensorDataMapper.selectLatestByDeviceIds(deviceIds);
        return list.stream().collect(Collectors.toMap(SysSensorData::getDeviceId, d -> d, (a, b) -> a));
    }

    @Override
    public int cleanExpiredData(int retentionDays) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -retentionDays);
        Date expireTime = cal.getTime();
        int deleted = sensorDataMapper.deleteExpiredData(expireTime);
        if (deleted > 0) {
            logger.info("清理过期传感器数据 {} 条（保留 {} 天）", deleted, retentionDays);
        }
        return deleted;
    }

    /**
     * 定时清理过期传感器数据（每天凌晨 2 点执行）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledCleanup() {
        try {
            cleanExpiredData(DEFAULT_RETENTION_DAYS);
        } catch (Exception e) {
            logger.error("定时清理传感器数据失败", e);
        }
    }
}

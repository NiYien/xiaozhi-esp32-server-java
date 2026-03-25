package com.xiaozhi.service;

import com.xiaozhi.entity.SysSensorData;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 传感器数据服务
 */
public interface SensorDataService {

    /**
     * 保存传感器数据
     */
    int save(SysSensorData sensorData);

    /**
     * 查询设备最新传感器数据
     */
    SysSensorData getLatest(String deviceId);

    /**
     * 查询设备历史传感器数据
     */
    List<SysSensorData> getHistory(String deviceId, Date startTime, Date endTime);

    /**
     * 批量查询多个设备的最新传感器数据
     *
     * @param deviceIds 设备ID列表
     * @return deviceId → SysSensorData 映射
     */
    Map<String, SysSensorData> getLatestByDeviceIds(List<String> deviceIds);

    /**
     * 清理过期数据
     *
     * @param retentionDays 保留天数
     * @return 删除的记录数
     */
    int cleanExpiredData(int retentionDays);
}

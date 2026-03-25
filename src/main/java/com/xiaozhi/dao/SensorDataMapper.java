package com.xiaozhi.dao;

import com.xiaozhi.entity.SysSensorData;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 传感器数据 数据层
 */
public interface SensorDataMapper {

    /**
     * 插入传感器数据
     */
    int insert(SysSensorData sensorData);

    /**
     * 查询设备最新一条传感器数据
     */
    SysSensorData selectLatestByDeviceId(String deviceId);

    /**
     * 查询设备历史传感器数据
     */
    List<SysSensorData> selectByDeviceId(@Param("deviceId") String deviceId,
                                          @Param("startTime") Date startTime,
                                          @Param("endTime") Date endTime);

    /**
     * 删除指定时间之前的过期数据
     */
    int deleteExpiredData(@Param("expireTime") Date expireTime);

    /**
     * 批量查询多个设备的最新传感器数据
     */
    List<SysSensorData> selectLatestByDeviceIds(@Param("deviceIds") List<String> deviceIds);
}

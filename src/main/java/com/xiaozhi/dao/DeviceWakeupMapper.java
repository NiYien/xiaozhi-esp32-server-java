package com.xiaozhi.dao;

import com.xiaozhi.entity.SysDeviceWakeup;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备唤醒词-角色映射 数据层
 */
public interface DeviceWakeupMapper {

    /**
     * 新增唤醒词映射
     */
    int insert(SysDeviceWakeup wakeup);

    /**
     * 更新唤醒词映射
     */
    int update(SysDeviceWakeup wakeup);

    /**
     * 删除唤醒词映射
     */
    int delete(@Param("id") Integer id);

    /**
     * 按设备ID查询所有映射
     */
    List<SysDeviceWakeup> queryByDevice(@Param("deviceId") String deviceId);

    /**
     * 按设备ID和唤醒词查询映射
     */
    SysDeviceWakeup queryByDeviceAndWord(@Param("deviceId") String deviceId, @Param("wakeupWord") String wakeupWord);
}

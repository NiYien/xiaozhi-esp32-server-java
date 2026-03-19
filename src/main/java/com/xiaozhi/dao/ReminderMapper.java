package com.xiaozhi.dao;

import com.xiaozhi.entity.SysReminder;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 提醒闹钟 数据层
 */
public interface ReminderMapper {

    /**
     * 插入提醒
     */
    int insert(SysReminder reminder);

    /**
     * 更新提醒状态
     */
    int updateStatus(@Param("reminderId") int reminderId, @Param("status") String status);

    /**
     * 更新下次触发时间（重复提醒用）
     */
    int updateTriggerTime(SysReminder reminder);

    /**
     * 按设备查询活跃提醒
     */
    List<SysReminder> queryActiveByDevice(@Param("userId") int userId, @Param("deviceId") String deviceId);

    /**
     * 按用户查询活跃提醒
     */
    List<SysReminder> queryActiveByUser(@Param("userId") int userId);

    /**
     * 查询所有活跃提醒（启动恢复用）
     */
    List<SysReminder> queryPendingReminders();

    /**
     * 统计用户活跃提醒数量
     */
    int countActiveByUser(@Param("userId") int userId);
}

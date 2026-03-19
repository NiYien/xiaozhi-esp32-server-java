package com.xiaozhi.dao;

import com.xiaozhi.entity.SysUsageDaily;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 每日用量统计 数据层
 */
public interface UsageDailyMapper {

    /**
     * 聚合写入（INSERT ON DUPLICATE KEY UPDATE）
     *
     * @param record 聚合后的统计记录
     */
    void insertOrUpdate(SysUsageDaily record);

    /**
     * 按日查询用量，支持动态分组
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param userId    用户ID（普通用户过滤）
     * @param groupBy   分组维度：user、device、role、provider
     * @return 查询结果列表
     */
    List<Map<String, Object>> queryDailyUsage(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("userId") Integer userId,
            @Param("groupBy") String groupBy
    );

    /**
     * 周期汇总查询
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param userId    用户ID（普通用户过滤）
     * @return 汇总结果
     */
    Map<String, Object> querySummary(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("userId") Integer userId
    );

    /**
     * 延迟趋势查询
     *
     * @param startDate   开始日期
     * @param endDate     结束日期
     * @param serviceType 服务类型
     * @param userId      用户ID（普通用户过滤）
     * @return 延迟趋势列表
     */
    List<Map<String, Object>> queryLatencyTrend(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("serviceType") String serviceType,
            @Param("userId") Integer userId
    );

    /**
     * 查询活跃设备统计
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param userId    用户ID（普通用户过滤）
     * @return 活跃设备列表
     */
    List<Map<String, Object>> queryActiveDevices(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("userId") Integer userId
    );
}

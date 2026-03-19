package com.xiaozhi.controller;

import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dao.UsageDailyMapper;
import com.xiaozhi.entity.SysUser;
import com.xiaozhi.utils.CmsUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 监控面板
 * 提供用量统计、活跃设备、延迟趋势等监控数据的查询接口
 */
@RestController
@RequestMapping("/api/monitor")
@Tag(name = "监控面板", description = "用量统计和监控相关操作")
public class MonitorController extends BaseController {

    /** 最大查询天数限制 */
    private static final int MAX_QUERY_DAYS = 90;

    @Resource
    private UsageDailyMapper usageDailyMapper;

    @Resource
    private SessionManager sessionManager;

    /**
     * 按日查询 Token 用量
     *
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param endDate   结束日期（yyyy-MM-dd）
     * @param groupBy   分组维度：user|device|role|provider
     * @return 每日用量统计列表
     */
    @GetMapping("/usage/daily")
    @ResponseBody
    @Operation(summary = "按日查询Token用量", description = "按时间范围和维度查询每日Token消耗统计")
    public ResultMessage queryDailyUsage(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "provider") String groupBy) {
        try {
            // 解析日期，默认查询最近7天
            LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
            LocalDate start = startDate != null ? LocalDate.parse(startDate) : end.minusDays(6);

            // 限制最大查询天数
            if (start.plusDays(MAX_QUERY_DAYS).isBefore(end)) {
                start = end.minusDays(MAX_QUERY_DAYS);
            }

            // 权限过滤：普通用户只能查看自己的数据
            Integer userId = getFilteredUserId();

            List<Map<String, Object>> data = usageDailyMapper.queryDailyUsage(start, end, userId, groupBy);

            ResultMessage result = ResultMessage.success();
            result.put("data", data);
            return result;
        } catch (Exception e) {
            logger.error("查询每日用量失败", e);
            return ResultMessage.error("查询每日用量失败: " + e.getMessage());
        }
    }

    /**
     * 周期汇总查询
     *
     * @param period 周期：week|month
     * @return 汇总统计数据
     */
    @GetMapping("/usage/summary")
    @ResponseBody
    @Operation(summary = "查询周期汇总", description = "查询指定周期的总对话数、总Token数等汇总数据")
    public ResultMessage querySummary(
            @RequestParam(required = false, defaultValue = "week") String period) {
        try {
            LocalDate end = LocalDate.now();
            LocalDate start;
            if ("month".equals(period)) {
                start = end.withDayOfMonth(1);
            } else {
                start = end.minusDays(6);
            }

            Integer userId = getFilteredUserId();

            Map<String, Object> summary = usageDailyMapper.querySummary(start, end, userId);

            // 添加在线设备数
            int onlineDevices = getOnlineDeviceCount();
            if (summary == null) {
                summary = new HashMap<>();
            }
            summary.put("onlineDevices", onlineDevices);
            summary.put("period", period);
            summary.put("startDate", start.format(DateTimeFormatter.ISO_DATE));
            summary.put("endDate", end.format(DateTimeFormatter.ISO_DATE));

            ResultMessage result = ResultMessage.success();
            result.put("data", summary);
            return result;
        } catch (Exception e) {
            logger.error("查询周期汇总失败", e);
            return ResultMessage.error("查询周期汇总失败: " + e.getMessage());
        }
    }

    /**
     * 查询活跃设备列表
     *
     * @return 活跃设备统计列表
     */
    @GetMapping("/active-devices")
    @ResponseBody
    @Operation(summary = "查询活跃设备", description = "查询活跃设备排行和在线设备数")
    public ResultMessage queryActiveDevices() {
        try {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(29);

            Integer userId = getFilteredUserId();

            List<Map<String, Object>> devices = usageDailyMapper.queryActiveDevices(start, end, userId);

            // 添加在线设备数
            int onlineDevices = getOnlineDeviceCount();

            ResultMessage result = ResultMessage.success();
            Map<String, Object> data = new HashMap<>();
            data.put("devices", devices);
            data.put("onlineDevices", onlineDevices);
            result.put("data", data);
            return result;
        } catch (Exception e) {
            logger.error("查询活跃设备失败", e);
            return ResultMessage.error("查询活跃设备失败: " + e.getMessage());
        }
    }

    /**
     * 查询延迟趋势
     *
     * @param serviceType 服务类型：llm|stt|tts（可选，不传则查所有）
     * @param days        查询天数（默认30天）
     * @return 延迟趋势数据
     */
    @GetMapping("/latency")
    @ResponseBody
    @Operation(summary = "查询延迟趋势", description = "查询LLM/STT/TTS的平均响应延迟趋势")
    public ResultMessage queryLatency(
            @RequestParam(required = false) String serviceType,
            @RequestParam(required = false, defaultValue = "30") int days) {
        try {
            if (days > MAX_QUERY_DAYS) {
                days = MAX_QUERY_DAYS;
            }
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(days - 1);

            Integer userId = getFilteredUserId();

            List<Map<String, Object>> data = usageDailyMapper.queryLatencyTrend(start, end, serviceType, userId);

            ResultMessage result = ResultMessage.success();
            result.put("data", data);
            return result;
        } catch (Exception e) {
            logger.error("查询延迟趋势失败", e);
            return ResultMessage.error("查询延迟趋势失败: " + e.getMessage());
        }
    }

    /**
     * 获取过滤后的用户ID
     * 管理员返回 null（查看全局数据），普通用户返回自己的 userId
     */
    private Integer getFilteredUserId() {
        try {
            SysUser user = CmsUtils.getUser();
            if (user != null && "1".equals(user.getIsAdmin())) {
                return null; // 管理员查看全局数据
            }
            return CmsUtils.getUserId();
        } catch (Exception e) {
            return CmsUtils.getUserId();
        }
    }

    /**
     * 获取当前在线设备数
     */
    private int getOnlineDeviceCount() {
        try {
            return sessionManager.getOnlineDeviceCount();
        } catch (Exception e) {
            return 0;
        }
    }
}

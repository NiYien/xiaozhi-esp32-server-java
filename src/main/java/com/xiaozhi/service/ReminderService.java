package com.xiaozhi.service;

import com.xiaozhi.dao.ReminderMapper;
import com.xiaozhi.entity.SysReminder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;

/**
 * 提醒服务
 * 负责提醒的创建、取消、查询以及重复提醒的时间计算
 */
@Service
public class ReminderService {
    private static final Logger logger = LoggerFactory.getLogger(ReminderService.class);

    /**
     * 每用户最大活跃提醒数量
     */
    private static final int MAX_ACTIVE_REMINDERS_PER_USER = 100;

    @Resource
    private ReminderMapper reminderMapper;

    @Resource
    private ReminderScheduler reminderScheduler;

    /**
     * 创建提醒
     *
     * @param userId     用户ID
     * @param deviceId   设备ID
     * @param message    提醒内容
     * @param triggerTime 触发时间
     * @param repeatType 重复类型
     * @param repeatDays 周重复的星期几列表
     * @return 创建结果描述
     */
    public String createReminder(int userId, String deviceId, String message,
                                 LocalDateTime triggerTime, String repeatType, String repeatDays) {
        // 校验触发时间不在过去
        if (triggerTime.isBefore(LocalDateTime.now())) {
            return "提醒时间不能在过去";
        }

        // 校验用户活跃提醒数量
        int activeCount = reminderMapper.countActiveByUser(userId);
        if (activeCount >= MAX_ACTIVE_REMINDERS_PER_USER) {
            return "提醒数量已达上限（" + MAX_ACTIVE_REMINDERS_PER_USER + "个），请先取消不需要的提醒";
        }

        // 默认重复类型
        if (repeatType == null || repeatType.isEmpty()) {
            repeatType = "once";
        }

        SysReminder reminder = new SysReminder();
        reminder.setUserId(userId);
        reminder.setDeviceId(deviceId);
        reminder.setMessage(message);
        reminder.setTriggerTime(triggerTime);
        reminder.setRepeatType(repeatType);
        reminder.setRepeatDays(repeatDays);
        reminder.setStatus("active");

        reminderMapper.insert(reminder);
        logger.info("创建提醒成功 - 用户: {}, 设备: {}, 内容: {}, 触发时间: {}, 类型: {}",
                userId, deviceId, message, triggerTime, repeatType);

        // 注册定时任务
        reminderScheduler.scheduleReminder(reminder);

        return "已设置提醒：" + message + "，时间：" + formatTime(triggerTime)
                + ("once".equals(repeatType) ? "" : "，重复：" + formatRepeatType(repeatType, repeatDays));
    }

    /**
     * 取消提醒
     *
     * @param reminderId 提醒ID
     * @param userId     用户ID（权限校验）
     * @return 取消结果描述
     */
    public String cancelReminder(int reminderId, int userId) {
        List<SysReminder> reminders = reminderMapper.queryActiveByUser(userId);
        SysReminder target = reminders.stream()
                .filter(r -> r.getReminderId() == reminderId)
                .findFirst()
                .orElse(null);

        if (target == null) {
            return "未找到匹配的提醒";
        }

        reminderMapper.updateStatus(reminderId, "cancelled");
        reminderScheduler.cancelReminder(reminderId);
        logger.info("取消提醒成功 - ID: {}, 内容: {}", reminderId, target.getMessage());

        return "已取消提醒：" + target.getMessage();
    }

    /**
     * 查询用户在指定设备上的活跃提醒列表
     */
    public List<SysReminder> listReminders(int userId, String deviceId) {
        return reminderMapper.queryActiveByDevice(userId, deviceId);
    }

    /**
     * 查询用户所有活跃提醒
     */
    public List<SysReminder> listAllReminders(int userId) {
        return reminderMapper.queryActiveByUser(userId);
    }

    /**
     * 按消息内容模糊匹配提醒（cancel_reminder 工具用）
     *
     * @param userId  用户ID
     * @param message 提醒内容关键词
     * @return 匹配的提醒，未找到返回 null
     */
    public SysReminder findReminderByMessage(int userId, String message) {
        List<SysReminder> reminders = reminderMapper.queryActiveByUser(userId);
        // 优先精确匹配
        for (SysReminder r : reminders) {
            if (r.getMessage().equals(message)) {
                return r;
            }
        }
        // 模糊匹配
        for (SysReminder r : reminders) {
            if (r.getMessage().contains(message) || message.contains(r.getMessage())) {
                return r;
            }
        }
        return null;
    }

    /**
     * 计算下次触发时间
     *
     * @param reminder 提醒实体
     * @return 下次触发时间，如果无法计算返回 null
     */
    public LocalDateTime calculateNextTriggerTime(SysReminder reminder) {
        LocalDateTime current = reminder.getTriggerTime();

        return switch (reminder.getRepeatType()) {
            case "daily" -> current.plusDays(1);
            case "weekly" -> calculateNextWeeklyTrigger(current, reminder.getRepeatDays());
            default -> null; // 一次性提醒无下次触发时间
        };
    }

    /**
     * 计算周重复提醒的下次触发时间
     */
    private LocalDateTime calculateNextWeeklyTrigger(LocalDateTime current, String repeatDays) {
        if (repeatDays == null || repeatDays.isEmpty()) {
            return current.plusWeeks(1);
        }

        List<DayOfWeek> days = Arrays.stream(repeatDays.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .map(DayOfWeek::of)
                .sorted()
                .toList();

        if (days.isEmpty()) {
            return current.plusWeeks(1);
        }

        DayOfWeek currentDay = current.getDayOfWeek();

        // 在当前周找下一个匹配的星期几
        for (DayOfWeek day : days) {
            if (day.getValue() > currentDay.getValue()) {
                return current.with(TemporalAdjusters.next(day));
            }
        }

        // 没有更晚的，取下周第一个匹配的星期几
        return current.with(TemporalAdjusters.next(days.get(0)));
    }

    /**
     * 更新提醒触发时间
     */
    public void updateTriggerTime(SysReminder reminder) {
        reminderMapper.updateTriggerTime(reminder);
    }

    /**
     * 更新提醒状态
     */
    public void updateStatus(int reminderId, String status) {
        reminderMapper.updateStatus(reminderId, status);
    }

    /**
     * 查询所有活跃提醒（启动恢复用）
     */
    public List<SysReminder> queryPendingReminders() {
        return reminderMapper.queryPendingReminders();
    }

    private String formatTime(LocalDateTime time) {
        return time.getMonthValue() + "月" + time.getDayOfMonth() + "日 "
                + String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    private String formatRepeatType(String repeatType, String repeatDays) {
        return switch (repeatType) {
            case "daily" -> "每天";
            case "weekly" -> "每周" + formatWeekDays(repeatDays);
            default -> "";
        };
    }

    private String formatWeekDays(String repeatDays) {
        if (repeatDays == null || repeatDays.isEmpty()) {
            return "";
        }
        String[] dayNames = {"", "一", "二", "三", "四", "五", "六", "日"};
        return Arrays.stream(repeatDays.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .map(d -> dayNames[d])
                .reduce((a, b) -> a + "、" + b)
                .orElse("");
    }
}

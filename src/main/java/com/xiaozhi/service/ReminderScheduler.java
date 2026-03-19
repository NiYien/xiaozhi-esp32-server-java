package com.xiaozhi.service;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.mqtt.DeviceWakeupService;
import com.xiaozhi.dao.ReminderMapper;
import com.xiaozhi.entity.SysReminder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 提醒调度器
 * 使用 Spring TaskScheduler 动态注册和管理定时任务
 * 启动时从数据库加载所有活跃提醒并恢复调度
 */
@Component
public class ReminderScheduler {
    private static final Logger logger = LoggerFactory.getLogger(ReminderScheduler.class);

    /**
     * 管理已注册的定时任务
     */
    private final Map<Integer, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    private final TaskScheduler taskScheduler;

    @Resource
    private ReminderMapper reminderMapper;

    @Resource
    private SessionManager sessionManager;

    @Autowired(required = false)
    private DeviceWakeupService deviceWakeupService;

    /**
     * 延迟注入 ReminderService，避免循环依赖
     */
    @Lazy
    @Resource
    private ReminderService reminderService;

    public ReminderScheduler() {
        // 创建虚拟线程任务调度器
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("reminder-scheduler-");
        scheduler.setThreadFactory(Thread.ofVirtual().name("reminder-", 0).factory());
        scheduler.initialize();
        this.taskScheduler = scheduler;
    }

    /**
     * 启动时从数据库加载所有活跃提醒，注册到调度器
     */
    @PostConstruct
    public void init() {
        List<SysReminder> pendingReminders = reminderMapper.queryPendingReminders();
        logger.info("启动恢复：加载到 {} 个活跃提醒", pendingReminders.size());

        LocalDateTime now = LocalDateTime.now();
        for (SysReminder reminder : pendingReminders) {
            if (reminder.getTriggerTime().isAfter(now)) {
                // 未到期的提醒，正常注册
                scheduleReminder(reminder);
            } else if ("once".equals(reminder.getRepeatType())) {
                // 一次性提醒已过期，立即补发
                logger.info("补发过期一次性提醒 - ID: {}, 内容: {}", reminder.getReminderId(), reminder.getMessage());
                triggerReminder(reminder);
            } else {
                // 重复提醒已过期，计算下一次有效时间
                LocalDateTime nextTime = calculateNextValidTime(reminder, now);
                if (nextTime != null) {
                    reminder.setTriggerTime(nextTime);
                    reminderMapper.updateTriggerTime(reminder);
                    scheduleReminder(reminder);
                    logger.info("重复提醒时间已更新 - ID: {}, 下次触发: {}", reminder.getReminderId(), nextTime);
                }
            }
        }
    }

    /**
     * 注册定时任务
     */
    public void scheduleReminder(SysReminder reminder) {
        Instant triggerInstant = reminder.getTriggerTime()
                .atZone(ZoneId.systemDefault())
                .toInstant();

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> triggerReminder(reminder),
                triggerInstant
        );
        scheduledTasks.put(reminder.getReminderId(), future);
        logger.debug("已注册定时任务 - ID: {}, 触发时间: {}", reminder.getReminderId(), reminder.getTriggerTime());
    }

    /**
     * 取消定时任务
     */
    public void cancelReminder(int reminderId) {
        ScheduledFuture<?> future = scheduledTasks.remove(reminderId);
        if (future != null) {
            future.cancel(false);
            logger.debug("已取消定时任务 - ID: {}", reminderId);
        }
    }

    /**
     * 触发提醒
     * 优先通过 WebSocket 在线会话播放，其次通过 MQTT 唤醒设备
     */
    private void triggerReminder(SysReminder reminder) {
        String deviceId = reminder.getDeviceId();
        String message = reminder.getMessage();
        int reminderId = reminder.getReminderId();

        logger.info("触发提醒 - ID: {}, 设备: {}, 内容: {}", reminderId, deviceId, message);

        // 从已注册任务中移除
        scheduledTasks.remove(reminderId);

        String reminderText = "提醒时间到：" + message;

        try {
            // 优先检查 WebSocket 在线
            ChatSession session = sessionManager.getSessionByDeviceId(deviceId);
            if (session != null && session.isOpen() && session.getPersona() != null) {
                // 设备 WS 在线，通过 Persona 的 Synthesizer 播放提醒内容
                session.getPersona().getSynthesizer().synthesize(reminderText);
                logger.info("提醒已通过 WebSocket 播放 - ID: {}", reminderId);
            } else if (deviceWakeupService != null) {
                // 尝试通过 MQTT 唤醒设备
                DeviceWakeupService.WakeupResult result = deviceWakeupService.wakeupDevice(deviceId, reminderText);
                if (result.success()) {
                    logger.info("提醒已通过 MQTT 发送唤醒命令 - ID: {}", reminderId);
                } else {
                    logger.warn("设备离线且 MQTT 唤醒失败 - ID: {}, 原因: {}", reminderId, result.message());
                }
            } else {
                // MQTT 未启用，设备也不在线
                logger.warn("设备离线且 MQTT 未启用，提醒无法送达 - ID: {}, 设备: {}", reminderId, deviceId);
            }
        } catch (Exception e) {
            logger.error("触发提醒时发生异常 - ID: {}", reminderId, e);
        }

        // 处理提醒状态
        handlePostTrigger(reminder);
    }

    /**
     * 触发后的状态处理
     */
    private void handlePostTrigger(SysReminder reminder) {
        if ("once".equals(reminder.getRepeatType())) {
            // 一次性提醒：标记为已触发
            reminderMapper.updateStatus(reminder.getReminderId(), "triggered");
            logger.debug("一次性提醒已标记为 triggered - ID: {}", reminder.getReminderId());
        } else {
            // 重复提醒：计算下次触发时间并重新注册
            LocalDateTime nextTime = reminderService.calculateNextTriggerTime(reminder);
            if (nextTime != null) {
                reminder.setTriggerTime(nextTime);
                reminderMapper.updateTriggerTime(reminder);
                scheduleReminder(reminder);
                logger.info("重复提醒已更新下次触发时间 - ID: {}, 下次: {}", reminder.getReminderId(), nextTime);
            }
        }
    }

    /**
     * 计算重复提醒的下一个有效触发时间（跳过已过期的时间段）
     */
    private LocalDateTime calculateNextValidTime(SysReminder reminder, LocalDateTime now) {
        LocalDateTime nextTime = reminder.getTriggerTime();
        // 持续推进直到超过当前时间
        int maxIterations = 400; // 防止无限循环
        int iteration = 0;
        while (nextTime.isBefore(now) && iteration < maxIterations) {
            SysReminder tempReminder = new SysReminder();
            tempReminder.setTriggerTime(nextTime);
            tempReminder.setRepeatType(reminder.getRepeatType());
            tempReminder.setRepeatDays(reminder.getRepeatDays());
            nextTime = reminderService.calculateNextTriggerTime(tempReminder);
            if (nextTime == null) {
                return null;
            }
            iteration++;
        }
        return nextTime;
    }
}

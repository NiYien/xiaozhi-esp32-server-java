package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysReminder;
import com.xiaozhi.service.ReminderService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 提醒闹钟 Function Call 工具集
 * 注册 set_reminder、list_reminders、cancel_reminder 三个全局工具
 */
@Component
public class ReminderTools implements ToolsGlobalRegistry.GlobalFunction {
    private static final Logger logger = LoggerFactory.getLogger(ReminderTools.class);

    @Resource
    private ReminderService reminderService;

    /**
     * 工具注册入口，返回一个包装了三个子工具的复合回调
     * 由于 GlobalFunction 接口只返回一个 ToolCallback，这里返回 set_reminder
     * 其余两个工具通过额外的 GlobalFunction 实例注册
     */
    @Override
    public ToolCallback getFunctionCallTool(ChatSession chatSession) {
        return buildSetReminderTool();
    }

    /**
     * 构建 set_reminder 工具
     */
    private ToolCallback buildSetReminderTool() {
        return FunctionToolCallback
                .builder("set_reminder", (Map<String, String> params, ToolContext toolContext) -> {
                    ChatSession session = (ChatSession) toolContext.getContext().get(ChatService.TOOL_CONTEXT_SESSION_KEY);
                    SysDevice device = session.getSysDevice();

                    String message = params.get("message");
                    String triggerTimeStr = params.get("triggerTime");
                    String repeatType = params.get("repeatType");
                    String repeatDays = params.get("repeatDays");

                    if (message == null || message.isEmpty()) {
                        return "请告诉我需要提醒你什么内容";
                    }

                    if (triggerTimeStr == null || triggerTimeStr.isEmpty()) {
                        return "请告诉我提醒的时间";
                    }

                    LocalDateTime triggerTime;
                    try {
                        triggerTime = LocalDateTime.parse(triggerTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    } catch (DateTimeParseException e) {
                        logger.warn("时间格式解析失败: {}", triggerTimeStr);
                        return "时间格式不正确，请使用标准格式，如 2026-03-19T08:00:00";
                    }

                    String result = reminderService.createReminder(
                            device.getUserId(), device.getDeviceId(),
                            message, triggerTime,
                            repeatType != null ? repeatType : "once",
                            repeatDays
                    );
                    return result;
                })
                .description("设置提醒闹钟。当用户说\"提醒我\"、\"几点叫我\"、\"设个闹钟\"等时调用。"
                        + "参数 triggerTime 使用 ISO 8601 格式（如 2026-03-19T08:00:00），"
                        + "repeatType 支持 once（一次性）、daily（每天）、weekly（每周），"
                        + "repeatDays 仅 weekly 时使用，如 \"1,3,5\" 表示周一三五。"
                        + "当前时间由系统提示词提供。")
                .inputSchema("""
                        {
                            "type": "object",
                            "properties": {
                                "message": {
                                    "type": "string",
                                    "description": "提醒内容，如：开会、喝水、吃药"
                                },
                                "triggerTime": {
                                    "type": "string",
                                    "description": "触发时间，ISO 8601格式，如 2026-03-19T08:00:00"
                                },
                                "repeatType": {
                                    "type": "string",
                                    "enum": ["once", "daily", "weekly"],
                                    "description": "重复类型：once-一次性，daily-每天，weekly-每周"
                                },
                                "repeatDays": {
                                    "type": "string",
                                    "description": "周重复时的星期几列表，逗号分隔，1=周一...7=周日，如 1,3,5"
                                }
                            },
                            "required": ["message", "triggerTime"]
                        }
                    """)
                .inputType(Map.class)
                .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                .build();
    }

    /**
     * 查看提醒列表 GlobalFunction
     */
    @Component
    public static class ListRemindersFunction implements ToolsGlobalRegistry.GlobalFunction {

        @Resource
        private ReminderService reminderService;

        @Override
        public ToolCallback getFunctionCallTool(ChatSession chatSession) {
            return FunctionToolCallback
                    .builder("list_reminders", (Map<String, String> params, ToolContext toolContext) -> {
                        ChatSession session = (ChatSession) toolContext.getContext().get(ChatService.TOOL_CONTEXT_SESSION_KEY);
                        SysDevice device = session.getSysDevice();

                        List<SysReminder> reminders = reminderService.listReminders(
                                device.getUserId(), device.getDeviceId());

                        if (reminders.isEmpty()) {
                            return "你目前没有设置任何提醒";
                        }

                        StringBuilder sb = new StringBuilder("你的提醒列表：\n");
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M月d日 HH:mm");
                        for (int i = 0; i < reminders.size(); i++) {
                            SysReminder r = reminders.get(i);
                            sb.append(i + 1).append(". ").append(r.getMessage())
                                    .append(" - ").append(r.getTriggerTime().format(formatter));
                            if (!"once".equals(r.getRepeatType())) {
                                sb.append(" (").append(formatRepeat(r)).append(")");
                            }
                            sb.append("\n");
                        }
                        return sb.toString().trim();
                    })
                    .description("查看当前设置的提醒列表。当用户说\"我有哪些提醒\"、\"查看提醒\"、\"看看闹钟\"等时调用。")
                    .inputSchema("""
                            {
                                "type": "object",
                                "properties": {}
                            }
                        """)
                    .inputType(Map.class)
                    .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                    .build();
        }

        private String formatRepeat(SysReminder r) {
            return switch (r.getRepeatType()) {
                case "daily" -> "每天";
                case "weekly" -> "每周" + formatWeekDays(r.getRepeatDays());
                default -> "";
            };
        }

        private String formatWeekDays(String repeatDays) {
            if (repeatDays == null || repeatDays.isEmpty()) return "";
            String[] dayNames = {"", "一", "二", "三", "四", "五", "六", "日"};
            StringBuilder sb = new StringBuilder();
            for (String d : repeatDays.split(",")) {
                int day = Integer.parseInt(d.trim());
                if (day >= 1 && day <= 7) {
                    sb.append(dayNames[day]);
                }
            }
            return sb.toString();
        }
    }

    /**
     * 取消提醒 GlobalFunction
     */
    @Component
    public static class CancelReminderFunction implements ToolsGlobalRegistry.GlobalFunction {

        @Resource
        private ReminderService reminderService;

        @Override
        public ToolCallback getFunctionCallTool(ChatSession chatSession) {
            return FunctionToolCallback
                    .builder("cancel_reminder", (Map<String, String> params, ToolContext toolContext) -> {
                        ChatSession session = (ChatSession) toolContext.getContext().get(ChatService.TOOL_CONTEXT_SESSION_KEY);
                        SysDevice device = session.getSysDevice();

                        String message = params.get("message");
                        if (message == null || message.isEmpty()) {
                            return "请告诉我要取消哪个提醒";
                        }

                        SysReminder reminder = reminderService.findReminderByMessage(device.getUserId(), message);
                        if (reminder == null) {
                            return "未找到匹配的提醒";
                        }

                        return reminderService.cancelReminder(reminder.getReminderId(), device.getUserId());
                    })
                    .description("取消指定的提醒。当用户说\"取消提醒\"、\"删除闹钟\"等时调用。"
                            + "参数 message 为提醒内容的关键词，用于匹配要取消的提醒。")
                    .inputSchema("""
                            {
                                "type": "object",
                                "properties": {
                                    "message": {
                                        "type": "string",
                                        "description": "要取消的提醒内容关键词，如：开会、喝水"
                                    }
                                },
                                "required": ["message"]
                            }
                        """)
                    .inputType(Map.class)
                    .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                    .build();
        }
    }
}

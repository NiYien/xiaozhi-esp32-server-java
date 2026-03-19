package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.entity.SysDeviceGroup;
import com.xiaozhi.service.BroadcastService;
import com.xiaozhi.service.SysDeviceGroupService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 广播文本消息到设备组的Function Call工具
 * 通过TTS合成一次，将语音广播到分组内所有在线设备
 */
@Component
public class BroadcastMessageFunction implements ToolsGlobalRegistry.GlobalFunction {
    private static final Logger logger = LoggerFactory.getLogger(BroadcastMessageFunction.class);

    @Resource
    private BroadcastService broadcastService;

    @Resource
    private SysDeviceGroupService deviceGroupService;

    @Override
    public ToolCallback getFunctionCallTool(ChatSession chatSession) {
        if (chatSession == null || chatSession.getSysDevice() == null || chatSession.getSysDevice().getUserId() == null) {
            return null;
        }

        Integer userId = chatSession.getSysDevice().getUserId();
        List<SysDeviceGroup> groups = deviceGroupService.queryByUserId(userId);
        if (groups == null || groups.isEmpty()) {
            return null;
        }

        String groupList = groups.stream()
                .map(g -> g.getGroupName() + "(ID:" + g.getGroupId() + ",设备数:" + g.getDeviceCount() + ")")
                .collect(Collectors.joining(", "));

        return FunctionToolCallback
                .builder("broadcast_message", (Map<String, Object> params, ToolContext toolContext) -> {
                    ChatSession session = (ChatSession) toolContext.getContext().get(ChatService.TOOL_CONTEXT_SESSION_KEY);
                    try {
                        Object groupIdObj = params.get("groupId");
                        String message = (String) params.get("message");

                        if (groupIdObj == null || message == null || message.isBlank()) {
                            return "参数不完整，需要提供分组ID和消息内容";
                        }

                        int groupId;
                        if (groupIdObj instanceof Number) {
                            groupId = ((Number) groupIdObj).intValue();
                        } else {
                            groupId = Integer.parseInt(groupIdObj.toString());
                        }

                        logger.info("广播文本消息 - 分组ID: {}, 消息: {}", groupId, message);
                        BroadcastService.BroadcastResult result = broadcastService.broadcastMessage(groupId, message, session);
                        return result.message();
                    } catch (Exception e) {
                        logger.error("广播消息失败", e);
                        return "广播消息失败: " + e.getMessage();
                    }
                })
                .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
                .description("向设备组广播文本消息，会通过语音合成后播放到组内所有在线设备。可用的设备组：" + groupList)
                .inputSchema("""
                    {
                        "type": "object",
                        "properties": {
                            "groupId": {
                                "type": "integer",
                                "description": "设备分组ID"
                            },
                            "message": {
                                "type": "string",
                                "description": "要广播的文本消息内容"
                            }
                        },
                        "required": ["groupId", "message"]
                    }
                """)
                .inputType(Map.class)
                .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                .build();
    }
}

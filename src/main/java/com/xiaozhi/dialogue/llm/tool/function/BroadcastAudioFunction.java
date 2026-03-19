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
 * 广播语音到设备组的Function Call工具
 * 接收文本参数，内部通过TTS合成后广播到组内所有在线设备
 */
@Component
public class BroadcastAudioFunction implements ToolsGlobalRegistry.GlobalFunction {
    private static final Logger logger = LoggerFactory.getLogger(BroadcastAudioFunction.class);

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
                .map(g -> g.getGroupName() + "(设备数:" + g.getDeviceCount() + ")")
                .collect(Collectors.joining(", "));

        return FunctionToolCallback
                .builder("broadcast_audio", (Map<String, Object> params, ToolContext toolContext) -> {
                    ChatSession session = (ChatSession) toolContext.getContext().get(ChatService.TOOL_CONTEXT_SESSION_KEY);
                    try {
                        String groupName = (String) params.get("groupName");
                        String text = (String) params.get("text");

                        if (groupName == null || groupName.isBlank() || text == null || text.isBlank()) {
                            return "参数不完整，需要提供分组名称和广播文本内容";
                        }

                        // 通过 groupName + userId 查找分组，同时实现权限隔离
                        Integer currentUserId = session.getSysDevice().getUserId();
                        if (currentUserId == null) {
                            return "无法获取用户信息，请先绑定设备";
                        }

                        SysDeviceGroup group = deviceGroupService.selectByUserIdAndName(currentUserId, groupName);
                        if (group == null) {
                            return "设备组不存在";
                        }

                        logger.info("广播语音 - 分组: {}, 文本: {}", groupName, text);
                        // 使用 broadcastMessage 进行 TTS 合成后广播
                        BroadcastService.BroadcastResult result = broadcastService.broadcastMessage(
                                group.getGroupId(), text, session, currentUserId);
                        return result.message();
                    } catch (Exception e) {
                        logger.error("广播语音失败", e);
                        return "广播语音失败: " + e.getMessage();
                    }
                })
                .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
                .description("向设备组广播语音消息，将文本通过TTS合成后播放到组内所有在线设备。可用的设备组：" + groupList)
                .inputSchema("""
                    {
                        "type": "object",
                        "properties": {
                            "groupName": {
                                "type": "string",
                                "description": "设备分组名称"
                            },
                            "text": {
                                "type": "string",
                                "description": "要广播的文本内容，将通过TTS合成为语音后播放"
                            }
                        },
                        "required": ["groupName", "text"]
                    }
                """)
                .inputType(Map.class)
                .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                .build();
    }
}

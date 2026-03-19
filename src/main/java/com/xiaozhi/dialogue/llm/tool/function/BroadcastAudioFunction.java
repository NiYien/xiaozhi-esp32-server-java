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
 * 广播音频文件到设备组的Function Call工具
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
                .map(g -> g.getGroupName() + "(ID:" + g.getGroupId() + ")")
                .collect(Collectors.joining(", "));

        return FunctionToolCallback
                .builder("broadcast_audio", (Map<String, Object> params, ToolContext toolContext) -> {
                    ChatSession session = (ChatSession) toolContext.getContext().get(ChatService.TOOL_CONTEXT_SESSION_KEY);
                    try {
                        Object groupIdObj = params.get("groupId");
                        String audioPath = (String) params.get("audioPath");

                        if (groupIdObj == null || audioPath == null || audioPath.isBlank()) {
                            return "参数不完整，需要提供分组ID和音频文件路径";
                        }

                        int groupId;
                        if (groupIdObj instanceof Number) {
                            groupId = ((Number) groupIdObj).intValue();
                        } else {
                            groupId = Integer.parseInt(groupIdObj.toString());
                        }

                        logger.info("广播音频 - 分组ID: {}, 音频路径: {}", groupId, audioPath);
                        BroadcastService.BroadcastResult result = broadcastService.broadcastAudio(groupId, audioPath, session);
                        return result.message();
                    } catch (Exception e) {
                        logger.error("广播音频失败", e);
                        return "广播音频失败: " + e.getMessage();
                    }
                })
                .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
                .description("向设备组广播音频文件，将音频播放到组内所有在线设备。可用的设备组：" + groupList)
                .inputSchema("""
                    {
                        "type": "object",
                        "properties": {
                            "groupId": {
                                "type": "integer",
                                "description": "设备分组ID"
                            },
                            "audioPath": {
                                "type": "string",
                                "description": "要广播的音频文件路径"
                            }
                        },
                        "required": ["groupId", "audioPath"]
                    }
                """)
                .inputType(Map.class)
                .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                .build();
    }
}

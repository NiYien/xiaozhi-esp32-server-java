package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.entity.SysDeviceGroup;
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
 * 列出用户设备分组的Function Call工具
 */
@Component
public class ListDeviceGroupsFunction implements ToolsGlobalRegistry.GlobalFunction {
    private static final Logger logger = LoggerFactory.getLogger(ListDeviceGroupsFunction.class);

    @Resource
    private SysDeviceGroupService deviceGroupService;

    @Override
    public ToolCallback getFunctionCallTool(ChatSession chatSession) {
        if (chatSession == null || chatSession.getSysDevice() == null || chatSession.getSysDevice().getUserId() == null) {
            return null;
        }

        return FunctionToolCallback
                .builder("list_device_groups", (Map<String, String> params, ToolContext toolContext) -> {
                    ChatSession session = (ChatSession) toolContext.getContext().get(ChatService.TOOL_CONTEXT_SESSION_KEY);
                    try {
                        Integer userId = session.getSysDevice().getUserId();
                        List<SysDeviceGroup> groups = deviceGroupService.queryByUserId(userId);

                        if (groups == null || groups.isEmpty()) {
                            return "您还没有创建任何设备分组";
                        }

                        String result = groups.stream()
                                .map(g -> String.format("%s（ID: %d，包含 %d 个设备）",
                                        g.getGroupName(), g.getGroupId(),
                                        g.getDeviceCount() != null ? g.getDeviceCount() : 0))
                                .collect(Collectors.joining("\n"));

                        return "您的设备分组列表：\n" + result;
                    } catch (Exception e) {
                        logger.error("查询设备分组失败", e);
                        return "查询设备分组失败: " + e.getMessage();
                    }
                })
                .toolMetadata(ToolMetadata.builder().returnDirect(false).build())
                .description("查询当前用户的设备分组列表，返回分组名称、ID和设备数量")
                .inputSchema("""
                    {
                        "type": "object",
                        "properties": {},
                        "required": []
                    }
                """)
                .inputType(Map.class)
                .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                .build();
    }
}

package com.xiaozhi.dialogue.voiceprint;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.dialogue.llm.tool.XiaozhiToolMetadata;
import com.xiaozhi.entity.SysVoiceprint;
import com.xiaozhi.service.SysVoiceprintService;
import com.xiaozhi.utils.CmsUtils;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 声纹管理 Function Call 工具
 * 提供3个全局工具：注册声纹、查询声纹列表、删除声纹
 *
 * 注意：register_voiceprint 仅返回提示信息，实际录制由设备端完成，
 * 服务端通过 VoiceprintController REST API 接收音频并注册。
 */
public class VoiceprintTools {

    /**
     * 注册声纹工具
     * 告知用户开始录制语音，实际录制由设备端完成，服务端通过API接收音频后注册
     */
    @Component
    public static class RegisterVoiceprintFunction implements ToolsGlobalRegistry.GlobalFunction {

        private final ToolCallback toolCallback = FunctionToolCallback
                .builder("register_voiceprint", (Map<String, String> params, ToolContext toolContext) -> {
                    String name = params.get("name");
                    if (name == null || name.trim().isEmpty()) {
                        name = "默认声纹";
                    }
                    return "好的，我来帮你注册名为「" + name + "」的声纹。请对着设备说几句话，" +
                            "录制至少3秒的语音，录制完成后系统会自动完成声纹注册。";
                })
                .toolMetadata(new XiaozhiToolMetadata(true, false))
                .description("当用户想要注册/添加/录制自己的声纹时调用此函数。声纹用于识别说话人身份。触发词汇：'注册声纹'、'录制声纹'、'添加声纹'、'记住我的声音'。")
                .inputSchema("""
                        {
                            "type": "object",
                            "properties": {
                                "name": {
                                    "type": "string",
                                    "description": "声纹名称，用于标识说话人，例如用户的名字"
                                }
                            },
                            "required": ["name"]
                        }
                        """)
                .inputType(Map.class)
                .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                .build();

        @Override
        public ToolCallback getFunctionCallTool(ChatSession chatSession) {
            return toolCallback;
        }
    }

    /**
     * 查询声纹列表工具
     */
    @Component
    public static class ListVoiceprintsFunction implements ToolsGlobalRegistry.GlobalFunction {

        @Resource
        private SysVoiceprintService voiceprintService;

        @Override
        public ToolCallback getFunctionCallTool(ChatSession chatSession) {
            return FunctionToolCallback
                    .builder("list_voiceprints", (Map<String, String> params, ToolContext toolContext) -> {
                        ChatSession session = (ChatSession) toolContext.getContext().get(ChatService.TOOL_CONTEXT_SESSION_KEY);
                        // 从设备关联的用户获取声纹列表
                        String deviceId = session.getSysDevice() != null ? session.getSysDevice().getDeviceId() : null;
                        if (deviceId == null) {
                            return "无法获取设备信息，请重新连接后再试。";
                        }

                        List<SysVoiceprint> voiceprints = voiceprintService.listByDeviceId(deviceId);
                        if (voiceprints == null || voiceprints.isEmpty()) {
                            return "当前没有已注册的声纹。你可以说「注册声纹」来添加。";
                        }

                        StringBuilder sb = new StringBuilder("已注册的声纹列表：\n");
                        for (int i = 0; i < voiceprints.size(); i++) {
                            SysVoiceprint vp = voiceprints.get(i);
                            sb.append(String.format("%d. %s（采样%d次）\n",
                                    i + 1, vp.getVoiceprintName(), vp.getSampleCount() != null ? vp.getSampleCount() : 1));
                        }
                        return sb.toString();
                    })
                    .toolMetadata(new XiaozhiToolMetadata(true, false))
                    .description("当用户想要查看/列出已注册的声纹列表时调用此函数。触发词汇：'查看声纹'、'声纹列表'、'有哪些声纹'、'我注册过哪些声纹'。")
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
    }

    /**
     * 删除声纹工具
     */
    @Component
    public static class DeleteVoiceprintFunction implements ToolsGlobalRegistry.GlobalFunction {

        @Resource
        private SysVoiceprintService voiceprintService;

        @Resource
        private VoiceprintRecognitionService voiceprintRecognitionService;

        @Override
        public ToolCallback getFunctionCallTool(ChatSession chatSession) {
            return FunctionToolCallback
                    .builder("delete_voiceprint", (Map<String, String> params, ToolContext toolContext) -> {
                        ChatSession session = (ChatSession) toolContext.getContext().get(ChatService.TOOL_CONTEXT_SESSION_KEY);
                        String name = params.get("name");
                        if (name == null || name.trim().isEmpty()) {
                            return "请告诉我要删除哪个声纹的名称。";
                        }

                        String deviceId = session.getSysDevice() != null ? session.getSysDevice().getDeviceId() : null;
                        if (deviceId == null) {
                            return "无法获取设备信息，请重新连接后再试。";
                        }

                        // 获取设备关联用户的声纹，按名称模糊匹配
                        List<SysVoiceprint> allVoiceprints = voiceprintService.listByDeviceId(deviceId);
                        List<SysVoiceprint> matched = allVoiceprints.stream()
                                .filter(vp -> vp.getVoiceprintName() != null && vp.getVoiceprintName().contains(name))
                                .collect(Collectors.toList());

                        if (matched.isEmpty()) {
                            return "未找到名称包含「" + name + "」的声纹。";
                        }

                        // 删除匹配到的声纹
                        int deleted = 0;
                        for (SysVoiceprint vp : matched) {
                            int rows = voiceprintService.delete(vp.getVoiceprintId(), vp.getUserId());
                            if (rows > 0) {
                                deleted++;
                            }
                        }

                        // 删除后刷新设备缓存
                        if (deleted > 0) {
                            voiceprintRecognitionService.refreshDeviceVoiceprints(deviceId);
                        }

                        return deleted > 0
                                ? "已成功删除" + deleted + "个名称包含「" + name + "」的声纹。"
                                : "删除失败，请稍后再试。";
                    })
                    .toolMetadata(new XiaozhiToolMetadata(true, false))
                    .description("当用户想要删除已注册的声纹时调用此函数。触发词汇：'删除声纹'、'移除声纹'、'去掉声纹'。")
                    .inputSchema("""
                            {
                                "type": "object",
                                "properties": {
                                    "name": {
                                        "type": "string",
                                        "description": "要删除的声纹名称，支持模糊匹配"
                                    }
                                },
                                "required": ["name"]
                            }
                            """)
                    .inputType(Map.class)
                    .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                    .build();
        }
    }
}

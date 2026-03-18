package com.xiaozhi.communication.common;

import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.aec.AecService;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.dialogue.llm.tool.ToolsSessionHolder;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.service.SysRoleService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 已绑定设备的初始化器。
 * 将设备初始化逻辑从 MessageHandler 中提取出来，供多处复用。
 */
@Component
public class BoundDeviceInitializer {
    private static final Logger logger = LoggerFactory.getLogger(BoundDeviceInitializer.class);

    @Resource
    private SysDeviceService deviceService;

    @Resource
    private SysRoleService roleService;

    @Resource
    private ChatService chatService;

    @Resource
    private ToolsGlobalRegistry toolsGlobalRegistry;

    @Resource
    private SessionManager sessionManager;

    @Autowired(required = false)
    private AecService aecService;

    /**
     * 完整初始化已绑定的设备（包含设备状态更新）。
     * 用于普通设备连接场景。
     *
     * @param chatSession 聊天会话
     * @param device      设备信息
     */
    public void initializeDevice(ChatSession chatSession, SysDevice device) {
        String deviceId = device.getDeviceId();
        String sessionId = chatSession.getSessionId();

        // 初始化会话相关的工具、角色、AEC
        initializeSessionCore(chatSession, device, sessionId);

        try {
            // 更新设备状态
            deviceService.update(new SysDevice()
                    .setDeviceId(deviceId)
                    .setState(chatSession instanceof WebSocketSession ? SysDevice.DEVICE_STATE_ONLINE : SysDevice.DEVICE_STATE_STANDBY));
        } catch (Exception e) {
            logger.error("设备初始化失败 - DeviceId: " + deviceId, e);
            try {
                sessionManager.closeSession(sessionId);
            } catch (Exception ex) {
                logger.error("关闭WebSocket连接失败", ex);
            }
        }
    }

    /**
     * 初始化已绑定的设备（跳过设备状态更新）。
     * 用于虚拟设备自动绑定场景，因为创建时已设置了正确的状态。
     *
     * @param chatSession 聊天会话
     * @param device      设备信息
     */
    public void initializeDeviceSkipStateUpdate(ChatSession chatSession, SysDevice device) {
        String sessionId = chatSession.getSessionId();

        // 仅初始化会话相关的工具、角色、AEC，不更新设备状态
        initializeSessionCore(chatSession, device, sessionId);
    }

    /**
     * 会话核心初始化：设置工具持有者、构建角色人设、初始化AEC。
     */
    private void initializeSessionCore(ChatSession chatSession, SysDevice device, String sessionId) {
        // 这里需要放在虚拟线程外
        ToolsSessionHolder toolsSessionHolder = new ToolsSessionHolder(chatSession.getSessionId(),
                device, toolsGlobalRegistry);
        chatSession.setFunctionSessionHolder(toolsSessionHolder);

        // 从数据库获取角色描述。device.getRoleId()表示当前设备的当前活跃角色，或者上次退出时的活跃角色。
        SysRole role = roleService.selectRoleById(device.getRoleId());

        chatService.buildPersona(chatSession, device, role);

        // 连接建立时就初始化 AEC，确保后续任何 TTS 播放（含唤醒响应）的参考帧都不会被丢弃
        if (aecService != null) aecService.initSession(sessionId);
    }
}

package com.xiaozhi.communication.common;

import com.xiaozhi.dialogue.llm.tool.ToolsSessionHolder;
import com.xiaozhi.dialogue.llm.memory.Conversation;
import com.xiaozhi.dialogue.service.Persona;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.enums.ListenMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * 设备状态管理器
 * 负责设备配置注册、ListenMode切换、流式识别状态、播放状态查询等
 */
public class DeviceStateManager {
    private static final Logger logger = LoggerFactory.getLogger(DeviceStateManager.class);

    private final SessionRegistry sessionRegistry;

    public DeviceStateManager(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * 注册设备配置（不含事件发布，事件由 SessionManager 负责）
     *
     * @param sessionId 会话ID
     * @param device    设备信息
     * @return 会话对象，如果会话存在则返回该会话，否则返回null
     */
    public ChatSession registerDevice(String sessionId, SysDevice device) {
        ChatSession chatSession = sessionRegistry.getSession(sessionId);
        if (chatSession != null) {
            chatSession.setSysDevice(device);
            logger.debug("设备配置已注册 - SessionId: {}, DeviceId: {}", sessionId, device.getDeviceId());
            return chatSession;
        }
        return null;
    }

    /**
     * 获取设备配置
     *
     * @param sessionId 会话ID
     * @return 设备配置
     */
    public SysDevice getDeviceConfig(String sessionId) {
        ChatSession chatSession = sessionRegistry.getSession(sessionId);
        if (chatSession != null) {
            return chatSession.getSysDevice();
        }
        return null;
    }

    /**
     * 获取会话的function holder
     *
     * @param sessionId 会话ID
     * @return FunctionSessionHolder
     */
    public ToolsSessionHolder getFunctionSessionHolder(String sessionId) {
        ChatSession chatSession = sessionRegistry.getSession(sessionId);
        if (chatSession != null) {
            return chatSession.getFunctionSessionHolder();
        }
        return null;
    }

    /**
     * 获取用户的可用角色列表
     *
     * @param sessionId 会话ID
     * @return 角色列表
     */
    public List<SysRole> getAvailableRoles(String sessionId) {
        ChatSession chatSession = sessionRegistry.getSession(sessionId);
        if (chatSession != null) {
            return chatSession.getSysRoleList();
        }
        return null;
    }

    /**
     * 是否在播放音乐
     *
     * @param sessionId 会话ID
     * @return 是否正在播放音乐
     */
    public boolean isPlaying(String sessionId) {
        ChatSession chatSession = sessionRegistry.getSession(sessionId);
        if (chatSession != null) {
            return chatSession.isPlaying();
        }
        return false;
    }

    /**
     * 设置设备状态
     *
     * @param sessionId 会话ID
     * @param mode      设备状态 auto/realTime
     */
    public void setMode(String sessionId, ListenMode mode) {
        ChatSession chatSession = sessionRegistry.getSession(sessionId);
        if (chatSession != null) {
            chatSession.setMode(mode);
        }
    }

    /**
     * 获取设备状态
     *
     * @param sessionId 会话ID
     * @return 设备监听模式
     */
    public ListenMode getMode(String sessionId) {
        ChatSession chatSession = sessionRegistry.getSession(sessionId);
        if (chatSession != null) {
            return chatSession.getMode();
        }
        return ListenMode.Auto;
    }

    /**
     * 设置流式识别状态
     *
     * @param sessionId   会话ID
     * @param isStreaming 是否正在流式识别
     */
    public void setStreamingState(String sessionId, boolean isStreaming) {
        ChatSession chatSession = sessionRegistry.getSession(sessionId);
        if (chatSession != null) {
            chatSession.setStreamingState(isStreaming);
        }
    }

    /**
     * 获取流式识别状态
     *
     * @param sessionId 会话ID
     * @return 是否正在流式识别
     */
    public boolean isStreaming(String sessionId) {
        ChatSession chatSession = sessionRegistry.getSession(sessionId);
        if (chatSession != null) {
            return chatSession.isStreamingState();
        }
        return false;
    }

    /**
     * 根据设备ID查找对话
     *
     * @param deviceId 设备ID
     * @return 对话对象
     */
    public Optional<Conversation> findConversation(String deviceId) {
        return sessionRegistry.getAllSessions().stream()
                .filter(session -> session.getSysDevice().getDeviceId().equals(deviceId))
                .findFirst()
                .map(ChatSession::getPersona)
                .map(Persona::getConversation);
    }
}

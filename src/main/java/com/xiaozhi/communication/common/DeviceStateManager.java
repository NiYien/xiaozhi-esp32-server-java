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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备状态管理器
 * 负责设备配置注册、ListenMode切换、流式识别状态、播放状态查询等
 * 支持双通道（WebSocket + MQTT）在线状态聚合
 */
public class DeviceStateManager {
    private static final Logger logger = LoggerFactory.getLogger(DeviceStateManager.class);

    private final SessionRegistry sessionRegistry;

    /**
     * MQTT 在线设备集合，用于双通道状态聚合
     */
    private final Set<String> mqttOnlineDevices = ConcurrentHashMap.newKeySet();

    public DeviceStateManager(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    // ---- MQTT 在线状态管理 ----

    /**
     * 设置设备的 MQTT 在线状态
     *
     * @param deviceId 设备ID
     * @param online   是否在线
     */
    public void setMqttOnline(String deviceId, boolean online) {
        if (online) {
            mqttOnlineDevices.add(deviceId);
        } else {
            mqttOnlineDevices.remove(deviceId);
        }
        logger.debug("设备 MQTT 状态更新 - DeviceId: {}, Online: {}", deviceId, online);
    }

    /**
     * 查询设备是否通过 MQTT 在线
     *
     * @param deviceId 设备ID
     * @return 是否 MQTT 在线
     */
    public boolean isMqttOnline(String deviceId) {
        return mqttOnlineDevices.contains(deviceId);
    }

    /**
     * 获取所有 MQTT 在线设备 ID 集合
     *
     * @return MQTT 在线设备 ID 的不可变副本
     */
    public Set<String> getAllMqttOnlineDevices() {
        return Set.copyOf(mqttOnlineDevices);
    }

    /**
     * 查询设备是否通过 WebSocket 在线
     *
     * @param deviceId 设备ID
     * @return 是否 WebSocket 在线
     */
    public boolean isWebSocketOnline(String deviceId) {
        ChatSession session = sessionRegistry.getSessionByDeviceId(deviceId);
        return session != null && session.isOpen();
    }

    /**
     * 查询设备是否在线（任一通道在线即视为在线）
     * 聚合 WebSocket 和 MQTT 两个通道的状态
     *
     * @param deviceId 设备ID
     * @return 是否在线
     */
    public boolean isDeviceOnline(String deviceId) {
        return isWebSocketOnline(deviceId) || isMqttOnline(deviceId);
    }

    /**
     * 获取设备三态状态
     * online: WebSocket 已连接，设备正在活跃通话
     * standby: 仅 MQTT 在线，设备待机可唤醒
     * offline: 所有通道均离线
     *
     * @param deviceId 设备ID
     * @return 设备状态字符串（online / standby / offline）
     */
    public String getDeviceState(String deviceId) {
        boolean wsOnline = isWebSocketOnline(deviceId);
        boolean mqttOnline = isMqttOnline(deviceId);
        if (wsOnline) {
            return "online";
        }
        if (mqttOnline) {
            return "standby";
        }
        return "offline";
    }

    /**
     * 获取设备的在线通道信息
     *
     * @param deviceId 设备ID
     * @return 在线通道描述
     */
    public String getOnlineChannels(String deviceId) {
        boolean ws = isWebSocketOnline(deviceId);
        boolean mqtt = isMqttOnline(deviceId);
        if (ws && mqtt) {
            return "WebSocket+MQTT";
        } else if (ws) {
            return "WebSocket";
        } else if (mqtt) {
            return "MQTT";
        } else {
            return "离线";
        }
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

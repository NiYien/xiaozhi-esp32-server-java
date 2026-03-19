package com.xiaozhi.communication.common;

import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.dialogue.llm.memory.Conversation;
import com.xiaozhi.dialogue.llm.tool.ToolsSessionHolder;
import com.xiaozhi.dialogue.service.DialogueService;
import com.xiaozhi.dialogue.service.Persona;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.event.ChatSessionCloseEvent;
import com.xiaozhi.event.DeviceOnlineEvent;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.event.ChatSessionOpenEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Optional;

/**
 * WebSocket会话管理服务
 * 协调层：组合 5 个内部管理器，对外保持公开 API 不变
 * 负责事件发布和外部依赖注入，作为唯一的 Spring 入口点
 */
@Service
public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    // 服务关闭标志，关闭期间跳过设备状态写库（启动时会 bulk reset，无需重复写）
    private volatile boolean shuttingDown = false;

    // ---- 5 个内部管理器 ----
    private final SessionRegistry sessionRegistry = new SessionRegistry();
    private final AudioStreamManager audioStreamManager = new AudioStreamManager(sessionRegistry);
    private final DeviceStateManager deviceStateManager = new DeviceStateManager(sessionRegistry);
    private final CaptchaGenerationTracker captchaGenerationTracker = new CaptchaGenerationTracker();
    private final SessionActivityMonitor sessionActivityMonitor = new SessionActivityMonitor(sessionRegistry);

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    @Lazy
    private SysDeviceService deviceService;

    @Value("${check.inactive.session:true}")
    private boolean checkInactiveSession;

    @Value("${inactive.timeout.seconds:60}")
    private int inactiveTimeOutSeconds;

    private DialogueService getDialogueService() {
        return applicationContext.getBean(DialogueService.class);
    }

    /**
     * 初始化方法，启动定时检查不活跃会话的任务
     */
    @PostConstruct
    public void init() {
        sessionActivityMonitor.start(deviceService, checkInactiveSession, inactiveTimeOutSeconds, this::removeSession);
    }

    /**
     * 销毁方法，关闭定时任务执行器
     */
    public boolean isShuttingDown() {
        return shuttingDown;
    }

    @PreDestroy
    public void destroy() {
        sessionActivityMonitor.shutdown();
    }

    // ---- 会话注册表相关（委托给 SessionRegistry） ----

    /**
     * 注册新的会话
     *
     * @param sessionId 会话ID
     * @param chatSession  会话
     */
    public void registerSession(String sessionId, ChatSession chatSession) {
        sessionRegistry.register(sessionId, chatSession);
        applicationContext.publishEvent(new ChatSessionOpenEvent(chatSession));
    }

    /**
     * 关闭并清理WebSocket会话
     *
     * @param sessionId 会话ID
     */
    public void removeSession(String sessionId){
        sessionRegistry.remove(sessionId);
    }

    /**
     * 关闭并清理WebSocket会话
     *
     * @param sessionId 会话ID
     */
    public void closeSession(String sessionId){
        ChatSession chatSession = sessionRegistry.getSession(sessionId);
        if(chatSession != null) {
            closeSession(chatSession);
        }
    }

    /**
     * 关闭并清理WebSocket会话
     * 使用虚拟线程实现异步处理
     *
     * @param chatSession 聊天session
     */
    public void closeSession(ChatSession chatSession) {
        if(chatSession == null){
            return;
        }
        try {
            if(chatSession instanceof WebSocketSession){
                sessionRegistry.remove(chatSession.getSessionId());
                // 先关闭WebSocket连接
                chatSession.close();

                applicationContext.publishEvent(new ChatSessionCloseEvent(chatSession));
                logger.info("会话已关闭 - SessionId: {} SessionType: {}", chatSession.getSessionId(), chatSession.getClass().getSimpleName());
            }
            chatSession.clearAudioSinks();

        } catch (Exception e) {
            logger.error("清理会话资源时发生错误 - SessionId: {}",
                    chatSession.getSessionId(), e);
        }
    }

    /**
     * 获取会话
     *
     * @param sessionId 会话ID
     * @return WebSocket会话
     */
    public ChatSession getSession(String sessionId) {
        return sessionRegistry.getSession(sessionId);
    }

    /**
     * 根据设备ID获取会话
     *
     * @param deviceId 设备ID
     * @return 会话对象，如果不存在则返回null
     */
    public ChatSession getSessionByDeviceId(String deviceId) {
        return sessionRegistry.getSessionByDeviceId(deviceId);
    }

    // ---- 设备状态相关（委托给 DeviceStateManager） ----

    /**
     * 注册设备配置
     *
     * @param sessionId 会话ID
     * @param device    设备信息
     */
    public void registerDevice(String sessionId, SysDevice device) {
        ChatSession chatSession = deviceStateManager.registerDevice(sessionId, device);
        if (chatSession != null) {
            updateLastActivity(sessionId); // 更新活动时间
            applicationContext.publishEvent(new DeviceOnlineEvent(this, device.getDeviceId()));
        }
    }

    /**
     * 获取设备配置
     *
     * @param sessionId 会话ID
     * @return 设备配置
     */
    public SysDevice getDeviceConfig(String sessionId) {
        return deviceStateManager.getDeviceConfig(sessionId);
    }

    /**
     * 获取会话的function holder
     *
     * @param sessionId 会话ID
     * @return FunctionSessionHolder
     */
    public ToolsSessionHolder getFunctionSessionHolder(String sessionId) {
        return deviceStateManager.getFunctionSessionHolder(sessionId);
    }

    /**
     * 获取用户的可用角色列表
     *
     * @param sessionId 会话ID
     * @return 角色列表
     */
    public List<SysRole> getAvailableRoles(String sessionId) {
        return deviceStateManager.getAvailableRoles(sessionId);
    }

    /**
     * 是否在播放音乐
     *
     * @param sessionId 会话ID
     * @return 是否正在播放音乐
     */
    public boolean isPlaying(String sessionId) {
        return deviceStateManager.isPlaying(sessionId);
    }

    /**
     * 设备状态
     *
     * @param sessionId
     * @param mode  设备状态 auto/realTime
     */
    public void setMode(String sessionId, ListenMode mode) {
        deviceStateManager.setMode(sessionId, mode);
    }

    /**
     * 获取设备状态
     *
     * @param sessionId
     */
    public ListenMode getMode(String sessionId) {
        return deviceStateManager.getMode(sessionId);
    }

    /**
     * 设置流式识别状态
     *
     * @param sessionId   会话ID
     * @param isStreaming 是否正在流式识别
     */
    public void setStreamingState(String sessionId, boolean isStreaming) {
        deviceStateManager.setStreamingState(sessionId, isStreaming);
        updateLastActivity(sessionId); // 更新活动时间
    }

    /**
     * 获取流式识别状态
     *
     * @param sessionId 会话ID
     * @return 是否正在流式识别
     */
    public boolean isStreaming(String sessionId) {
        return deviceStateManager.isStreaming(sessionId);
    }

    public Optional<Conversation> findConversation(String deviceId) {
        return deviceStateManager.findConversation(deviceId);
    }

    // ---- 音频流相关（委托给 AudioStreamManager） ----

    /**
     * 创建音频数据流
     *
     * @param sessionId 会话ID
     */
    public void createAudioStream(String sessionId) {
        audioStreamManager.createAudioStream(sessionId);
    }

    /**
     * 获取音频数据流
     *
     * @param sessionId 会话ID
     * @return 音频数据流
     */
    public Sinks.Many<byte[]> getAudioStream(String sessionId) {
        return audioStreamManager.getAudioStream(sessionId);
    }

    /**
     * 发送音频数据
     *
     * @param sessionId 会话ID
     * @param data 音频数据
     */
    public void sendAudioData(String sessionId, byte[] data) {
        audioStreamManager.sendAudioData(sessionId, data);
    }

    /**
     * 完成音频流
     *
     * @param sessionId 会话ID
     */
    public void completeAudioStream(String sessionId) {
        audioStreamManager.completeAudioStream(sessionId);
    }

    /**
     * 关闭音频流
     *
     * @param sessionId 会话ID
     */
    public void closeAudioStream(String sessionId) {
        audioStreamManager.closeAudioStream(sessionId);
    }

    // ---- 验证码追踪（委托给 CaptchaGenerationTracker） ----

    /**
     * 标记设备正在生成验证码
     *
     * @param deviceId 设备ID
     * @return 如果设备之前没有在生成验证码，返回true；否则返回false
     */
    public boolean markCaptchaGeneration(String deviceId) {
        return captchaGenerationTracker.markCaptchaGeneration(deviceId);
    }

    /**
     * 取消设备验证码生成标记
     *
     * @param deviceId 设备ID
     */
    public void unmarkCaptchaGeneration(String deviceId) {
        captchaGenerationTracker.unmarkCaptchaGeneration(deviceId);
    }

    /**
     * 获取当前在线设备数量
     *
     * @return 在线设备数量
     */
    public int getOnlineDeviceCount() {
        return (int) sessionRegistry.getAllSessions().stream()
                .filter(session -> session.getSysDevice() != null)
                .count();
    }

    // ---- 活动监控（委托给 SessionActivityMonitor） ----

    /**
     * 更新会话的最后有效活动时间
     * 这个方法应该只在检测到实际的用户活动时调用，如语音输入或明确的交互
     *
     * @param sessionId 会话ID
     */
    public void updateLastActivity(String sessionId) {
        sessionActivityMonitor.updateLastActivity(sessionId);
    }

    // ---- MQTT 在线状态（委托给 DeviceStateManager） ----

    /**
     * 设置设备的 MQTT 在线状态
     *
     * @param deviceId 设备ID
     * @param online   是否在线
     */
    public void setMqttOnline(String deviceId, boolean online) {
        deviceStateManager.setMqttOnline(deviceId, online);
    }

    /**
     * 查询设备是否通过 MQTT 在线
     *
     * @param deviceId 设备ID
     * @return 是否 MQTT 在线
     */
    public boolean isMqttOnline(String deviceId) {
        return deviceStateManager.isMqttOnline(deviceId);
    }

    /**
     * 获取设备三态状态（online / standby / offline）
     *
     * @param deviceId 设备ID
     * @return 设备状态字符串
     */
    public String getDeviceState(String deviceId) {
        return deviceStateManager.getDeviceState(deviceId);
    }

    /**
     * 查询设备是否在线（任一通道在线即视为在线）
     *
     * @param deviceId 设备ID
     * @return 是否在线
     */
    public boolean isDeviceOnline(String deviceId) {
        return deviceStateManager.isDeviceOnline(deviceId);
    }

    /**
     * 获取设备的在线通道信息
     *
     * @param deviceId 设备ID
     * @return 在线通道描述
     */
    public String getOnlineChannels(String deviceId) {
        return deviceStateManager.getOnlineChannels(deviceId);
    }
}

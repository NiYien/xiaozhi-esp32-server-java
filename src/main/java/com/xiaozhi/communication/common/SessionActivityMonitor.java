package com.xiaozhi.communication.common;

import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.service.SysDeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 会话活动监控器
 * 负责定时检查不活跃会话并自动关闭，管理 ScheduledExecutorService 生命周期
 */
public class SessionActivityMonitor {
    private static final Logger logger = LoggerFactory.getLogger(SessionActivityMonitor.class);

    private final SessionRegistry sessionRegistry;

    // 定时任务执行器，在 start() 时创建
    private ScheduledExecutorService scheduler;

    public SessionActivityMonitor(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * 启动监控，由 SessionManager 的 @PostConstruct 调用
     *
     * @param deviceService          设备服务，用于启动时重置设备状态
     * @param checkInactiveSession   是否启用不活跃会话检查
     * @param inactiveTimeOutSeconds 不活跃超时时间（秒）
     * @param removeSessionCallback  移除会话的回调，由 SessionManager 提供
     */
    public void start(SysDeviceService deviceService,
                      boolean checkInactiveSession,
                      int inactiveTimeOutSeconds,
                      Consumer<String> removeSessionCallback) {
        if (checkInactiveSession) {
            scheduler = Executors.newSingleThreadScheduledExecutor();

            // 项目启动时，将所有设备状态设置为离线
            // 延迟执行设备状态重置，避免循环依赖
            scheduler.schedule(() -> {
                try {
                    SysDevice device = new SysDevice();
                    device.setState(SysDevice.DEVICE_STATE_OFFLINE);
                    // 不设置deviceId，这样会更新所有设备
                    int updatedRows = deviceService.update(device);
                    logger.info("项目启动，重置 {} 个设备状态为离线", updatedRows);
                } catch (Exception e) {
                    logger.error("项目启动时设置设备状态为离线失败", e);
                }
            }, 1, TimeUnit.SECONDS);

            // 定期检查不活跃的会话
            scheduler.scheduleAtFixedRate(
                    () -> checkInactiveSessions(inactiveTimeOutSeconds, removeSessionCallback),
                    10, 10, TimeUnit.SECONDS);
            logger.info("不活跃会话检查任务已启动，超时时间: {}秒", inactiveTimeOutSeconds);
        }
    }

    /**
     * 关闭监控，由 SessionManager 的 @PreDestroy 调用
     */
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("不活跃会话检查任务已关闭");
        }
    }

    /**
     * 检查不活跃的会话并关闭它们
     * 使用虚拟线程实现异步处理
     */
    private void checkInactiveSessions(int inactiveTimeOutSeconds, Consumer<String> removeSessionCallback) {
        Thread.startVirtualThread(() -> {
            Instant now = Instant.now();
            sessionRegistry.getAllSessions().forEach(session -> {
                if (session instanceof WebSocketSession) {
                    Instant lastActivity = session.getLastActivityTime();
                    if (lastActivity != null) {
                        Duration inactiveDuration = Duration.between(lastActivity, now);
                        if (inactiveDuration.getSeconds() > inactiveTimeOutSeconds) {
                            logger.info("会话 {} 已经 {} 秒没有有效活动，发送超时提示并自动关闭",
                                    session.getSessionId(), inactiveDuration.getSeconds());
                            // 长时间不活跃，可以直接清理ASR还没有被识别的音频数据
                            session.clearAudioSinks();
                            if (session.getPersona() != null) {
                                // 不涉及ASR了
                                session.getPersona().sendGoodbyeMessage();
                            }
                            if (session instanceof WebSocketSession) {
                                // 解绑WebSocket会话，回收Session对象。
                                removeSessionCallback.accept(session.getSessionId());
                            }
                        }
                    }
                }
            });
        });
    }

    /**
     * 更新会话的最后有效活动时间
     *
     * @param sessionId 会话ID
     */
    public void updateLastActivity(String sessionId) {
        ChatSession session = sessionRegistry.getSession(sessionId);
        if (session != null) {
            session.setLastActivityTime(Instant.now());
        }
    }
}

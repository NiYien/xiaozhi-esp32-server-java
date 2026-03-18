package com.xiaozhi.communication.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话注册表
 * 负责管理会话的注册、移除、查找等基础操作
 * 持有 sessions ConcurrentHashMap，作为唯一的会话存储
 */
public class SessionRegistry {
    private static final Logger logger = LoggerFactory.getLogger(SessionRegistry.class);

    // 用于存储所有连接的会话信息
    private final ConcurrentHashMap<String, ChatSession> sessions = new ConcurrentHashMap<>();

    /**
     * 注册新的会话
     *
     * @param sessionId   会话ID
     * @param chatSession 会话
     */
    public void register(String sessionId, ChatSession chatSession) {
        sessions.put(sessionId, chatSession);
        logger.info("会话已注册 - SessionId: {}  SessionType: {}", sessionId, chatSession.getClass().getSimpleName());
    }

    /**
     * 移除会话
     *
     * @param sessionId 会话ID
     */
    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * 获取会话
     *
     * @param sessionId 会话ID
     * @return 会话对象
     */
    public ChatSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 根据设备ID获取会话
     *
     * @param deviceId 设备ID
     * @return 会话对象，如果不存在则返回null
     */
    public ChatSession getSessionByDeviceId(String deviceId) {
        return sessions.values().stream()
                .filter(session -> session.getSysDevice() != null && deviceId.equals(session.getSysDevice().getDeviceId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取所有会话
     *
     * @return 所有会话的集合
     */
    public Collection<ChatSession> getAllSessions() {
        return sessions.values();
    }
}

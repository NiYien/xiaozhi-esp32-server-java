package com.xiaozhi.mcp.endpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP 接入点会话管理器
 */
@Service
public class McpEndpointSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(McpEndpointSessionManager.class);

    private static final int MAX_CONNECTIONS_PER_USER = 5;
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, McpEndpointSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, AtomicInteger> userConnectionCount = new ConcurrentHashMap<>();
    /** token -> sessionId 映射，用于按 token 查找连接 */
    private final ConcurrentHashMap<String, String> tokenSessionMap = new ConcurrentHashMap<>();

    /**
     * 检查用户是否可以创建新连接
     */
    public boolean canConnect(Integer userId) {
        AtomicInteger count = userConnectionCount.computeIfAbsent(userId, k -> new AtomicInteger(0));
        return count.get() < MAX_CONNECTIONS_PER_USER;
    }

    /**
     * 注册新会话
     */
    public boolean registerSession(McpEndpointSession session) {
        AtomicInteger count = userConnectionCount.computeIfAbsent(session.getUserId(), k -> new AtomicInteger(0));
        while (true) {
            int current = count.get();
            if (current >= MAX_CONNECTIONS_PER_USER) {
                return false;
            }
            if (count.compareAndSet(current, current + 1)) {
                break;
            }
        }
        sessions.put(session.getSessionId(), session);
        tokenSessionMap.put(session.getToken(), session.getSessionId());
        logger.info("MCP接入点连接已注册 - SessionId: {}, UserId: {}, 当前连接数: {}",
                session.getSessionId(), session.getUserId(), count.get());
        return true;
    }

    /**
     * 移除会话
     */
    public McpEndpointSession removeSession(String sessionId) {
        McpEndpointSession session = sessions.remove(sessionId);
        if (session != null) {
            tokenSessionMap.remove(session.getToken());
            AtomicInteger count = userConnectionCount.get(session.getUserId());
            if (count != null) {
                int remaining = count.decrementAndGet();
                if (remaining <= 0) {
                    userConnectionCount.remove(session.getUserId());
                }
            }
            logger.info("MCP接入点连接已移除 - SessionId: {}, UserId: {}", sessionId, session.getUserId());
        }
        return session;
    }

    public McpEndpointSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 按 token 断开连接
     */
    public void disconnectByToken(String token) {
        String sessionId = tokenSessionMap.get(token);
        if (sessionId != null) {
            McpEndpointSession session = sessions.get(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    session.getWsSession().close(CloseStatus.NORMAL);
                } catch (Exception e) {
                    logger.warn("断开MCP接入点连接失败 - SessionId: {}", sessionId, e);
                }
            }
        }
    }

    /**
     * 获取某用户的所有活跃会话
     */
    public List<McpEndpointSession> getSessionsByUserId(Integer userId) {
        List<McpEndpointSession> result = new ArrayList<>();
        for (McpEndpointSession session : sessions.values()) {
            if (userId.equals(session.getUserId())) {
                result.add(session);
            }
        }
        return result;
    }

    /**
     * 定时清理空闲超时连接（每5分钟）
     */
    @Scheduled(fixedRate = 300000)
    public void cleanIdleSessions() {
        Instant cutoff = Instant.now().minus(IDLE_TIMEOUT);
        for (Map.Entry<String, McpEndpointSession> entry : sessions.entrySet()) {
            McpEndpointSession session = entry.getValue();
            if (session.getLastActivityTime().isBefore(cutoff)) {
                logger.info("清理空闲MCP接入点连接 - SessionId: {}, UserId: {}",
                        session.getSessionId(), session.getUserId());
                try {
                    if (session.isOpen()) {
                        // 关闭 WebSocket 会触发 afterConnectionClosed → cleanup 完整清理
                        session.getWsSession().close(CloseStatus.NORMAL);
                    } else {
                        // 连接已断但未清理，手动移除
                        removeSession(session.getSessionId());
                    }
                } catch (Exception e) {
                    // 关闭异常时兜底移除
                    removeSession(session.getSessionId());
                }
            }
        }
    }
}

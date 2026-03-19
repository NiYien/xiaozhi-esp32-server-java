package com.xiaozhi.mcp.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP SSE 连接管理器
 * 管理 SSE 会话生命周期，包括连接注册、超时清理、并发限制
 */
@Service
public class McpSseConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(McpSseConnectionManager.class);
    private static final String TAG = "McpSseConnectionManager";

    /**
     * 每个用户最大并发 SSE 连接数
     */
    private static final int MAX_CONNECTIONS_PER_USER = 5;

    /**
     * 空闲连接超时时间（30分钟）
     */
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    /**
     * SSE 超时时间（与空闲超时一致，单位毫秒）
     */
    public static final long SSE_TIMEOUT_MS = IDLE_TIMEOUT.toMillis();

    /**
     * sessionId -> McpSseSession
     */
    private final ConcurrentHashMap<String, McpSseSession> sessions = new ConcurrentHashMap<>();

    /**
     * userId -> 当前连接数
     */
    private final ConcurrentHashMap<Integer, AtomicInteger> userConnectionCount = new ConcurrentHashMap<>();

    /**
     * 创建新的 SSE 会话
     *
     * @param userId          用户ID
     * @param baseUrl         服务器基础URL
     * @return McpSseSession 或 null（超出并发限制时）
     */
    public McpSseSession createSession(Integer userId, String baseUrl) {
        // 检查并发连接限制（使用 CAS 循环保证原子性）
        AtomicInteger count = userConnectionCount.computeIfAbsent(userId, k -> new AtomicInteger(0));
        while (true) {
            int current = count.get();
            if (current >= MAX_CONNECTIONS_PER_USER) {
                logger.warn("[{}] 用户 {} 已达最大SSE连接数 {}", TAG, userId, MAX_CONNECTIONS_PER_USER);
                return null;
            }
            if (count.compareAndSet(current, current + 1)) {
                break;
            }
        }

        String sessionId = UUID.randomUUID().toString();
        String messageEndpoint = baseUrl + "/mcp/message?sessionId=" + sessionId;

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        McpSseSession session = new McpSseSession(sessionId, userId, emitter, messageEndpoint);

        // 注册回调
        emitter.onCompletion(() -> removeSession(sessionId));
        emitter.onTimeout(() -> {
            logger.info("[{}] SSE连接超时 - SessionId: {}", TAG, sessionId);
            removeSession(sessionId);
        });
        emitter.onError(ex -> {
            logger.warn("[{}] SSE连接异常 - SessionId: {}, Error: {}", TAG, sessionId, ex.getMessage());
            removeSession(sessionId);
        });

        sessions.put(sessionId, session);

        logger.info("[{}] 新建SSE连接 - SessionId: {}, UserId: {}, 当前连接数: {}",
                TAG, sessionId, userId, count.get());
        return session;
    }

    /**
     * 获取会话
     */
    public McpSseSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 移除会话
     */
    public void removeSession(String sessionId) {
        McpSseSession session = sessions.remove(sessionId);
        if (session != null) {
            AtomicInteger count = userConnectionCount.get(session.getUserId());
            if (count != null) {
                int remaining = count.decrementAndGet();
                if (remaining <= 0) {
                    userConnectionCount.remove(session.getUserId());
                }
            }
            logger.info("[{}] SSE连接已移除 - SessionId: {}, UserId: {}", TAG, sessionId, session.getUserId());
        }
    }

    /**
     * 定时清理空闲超时的连接（每5分钟执行一次）
     */
    @Scheduled(fixedRate = 300000)
    public void cleanIdleSessions() {
        Instant cutoff = Instant.now().minus(IDLE_TIMEOUT);
        for (Map.Entry<String, McpSseSession> entry : sessions.entrySet()) {
            McpSseSession session = entry.getValue();
            if (session.getLastActivityTime().isBefore(cutoff)) {
                logger.info("[{}] 清理空闲SSE连接 - SessionId: {}, UserId: {}",
                        TAG, session.getSessionId(), session.getUserId());
                try {
                    session.getEmitter().complete();
                } catch (Exception e) {
                    // 忽略关闭异常
                }
                removeSession(session.getSessionId());
            }
        }
    }
}

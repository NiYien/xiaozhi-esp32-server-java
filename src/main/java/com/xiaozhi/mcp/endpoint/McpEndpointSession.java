package com.xiaozhi.mcp.endpoint;

import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP 接入点会话，封装 WebSocket 连接状态和 JSON-RPC 请求关联
 */
public class McpEndpointSession {

    private final String sessionId;
    private final Integer userId;
    private final String token;
    private final WebSocketSession wsSession;
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    private final Map<Long, CompletableFuture<Map<String, Object>>> pendingRequests = new ConcurrentHashMap<>();
    private final List<String> registeredToolNames = new CopyOnWriteArrayList<>();
    private volatile Instant lastActivityTime;
    private volatile boolean initialized = false;

    public McpEndpointSession(String sessionId, Integer userId, String token, WebSocketSession wsSession) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.token = token;
        this.wsSession = wsSession;
        this.lastActivityTime = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public WebSocketSession getWsSession() {
        return wsSession;
    }

    public long nextRequestId() {
        return requestIdCounter.getAndIncrement();
    }

    public Map<Long, CompletableFuture<Map<String, Object>>> getPendingRequests() {
        return pendingRequests;
    }

    public List<String> getRegisteredToolNames() {
        return registeredToolNames;
    }

    public Instant getLastActivityTime() {
        return lastActivityTime;
    }

    public void updateActivity() {
        this.lastActivityTime = Instant.now();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public boolean isOpen() {
        return wsSession != null && wsSession.isOpen();
    }
}

package com.xiaozhi.mcp.server;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

/**
 * MCP SSE 会话，封装一个 SSE 连接的状态
 */
public class McpSseSession {

    private final String sessionId;
    private final Integer userId;
    private final SseEmitter emitter;
    private final String messageEndpoint;
    private volatile Instant lastActivityTime;

    public McpSseSession(String sessionId, Integer userId, SseEmitter emitter, String messageEndpoint) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.emitter = emitter;
        this.messageEndpoint = messageEndpoint;
        this.lastActivityTime = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public Integer getUserId() {
        return userId;
    }

    public SseEmitter getEmitter() {
        return emitter;
    }

    public String getMessageEndpoint() {
        return messageEndpoint;
    }

    public Instant getLastActivityTime() {
        return lastActivityTime;
    }

    public void updateActivity() {
        this.lastActivityTime = Instant.now();
    }
}

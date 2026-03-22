package com.xiaozhi.mcp.endpoint;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * MCP 接入点 WebSocket 处理器
 * 处理 mcp_pipe.py 连接的全生命周期
 */
@Component
public class McpWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(McpWebSocketHandler.class);

    @Resource
    private McpEndpointSessionManager mcpEndpointSessionManager;

    @Resource
    private McpEndpointService endpointService;

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) {
        Integer userId = (Integer) wsSession.getAttributes().get("userId");
        String token = (String) wsSession.getAttributes().get("token");

        if (userId == null || token == null) {
            try {
                wsSession.close(CloseStatus.NOT_ACCEPTABLE);
            } catch (Exception e) {
                // ignore
            }
            return;
        }

        // 创建并注册接入点会话
        McpEndpointSession session = new McpEndpointSession(
                wsSession.getId(), userId, token, wsSession);

        if (!mcpEndpointSessionManager.registerSession(session)) {
            logger.warn("MCP接入点连接数超限 - UserId: {}", userId);
            try {
                wsSession.close(new CloseStatus(4029, "Too many connections"));
            } catch (Exception e) {
                // ignore
            }
            return;
        }

        logger.info("MCP接入点已连接 - SessionId: {}, UserId: {}", wsSession.getId(), userId);

        // 异步执行 initialize 和 tools/list
        Thread.startVirtualThread(() -> endpointService.initialize(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        McpEndpointSession session = mcpEndpointSessionManager.getSession(wsSession.getId());
        if (session == null) {
            return;
        }

        // 将消息交给 endpointService 处理（匹配 pending request）
        endpointService.handleResponse(session, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        McpEndpointSession session = mcpEndpointSessionManager.removeSession(wsSession.getId());
        if (session != null) {
            // 清理工具注册和 pending requests
            endpointService.cleanup(session);
            logger.info("MCP接入点已断开 - SessionId: {}, UserId: {}, Status: {}",
                    wsSession.getId(), session.getUserId(), status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable exception) {
        logger.warn("MCP接入点传输错误 - SessionId: {}, Error: {}",
                wsSession.getId(), exception.getMessage());
    }
}

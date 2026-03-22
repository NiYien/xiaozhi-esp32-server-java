package com.xiaozhi.mcp.endpoint;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * MCP 接入点 WebSocket 配置
 * 注册 /ws/mcp/ 端点，用于外部 mcp_pipe.py 连接
 */
@Configuration
public class McpWebSocketConfig implements WebSocketConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(McpWebSocketConfig.class);

    public static final String MCP_WS_PATH = "/ws/mcp/";

    @Resource
    private McpWebSocketHandler mcpWebSocketHandler;

    @Resource
    private McpHandshakeInterceptor mcpHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mcpWebSocketHandler, MCP_WS_PATH)
                .addInterceptors(mcpHandshakeInterceptor)
                .setAllowedOrigins("*");

        logger.info("📡 MCP接入点WebSocket端点已注册: {}", MCP_WS_PATH);
    }
}

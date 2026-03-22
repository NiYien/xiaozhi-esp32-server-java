package com.xiaozhi.mcp.endpoint;

import com.xiaozhi.service.McpTokenService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * MCP 接入点 WebSocket 握手拦截器
 * 在握手阶段验证 token 参数
 */
@Component
public class McpHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(McpHandshakeInterceptor.class);

    @Resource
    private McpTokenService mcpTokenService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 从 query param 提取 token
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams().getFirst("token");

        if (token == null || token.isEmpty()) {
            logger.warn("MCP接入点握手失败：缺少token参数");
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        // 验证 token
        Integer userId = mcpTokenService.validate(token);
        if (userId == null) {
            logger.warn("MCP接入点握手失败：token无效或已禁用");
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        // 将 userId 和 token 存入 WebSocket session attributes
        attributes.put("userId", userId);
        attributes.put("token", token);

        // 异步更新最后使用时间
        mcpTokenService.updateLastUsedAtAsync(token);

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需额外处理
    }
}

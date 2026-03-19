package com.xiaozhi.mcp.server;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * MCP over SSE 控制器
 * 实现标准 MCP 协议的 SSE 传输：GET /mcp/sse 建立事件流，POST /mcp/message 发送 JSON-RPC 消息
 * 兼容 Claude Desktop、Cursor 等标准 MCP Client
 */
@RestController
@RequestMapping("/mcp")
@Tag(name = "MCP SSE 服务", description = "MCP over SSE 协议端点")
public class McpSseController {

    private static final Logger logger = LoggerFactory.getLogger(McpSseController.class);
    private static final String TAG = "McpSseController";

    /**
     * MCP 协议版本
     */
    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";

    private final ObjectMapper objectMapper;

    @Autowired
    private McpSseConnectionManager connectionManager;

    @Autowired
    private McpServerToolsService toolsService;

    public McpSseController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * SSE 连接端点
     * MCP Client 通过 GET /mcp/sse 建立 SSE 事件流连接
     * 服务端返回 endpoint 事件，告知客户端消息接收 URL
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "建立MCP SSE连接", description = "建立SSE事件流，返回消息端点URL")
    public ResponseEntity<?> handleSse(HttpServletRequest request) {
        // 认证：从 Header 或 query parameter 获取 Token
        Integer userId = authenticateRequest(request);
        if (userId == null) {
            // 认证失败时返回 401 状态码
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token");
        }

        // 构建基础URL
        String baseUrl = getBaseUrl(request);

        // 创建SSE会话
        McpSseSession session = connectionManager.createSession(userId, baseUrl);
        if (session == null) {
            // 超出并发连接限制时返回 429 状态码
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Too many connections");
        }

        SseEmitter emitter = session.getEmitter();

        // 发送 endpoint 事件，告知客户端消息发送地址
        try {
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data(session.getMessageEndpoint()));
            logger.info("[{}] SSE连接已建立 - SessionId: {}, UserId: {}, Endpoint: {}",
                    TAG, session.getSessionId(), userId, session.getMessageEndpoint());
        } catch (IOException e) {
            logger.error("[{}] 发送endpoint事件失败 - SessionId: {}", TAG, session.getSessionId(), e);
            connectionManager.removeSession(session.getSessionId());
        }

        return ResponseEntity.ok().header("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE).body(emitter);
    }

    /**
     * JSON-RPC 消息处理端点
     * MCP Client 通过 POST /mcp/message 发送 JSON-RPC 请求
     */
    @PostMapping(value = "/message", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "处理MCP JSON-RPC消息", description = "接收并处理MCP协议的JSON-RPC请求")
    public ResponseEntity<String> handleMessage(
            @RequestParam("sessionId") String sessionId,
            @RequestBody String body,
            HttpServletRequest request) {

        // 认证
        Integer userId = authenticateRequest(request);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\":\"未授权访问\"}");
        }

        // 获取会话
        McpSseSession session = connectionManager.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\":\"会话不存在或已过期\"}");
        }

        // 校验用户一致性
        if (!userId.equals(session.getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("{\"error\":\"无权访问此会话\"}");
        }

        session.updateActivity();

        try {
            JsonNode jsonRpcRequest = objectMapper.readTree(body);
            String method = jsonRpcRequest.has("method") ? jsonRpcRequest.get("method").asText() : "";
            JsonNode id = jsonRpcRequest.get("id");
            JsonNode params = jsonRpcRequest.get("params");

            logger.info("[{}] 收到JSON-RPC请求 - SessionId: {}, Method: {}", TAG, sessionId, method);

            // 处理JSON-RPC请求并获取响应
            ObjectNode response = processJsonRpcRequest(method, id, params, userId);

            // 如果是通知类消息（id 为 null），处理后直接返回 202，不通过 SSE 发送响应
            if (response == null) {
                return ResponseEntity.accepted().build();
            }

            // 通过SSE事件流发送响应
            String responseStr = objectMapper.writeValueAsString(response);
            try {
                session.getEmitter().send(SseEmitter.event()
                        .name("message")
                        .data(responseStr));
            } catch (IOException e) {
                logger.error("[{}] SSE发送响应失败 - SessionId: {}", TAG, sessionId, e);
                connectionManager.removeSession(sessionId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"error\":\"SSE发送失败\"}");
            }

            // POST 请求返回 202 Accepted
            return ResponseEntity.accepted().build();

        } catch (Exception e) {
            logger.error("[{}] 处理JSON-RPC请求失败 - SessionId: {}", TAG, sessionId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\":\"请求格式错误: " + e.getMessage() + "\"}");
        }
    }

    /**
     * 处理 JSON-RPC 请求
     */
    private ObjectNode processJsonRpcRequest(String method, JsonNode id, JsonNode params, Integer userId) {
        return switch (method) {
            case "initialize" -> handleInitialize(id);
            case "notifications/initialized" -> handleNotification(id);
            case "tools/list" -> handleToolsList(id);
            case "tools/call" -> handleToolsCall(id, params, userId);
            case "ping" -> handlePing(id);
            default -> buildErrorResponse(id, -32601, "方法不存在: " + method);
        };
    }

    /**
     * 处理 initialize 请求
     */
    private ObjectNode handleInitialize(JsonNode id) {
        ObjectNode response = createJsonRpcResponse(id);
        ObjectNode result = objectMapper.createObjectNode();

        result.put("protocolVersion", MCP_PROTOCOL_VERSION);

        ObjectNode capabilities = objectMapper.createObjectNode();
        ObjectNode toolsCap = objectMapper.createObjectNode();
        toolsCap.put("listChanged", false);
        capabilities.set("tools", toolsCap);
        result.set("capabilities", capabilities);

        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", "xiaozhi-mcp-server");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);

        response.set("result", result);
        return response;
    }

    /**
     * 处理 initialized 通知（无需响应）
     */
    private ObjectNode handleNotification(JsonNode id) {
        // 通知类消息不需要响应，但为了协议兼容返回空result
        if (id == null || id.isNull()) {
            return null;
        }
        ObjectNode response = createJsonRpcResponse(id);
        response.set("result", objectMapper.createObjectNode());
        return response;
    }

    /**
     * 处理 tools/list 请求
     */
    private ObjectNode handleToolsList(JsonNode id) {
        ObjectNode response = createJsonRpcResponse(id);
        ObjectNode result = objectMapper.createObjectNode();

        List<Map<String, Object>> tools = toolsService.getToolDefinitions();
        result.set("tools", objectMapper.valueToTree(tools));

        response.set("result", result);
        return response;
    }

    /**
     * 处理 tools/call 请求
     */
    private ObjectNode handleToolsCall(JsonNode id, JsonNode params, Integer userId) {
        if (params == null) {
            return buildErrorResponse(id, -32602, "缺少参数");
        }

        String toolName = params.has("name") ? params.get("name").asText() : "";
        JsonNode arguments = params.get("arguments");

        logger.info("[{}] 调用工具 - Name: {}, UserId: {}", TAG, toolName, userId);

        try {
            Object toolResult = executeToolCall(toolName, arguments, userId);

            ObjectNode response = createJsonRpcResponse(id);
            ObjectNode result = objectMapper.createObjectNode();

            // MCP 工具调用结果格式
            ObjectNode content = objectMapper.createObjectNode();
            content.put("type", "text");
            content.put("text", objectMapper.writeValueAsString(toolResult));

            result.set("content", objectMapper.createArrayNode().add(content));
            result.put("isError", false);

            response.set("result", result);
            return response;

        } catch (Exception e) {
            logger.error("[{}] 工具调用失败 - Name: {}, Error: {}", TAG, toolName, e.getMessage(), e);

            ObjectNode response = createJsonRpcResponse(id);
            ObjectNode result = objectMapper.createObjectNode();

            ObjectNode content = objectMapper.createObjectNode();
            content.put("type", "text");
            content.put("text", "工具调用失败: " + e.getMessage());

            result.set("content", objectMapper.createArrayNode().add(content));
            result.put("isError", true);

            response.set("result", result);
            return response;
        }
    }

    /**
     * 执行工具调用
     */
    private Object executeToolCall(String toolName, JsonNode arguments, Integer userId) {
        return switch (toolName) {
            case "get_device_list" -> toolsService.getDeviceList(userId);
            case "get_device_status" -> {
                String deviceId = getStringArg(arguments, "deviceId");
                yield toolsService.getDeviceStatus(userId, deviceId);
            }
            case "control_device" -> {
                String deviceId = getStringArg(arguments, "deviceId");
                String property = getStringArg(arguments, "property");
                Object value = getArg(arguments, "value");
                yield toolsService.controlDevice(userId, deviceId, property, value);
            }
            case "send_message" -> {
                String deviceId = getStringArg(arguments, "deviceId");
                String message = getStringArg(arguments, "message");
                yield toolsService.sendMessage(userId, deviceId, message);
            }
            case "get_chat_history" -> {
                String deviceId = getStringArg(arguments, "deviceId");
                int limit = arguments != null && arguments.has("limit") ? arguments.get("limit").asInt(20) : 20;
                yield toolsService.getChatHistory(userId, deviceId, limit);
            }
            default -> throw new IllegalArgumentException("未知工具: " + toolName);
        };
    }

    /**
     * 处理 ping 请求
     */
    private ObjectNode handlePing(JsonNode id) {
        ObjectNode response = createJsonRpcResponse(id);
        response.set("result", objectMapper.createObjectNode());
        return response;
    }

    /**
     * 创建 JSON-RPC 响应骨架
     */
    private ObjectNode createJsonRpcResponse(JsonNode id) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null && !id.isNull()) {
            response.set("id", id);
        }
        return response;
    }

    /**
     * 创建 JSON-RPC 错误响应
     */
    private ObjectNode buildErrorResponse(JsonNode id, int code, String message) {
        ObjectNode response = createJsonRpcResponse(id);
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);
        return response;
    }

    /**
     * 认证请求，支持 Header 和 query parameter 两种方式
     *
     * @return 用户ID，认证失败返回 null
     */
    private Integer authenticateRequest(HttpServletRequest request) {
        try {
            // 优先从 Authorization Header 获取
            String authHeader = request.getHeader("Authorization");
            String tokenValue = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                tokenValue = authHeader.substring(7).trim();
            }

            // 回退：从 query parameter 获取
            if (tokenValue == null || tokenValue.isEmpty()) {
                tokenValue = request.getParameter("token");
            }

            if (tokenValue == null || tokenValue.isEmpty()) {
                logger.warn("[{}] 未提供认证Token", TAG);
                return null;
            }

            // 使用 Sa-Token 验证
            Object loginId = StpUtil.getLoginIdByToken(tokenValue);
            if (loginId == null) {
                logger.warn("[{}] Token无效或已过期", TAG);
                return null;
            }

            return Integer.valueOf(loginId.toString());
        } catch (Exception e) {
            logger.warn("[{}] 认证失败: {}", TAG, e.getMessage());
            return null;
        }
    }

    /**
     * 获取请求的基础URL
     */
    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        // 检查是否有反向代理的 header
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null) {
            scheme = forwardedProto;
        }
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (forwardedHost != null) {
            serverName = forwardedHost;
            serverPort = -1; // host中可能已包含端口
        }

        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        if (serverPort > 0 && serverPort != 80 && serverPort != 443) {
            url.append(":").append(serverPort);
        }
        return url.toString();
    }

    /**
     * 从 JSON 参数中获取字符串值
     */
    private String getStringArg(JsonNode arguments, String key) {
        if (arguments == null || !arguments.has(key)) {
            return null;
        }
        return arguments.get(key).asText();
    }

    /**
     * 从 JSON 参数中获取值（自动转换类型）
     */
    private Object getArg(JsonNode arguments, String key) {
        if (arguments == null || !arguments.has(key)) {
            return null;
        }
        JsonNode node = arguments.get(key);
        if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isInt()) {
            return node.asInt();
        } else if (node.isDouble() || node.isFloat()) {
            return node.asDouble();
        } else if (node.isLong()) {
            return node.asLong();
        } else {
            return node.asText();
        }
    }
}

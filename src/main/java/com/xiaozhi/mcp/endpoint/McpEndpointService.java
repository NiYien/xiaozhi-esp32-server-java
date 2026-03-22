package com.xiaozhi.mcp.endpoint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.dialogue.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * MCP 接入点核心服务：initialize、tools/list、tools/call
 */
@Service
public class McpEndpointService {

    private static final Logger logger = LoggerFactory.getLogger(McpEndpointService.class);
    private static final int REQUEST_TIMEOUT_SECONDS = 30;
    private static final int MAX_TOOLS_COUNT = 32;
    private static final String TOOL_PREFIX = "endpoint_";

    @Resource
    private ToolsGlobalRegistry toolsGlobalRegistry;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 连接建立后执行初始化流程：initialize → tools/list
     */
    public void initialize(McpEndpointSession session) {
        try {
            // 1. 发送 initialize
            Map<String, Object> initParams = new HashMap<>();
            initParams.put("protocolVersion", "2024-11-05");
            initParams.put("clientInfo", Map.of("name", "xiaozhi-server", "version", "1.0.0"));
            initParams.put("capabilities", Map.of());

            Map<String, Object> initResult = sendRequest(session, "initialize", initParams);
            if (initResult != null) {
                session.setInitialized(true);
                logger.info("MCP接入点 initialize 成功 - SessionId: {}, UserId: {}",
                        session.getSessionId(), session.getUserId());
            } else {
                logger.warn("MCP接入点 initialize 超时 - SessionId: {}", session.getSessionId());
                return;
            }

            // 2. 发送 notifications/initialized（通知，无需响应）
            sendNotification(session, "notifications/initialized", null);

            // 3. 获取工具列表
            fetchAndRegisterTools(session, null);

        } catch (Exception e) {
            logger.error("MCP接入点初始化失败 - SessionId: {}, Error: {}",
                    session.getSessionId(), e.getMessage(), e);
        }
    }

    /**
     * 获取并注册工具列表（支持分页）
     */
    private void fetchAndRegisterTools(McpEndpointSession session, String cursor) {
        Map<String, Object> params = new HashMap<>();
        if (cursor != null && !cursor.isEmpty()) {
            params.put("cursor", cursor);
        }

        Map<String, Object> result = sendRequest(session, "tools/list", params.isEmpty() ? null : params);
        if (result == null) {
            logger.warn("MCP接入点 tools/list 超时 - SessionId: {}", session.getSessionId());
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        if (tools == null || tools.isEmpty()) {
            return;
        }

        int currentCount = session.getRegisteredToolNames().size();
        if (currentCount + tools.size() > MAX_TOOLS_COUNT) {
            logger.warn("MCP接入点工具数量超限 - SessionId: {}, 已有: {}, 新增: {}",
                    session.getSessionId(), currentCount, tools.size());
            return;
        }

        for (Map<String, Object> tool : tools) {
            registerTool(session, tool);
        }

        // 分页处理
        Object nextCursor = result.get("nextCursor");
        if (nextCursor != null && !nextCursor.toString().isEmpty()) {
            fetchAndRegisterTools(session, nextCursor.toString());
        } else {
            logger.info("MCP接入点工具加载完成 - SessionId: {}, UserId: {}, 工具数: {}",
                    session.getSessionId(), session.getUserId(), session.getRegisteredToolNames().size());
        }
    }

    /**
     * 注册单个工具到用户级工具注册表
     */
    private void registerTool(McpEndpointSession session, Map<String, Object> tool) {
        String name = (String) tool.get("name");
        String description = (String) tool.get("description");
        Object inputSchema = tool.get("inputSchema");
        String toolName = TOOL_PREFIX + name.replace(".", "_");

        ToolCallback toolCallback = FunctionToolCallback
                .builder(toolName, (Map<String, Object> params, ToolContext toolContext) -> {
                    // 调用远端工具
                    Map<String, Object> callParams = Map.of("name", name, "arguments", params);
                    Map<String, Object> response = sendRequest(session, "tools/call", callParams);
                    if (response != null) {
                        Object content = response.get("content");
                        if (content != null) {
                            return content;
                        }
                        return response;
                    }
                    return "工具调用超时";
                })
                .toolMetadata(ToolMetadata.builder().returnDirect(false).build())
                .description(description != null ? description : toolName)
                .inputSchema(JsonUtil.toJson(inputSchema))
                .inputType(Map.class)
                .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                .build();

        toolsGlobalRegistry.registerUserTool(session.getUserId(), toolName, toolCallback);
        session.getRegisteredToolNames().add(toolName);
    }

    /**
     * 发送 JSON-RPC 请求并等待响应
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> sendRequest(McpEndpointSession session, String method, Object params) {
        if (!session.isOpen()) {
            return null;
        }
        long id = session.nextRequestId();
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        session.getPendingRequests().put(id, future);

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("method", method);
            request.put("id", id);
            if (params != null) {
                request.put("params", params);
            }

            String json = objectMapper.writeValueAsString(request);
            session.getWsSession().sendMessage(new TextMessage(json));
            session.updateActivity();

            Map<String, Object> response = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return response;
        } catch (Exception e) {
            logger.error("MCP接入点请求失败 - SessionId: {}, Method: {}, Error: {}",
                    session.getSessionId(), method, e.getMessage());
            return null;
        } finally {
            session.getPendingRequests().remove(id);
        }
    }

    /**
     * 发送通知（无 id，不等待响应）
     */
    public void sendNotification(McpEndpointSession session, String method, Object params) {
        if (!session.isOpen()) {
            return;
        }
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("jsonrpc", "2.0");
            notification.put("method", method);
            if (params != null) {
                notification.put("params", params);
            }
            String json = objectMapper.writeValueAsString(notification);
            session.getWsSession().sendMessage(new TextMessage(json));
        } catch (Exception e) {
            logger.warn("MCP接入点发送通知失败 - SessionId: {}, Method: {}", session.getSessionId(), method, e);
        }
    }

    /**
     * 处理接收到的 JSON-RPC 响应，完成对应的 CompletableFuture
     */
    @SuppressWarnings("unchecked")
    public void handleResponse(McpEndpointSession session, String message) {
        try {
            Map<String, Object> jsonRpc = objectMapper.readValue(message,
                    new TypeReference<Map<String, Object>>() {});

            Object idObj = jsonRpc.get("id");
            if (idObj == null) {
                // 通知消息，忽略
                return;
            }

            long id;
            if (idObj instanceof Number) {
                id = ((Number) idObj).longValue();
            } else {
                id = Long.parseLong(idObj.toString());
            }

            CompletableFuture<Map<String, Object>> future = session.getPendingRequests().get(id);
            if (future != null) {
                Map<String, Object> result = (Map<String, Object>) jsonRpc.get("result");
                if (result != null) {
                    future.complete(result);
                } else {
                    Map<String, Object> error = (Map<String, Object>) jsonRpc.get("error");
                    if (error != null) {
                        logger.warn("MCP接入点返回错误 - SessionId: {}, Id: {}, Error: {}",
                                session.getSessionId(), id, error);
                        future.complete(null);
                    } else {
                        future.complete(new HashMap<>());
                    }
                }
            }

            session.updateActivity();
        } catch (Exception e) {
            logger.error("MCP接入点处理响应失败 - SessionId: {}, Error: {}",
                    session.getSessionId(), e.getMessage(), e);
        }
    }

    /**
     * 清理接入点注册的工具和 pending requests
     */
    public void cleanup(McpEndpointSession session) {
        // 清理工具注册
        Integer userId = session.getUserId();
        for (String toolName : session.getRegisteredToolNames()) {
            toolsGlobalRegistry.unregisterUserTool(userId, toolName);
        }
        session.getRegisteredToolNames().clear();

        // 完成所有 pending futures
        for (CompletableFuture<Map<String, Object>> future : session.getPendingRequests().values()) {
            future.completeExceptionally(new RuntimeException("MCP接入点连接已断开"));
        }
        session.getPendingRequests().clear();

        logger.info("MCP接入点资源已清理 - SessionId: {}, UserId: {}", session.getSessionId(), userId);
    }
}

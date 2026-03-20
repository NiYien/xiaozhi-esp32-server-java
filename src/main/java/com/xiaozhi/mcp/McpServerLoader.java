package com.xiaozhi.mcp;

import com.xiaozhi.dao.McpServerMapper;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.entity.SysMcpServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.net.URI;
import java.net.http.HttpRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Server 动态加载服务
 * 启动时自动加载所有已启用的 MCP Server，注册工具到 ToolsGlobalRegistry
 */
@Service
public class McpServerLoader {

    private static final Logger logger = LoggerFactory.getLogger(McpServerLoader.class);

    @Resource
    private McpServerMapper mcpServerMapper;

    @Resource
    private ToolsGlobalRegistry toolsGlobalRegistry;

    /**
     * 维护已连接的 MCP Client，key 为 serverCode
     */
    private final ConcurrentHashMap<String, McpSyncClient> connectedClients = new ConcurrentHashMap<>();

    /**
     * 维护每个 server 注册的工具名称列表，key 为 serverCode
     */
    private final ConcurrentHashMap<String, List<String>> registeredTools = new ConcurrentHashMap<>();

    /**
     * 应用启动时加载所有已启用的 MCP Server
     */
    @PostConstruct
    public void init() {
        try {
            List<SysMcpServer> enabledServers = mcpServerMapper.selectEnabled();
            if (enabledServers.isEmpty()) {
                logger.info("没有已启用的 MCP Server 需要加载");
                return;
            }
            logger.info("开始加载 {} 个已启用的 MCP Server", enabledServers.size());
            for (SysMcpServer server : enabledServers) {
                try {
                    loadServer(server);
                    logger.info("MCP Server [{}] 加载成功", server.getServerName());
                } catch (Exception e) {
                    logger.error("MCP Server [{}] 加载失败: {}", server.getServerName(), e.getMessage(), e);
                }
            }
            logger.info("MCP Server 加载完成");
        } catch (Exception e) {
            logger.error("加载 MCP Server 列表失败", e);
        }
    }

    /**
     * 加载单个 MCP Server：连接并注册工具
     */
    public void loadServer(SysMcpServer server) {
        String serverCode = server.getServerCode();

        // 先卸载旧连接
        unloadServer(serverCode);

        // 创建 MCP Client 并连接
        logger.info("正在连接 MCP Server [{}], url={}, transport={}", server.getServerName(), server.getServerUrl(), server.getTransportType());
        McpSyncClient mcpClient = createMcpClient(server);
        logger.info("MCP Client 已创建，开始 initialize 握手...");
        mcpClient.initialize();
        logger.info("MCP Client initialize 握手完成");

        // 获取工具列表并注册
        List<String> toolNames = registerToolsFromClient(serverCode, mcpClient);

        // 保存连接和工具列表
        connectedClients.put(serverCode, mcpClient);
        registeredTools.put(serverCode, toolNames);

        logger.info("MCP Server [{}] 注册了 {} 个工具", server.getServerName(), toolNames.size());
    }

    /**
     * 卸载单个 MCP Server：断开连接并清理工具注册
     */
    public void unloadServer(String serverCode) {
        // 清理工具注册
        List<String> tools = registeredTools.remove(serverCode);
        if (tools != null) {
            for (String toolName : tools) {
                toolsGlobalRegistry.unregisterFunction(toolName);
            }
            logger.info("已清理 MCP Server [{}] 的 {} 个工具", serverCode, tools.size());
        }

        // 关闭连接
        McpSyncClient client = connectedClients.remove(serverCode);
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                logger.warn("关闭 MCP Client [{}] 时出错: {}", serverCode, e.getMessage());
            }
        }
    }

    /**
     * 重新加载所有 MCP Server
     */
    public void reloadAll() {
        logger.info("开始重新加载所有 MCP Server");

        // 先卸载所有
        List<String> serverCodes = new ArrayList<>(connectedClients.keySet());
        for (String serverCode : serverCodes) {
            unloadServer(serverCode);
        }

        // 重新加载所有已启用的
        List<SysMcpServer> enabledServers = mcpServerMapper.selectEnabled();
        for (SysMcpServer server : enabledServers) {
            try {
                loadServer(server);
                logger.info("MCP Server [{}] 重新加载成功", server.getServerName());
            } catch (Exception e) {
                logger.error("MCP Server [{}] 重新加载失败: {}", server.getServerName(), e.getMessage(), e);
            }
        }

        logger.info("MCP Server 重新加载完成");
    }

    /**
     * 测试 MCP Server 连接
     */
    public Map<String, Object> testConnection(SysMcpServer server) {
        Map<String, Object> result = new HashMap<>();
        McpSyncClient mcpClient = null;
        try {
            mcpClient = createMcpClient(server);
            mcpClient.initialize();

            // 获取工具列表
            List<McpSchema.Tool> tools = mcpClient.listTools().tools();
            List<String> toolNames = tools.stream()
                    .map(McpSchema.Tool::name)
                    .toList();

            result.put("success", true);
            result.put("message", "连接成功");
            result.put("toolCount", tools.size());
            result.put("tools", toolNames);
        } catch (Exception e) {
            logger.error("MCP 连接测试失败, url={}, transport={}, authType={}",
                    server.getServerUrl(), server.getTransportType(), server.getAuthType(), e);
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
        } finally {
            if (mcpClient != null) {
                try {
                    mcpClient.close();
                } catch (Exception e) {
                    logger.warn("关闭测试 MCP Client 时出错: {}", e.getMessage());
                }
            }
        }
        return result;
    }

    /**
     * 创建 MCP Client
     */
    private McpSyncClient createMcpClient(SysMcpServer server) {
        McpClientTransport transport;

        if ("streamable_http".equals(server.getTransportType())) {
            // Streamable HTTP 传输
            HttpClientStreamableHttpTransport.Builder builder =
                    HttpClientStreamableHttpTransport.builder(server.getServerUrl())
                            .connectTimeout(Duration.ofSeconds(10));

            // 处理认证
            if (!"none".equals(server.getAuthType()) && server.getAuthToken() != null) {
                builder.customizeRequest(requestBuilder -> {
                    if ("bearer".equals(server.getAuthType())) {
                        requestBuilder.header("Authorization", "Bearer " + server.getAuthToken());
                    } else if ("api_key".equals(server.getAuthType())) {
                        requestBuilder.header("Authorization", "ApiKey " + server.getAuthToken());
                    }
                });
            }

            transport = builder.build();
        } else {
            // SSE 传输（默认）
            // 处理 URL：如果以 /sse 结尾，分离 baseUri 和 sseEndpoint
            String serverUrl = server.getServerUrl();
            String sseEndpoint = "/sse";
            if (serverUrl.endsWith("/sse")) {
                sseEndpoint = "/sse";
                serverUrl = serverUrl.substring(0, serverUrl.length() - 4);
            }

            String authHeader = null;
            if (!"none".equals(server.getAuthType()) && server.getAuthToken() != null) {
                if ("bearer".equals(server.getAuthType())) {
                    authHeader = "Bearer " + server.getAuthToken();
                } else if ("api_key".equals(server.getAuthType())) {
                    authHeader = "ApiKey " + server.getAuthToken();
                }
            }

            // 使用 final 变量供 lambda 捕获
            final String finalAuthHeader = authHeader;
            final String finalServerUrl = serverUrl;

            HttpClientSseClientTransport.Builder builder =
                    HttpClientSseClientTransport.builder(finalServerUrl)
                            .sseEndpoint(sseEndpoint)
                            .connectTimeout(Duration.ofSeconds(30));

            if (finalAuthHeader != null) {
                // requestBuilder 设置默认 header（同时应用到 SSE GET 和 POST 请求）
                builder.requestBuilder(HttpRequest.newBuilder()
                        .uri(URI.create(finalServerUrl))
                        .header("Authorization", finalAuthHeader));
                // customizeRequest 确保 POST 请求也带 header
                builder.customizeRequest(rb -> rb.header("Authorization", finalAuthHeader));
            }

            transport = builder.build();
        }

        return McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(60))
                .initializationTimeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * 从 MCP Client 获取工具并注册到全局注册表
     */
    private List<String> registerToolsFromClient(String serverCode, McpSyncClient mcpClient) {
        List<String> toolNames = new ArrayList<>();

        // 使用 SyncMcpToolCallbackProvider 获取 ToolCallback 列表
        SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(mcpClient);
        ToolCallback[] callbacks = provider.getToolCallbacks();

        for (ToolCallback callback : callbacks) {
            String originalName = callback.getToolDefinition().name();
            String prefixedName = serverCode + "_" + originalName;
            toolsGlobalRegistry.registerFunction(prefixedName, callback);
            toolNames.add(prefixedName);
        }

        return toolNames;
    }
}

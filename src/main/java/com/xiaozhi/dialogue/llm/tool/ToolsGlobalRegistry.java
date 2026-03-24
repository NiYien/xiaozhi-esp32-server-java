package com.xiaozhi.dialogue.llm.tool;

import com.xiaozhi.communication.common.ChatSession;
import jakarta.annotation.Resource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ToolsGlobalRegistry implements ToolCallbackResolver {
    private final Logger logger = LoggerFactory.getLogger(ToolsGlobalRegistry.class);
    private static final String TAG = "FUNCTION_GLOBAL";

    // 用于存储所有function列表
    protected static final ConcurrentHashMap<String, ToolCallback> allFunction
            = new ConcurrentHashMap<>();

    // 用户接入点工具（按 userId 隔离）
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<String, ToolCallback>> userEndpointTools
            = new ConcurrentHashMap<>();

    @Resource
    protected List<GlobalFunction> globalFunctions;

    @Override
    public ToolCallback resolve(@NotNull String toolName) {
        return allFunction.get(toolName);
    }

    /**
     * Register a function by name
     *
     * @param name the name of the function to register
     * @return the registered function or null if not found
     */
    public ToolCallback registerFunction(String name, ToolCallback functionCallTool) {
        ToolCallback result = allFunction.putIfAbsent(name, functionCallTool);
        return result;
    }

    /**
     * Unregister a function by name
     *
     * @param name the name of the function to unregister
     * @return true if successful, false otherwise
     */
    public boolean unregisterFunction(String name) {
        // Check if the function exists before unregistering
        if (!allFunction.containsKey(name)) {
            return false;
        }
        allFunction.remove(name);
        return true;
    }

    /**
     * Get all registered functions
     *
     * @return a map of all registered functions
     */
    public Map<String, ToolCallback> getAllFunctions(ChatSession chatSession) {
        Map<String, ToolCallback> tempFunctions = new HashMap<>();
        // 包含通过 registerFunction 注册的工具（MCP Server 工具等）
        tempFunctions.putAll(allFunction);
        // 包含 GlobalFunction 接口实现的工具（IoT 等内置工具）
        globalFunctions.forEach(
                globalFunction -> {
                    ToolCallback toolCallback = globalFunction.getFunctionCallTool(chatSession);
                    if(toolCallback != null){
                        tempFunctions.put(toolCallback.getToolDefinition().name(), toolCallback);
                    }
                }
        );
        return tempFunctions;
    }

    // ========== 用户接入点工具管理 ==========

    /**
     * 注册用户接入点工具
     */
    public void registerUserTool(Integer userId, String name, ToolCallback toolCallback) {
        userEndpointTools.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(name, toolCallback);
    }

    /**
     * 卸载用户接入点的单个工具
     */
    public void unregisterUserTool(Integer userId, String name) {
        ConcurrentHashMap<String, ToolCallback> tools = userEndpointTools.get(userId);
        if (tools != null) {
            tools.remove(name);
            if (tools.isEmpty()) {
                userEndpointTools.remove(userId);
            }
        }
    }

    /**
     * 获取用户接入点的所有工具
     */
    public Map<String, ToolCallback> getUserEndpointTools(Integer userId) {
        ConcurrentHashMap<String, ToolCallback> tools = userEndpointTools.get(userId);
        return tools != null ? new HashMap<>(tools) : new HashMap<>();
    }

    public interface GlobalFunction{
        ToolCallback getFunctionCallTool(ChatSession chatSession);
    }
}

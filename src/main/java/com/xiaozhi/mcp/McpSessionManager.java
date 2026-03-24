package com.xiaozhi.mcp;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.dialogue.llm.tool.ToolsSessionHolder;
import com.xiaozhi.service.McpToolExcludeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * MCP会话管理器
 */
@Service
public class McpSessionManager {
    private static final Logger logger = LoggerFactory.getLogger(McpSessionManager.class);
    
    @Autowired
    private McpToolExcludeService mcpToolExcludeService;
    
    @Autowired
    private ToolsGlobalRegistry toolsGlobalRegistry;

    public void customMcpHandler(ChatSession chatSession) {
        Integer roleId = chatSession.getSysDevice().getRoleId();
        Integer userId = chatSession.getSysDevice().getUserId();
        ToolsSessionHolder functionSessionHolder = chatSession.getFunctionSessionHolder();
        
        // 获取所有排除的工具列表（全局和角色级别）
        Set<String> excludedTools = mcpToolExcludeService.getExcludedTools(userId, roleId);

        // 排除逻辑仅在会话级别过滤，不从全局注册表中移除工具
        // 避免一个用户/角色的排除配置影响所有用户

        // 处理设备指定的functionNames（如果设备有指定特定的函数列表）
        String functionNames = chatSession.getSysDevice().getFunctionNames();
        if (functionNames != null && !functionNames.isEmpty()) {
            String[] functionNameArr = functionNames.split(",");
            for (String functionName : functionNameArr) {
                functionName = functionName.trim();
                // 只注册未被排除的工具
                if (!excludedTools.contains(functionName)) {
                    functionSessionHolder.registerFunction(functionName);
                }
            }
            return; // 如果设备指定了函数列表，则只注册指定的函数，不注册其他工具
        }
        
        // 注册系统全局工具（带过滤）
        Map<String, ToolCallback> globalFunctions = toolsGlobalRegistry.getAllFunctions(chatSession);

        globalFunctions.forEach((toolName, toolCallback) -> {
            // 只注册未被排除的工具
            if (!excludedTools.contains(toolName)) {
                functionSessionHolder.registerFunction(toolName, toolCallback);
            }
        });

        // 注册用户接入点工具（带过滤）
        Map<String, ToolCallback> endpointTools = toolsGlobalRegistry.getUserEndpointTools(userId);
        endpointTools.forEach((toolName, toolCallback) -> {
            if (!excludedTools.contains(toolName)) {
                functionSessionHolder.registerFunction(toolName, toolCallback);
            }
        });

    }
    
}
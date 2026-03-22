package com.xiaozhi.controller;

import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.entity.SysMcpToken;
import com.xiaozhi.service.McpTokenService;
import com.xiaozhi.utils.CmsUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import com.xiaozhi.common.config.ServerAddressProvider;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Token 管理控制器
 */
@RestController
@RequestMapping("/api/mcpToken")
@Tag(name = "MCP Token 管理", description = "MCP接入点Token的增删改查")
public class McpTokenController extends BaseController {

    @Resource
    private McpTokenService mcpTokenService;

    @Autowired
    private ServerAddressProvider serverAddressProvider;

    /**
     * 查询Token列表（脱敏）
     */
    @GetMapping("")
    @Operation(summary = "查询Token列表", description = "查询当前用户的MCP Token列表，Token值脱敏显示")
    public ResultMessage list() {
        try {
            Integer userId = CmsUtils.getUserId();
            List<SysMcpToken> tokens = mcpTokenService.list(userId);
            // 脱敏：仅显示后4位
            for (SysMcpToken token : tokens) {
                maskToken(token);
            }
            return ResultMessage.success(tokens);
        } catch (Exception e) {
            logger.error("查询MCP Token列表失败", e);
            return ResultMessage.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 创建Token（返回完整Token，仅一次）
     */
    @PostMapping("")
    @Operation(summary = "创建Token", description = "创建新的MCP Token，完整Token值仅在创建时返回一次")
    public ResultMessage create(@RequestParam(required = false) String tokenName) {
        try {
            Integer userId = CmsUtils.getUserId();
            SysMcpToken token = mcpTokenService.generate(userId, tokenName);
            return ResultMessage.success("创建成功", token);
        } catch (IllegalArgumentException e) {
            return ResultMessage.error(e.getMessage());
        } catch (Exception e) {
            logger.error("创建MCP Token失败", e);
            return ResultMessage.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 启用Token
     */
    @PutMapping("/{id}/enable")
    @Operation(summary = "启用Token")
    public ResultMessage enable(@PathVariable Long id) {
        try {
            Integer userId = CmsUtils.getUserId();
            int rows = mcpTokenService.enable(id, userId);
            return rows > 0 ? ResultMessage.success("启用成功") : ResultMessage.error("操作失败");
        } catch (Exception e) {
            logger.error("启用MCP Token失败", e);
            return ResultMessage.error("操作失败: " + e.getMessage());
        }
    }

    /**
     * 禁用Token
     */
    @PutMapping("/{id}/disable")
    @Operation(summary = "禁用Token")
    public ResultMessage disable(@PathVariable Long id) {
        try {
            Integer userId = CmsUtils.getUserId();
            int rows = mcpTokenService.disable(id, userId);
            return rows > 0 ? ResultMessage.success("禁用成功") : ResultMessage.error("操作失败");
        } catch (Exception e) {
            logger.error("禁用MCP Token失败", e);
            return ResultMessage.error("操作失败: " + e.getMessage());
        }
    }

    /**
     * 删除Token
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除Token")
    public ResultMessage delete(@PathVariable Long id) {
        try {
            Integer userId = CmsUtils.getUserId();
            int rows = mcpTokenService.delete(id, userId);
            return rows > 0 ? ResultMessage.success("删除成功") : ResultMessage.error("删除失败");
        } catch (Exception e) {
            logger.error("删除MCP Token失败", e);
            return ResultMessage.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取接入点URL模板
     */
    @GetMapping("/endpoint")
    @Operation(summary = "获取接入点URL", description = "返回MCP WebSocket接入点地址模板")
    public ResultMessage getEndpointUrl() {
        try {
            String serverAddress = serverAddressProvider.getServerAddress();
            String wsScheme = serverAddress.startsWith("https") ? "wss" : "ws";
            String host = serverAddress.replaceFirst("^https?://", "");
            String endpointUrl = wsScheme + "://" + host + "/ws/mcp/?token=";
            Map<String, String> result = new HashMap<>();
            result.put("endpointUrl", endpointUrl);
            return ResultMessage.success(result);
        } catch (Exception e) {
            logger.error("获取接入点URL失败", e);
            return ResultMessage.error("获取失败: " + e.getMessage());
        }
    }

    private void maskToken(SysMcpToken token) {
        String raw = token.getToken();
        if (raw != null && raw.length() > 4) {
            token.setToken("****" + raw.substring(raw.length() - 4));
        }
    }
}

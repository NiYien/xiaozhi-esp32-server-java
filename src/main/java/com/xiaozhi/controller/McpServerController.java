package com.xiaozhi.controller;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.entity.SysMcpServer;
import com.xiaozhi.service.SysMcpServerService;
import com.xiaozhi.utils.CmsUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 管理控制器
 */
@RestController
@RequestMapping("/api/mcpServer")
@Tag(name = "MCP Server 管理", description = "MCP Server 配置的增删改查、连接测试、刷新等操作")
public class McpServerController extends BaseController {

    @Resource
    private SysMcpServerService mcpServerService;

    /**
     * 查询 MCP Server 列表
     */
    @GetMapping("")
    @Operation(summary = "查询 MCP Server 列表", description = "分页查询 MCP Server 配置列表")
    public ResultMessage list(SysMcpServer mcpServer, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            List<SysMcpServer> list = mcpServerService.query(mcpServer, pageFilter);
            return ResultMessage.success(list);
        } catch (Exception e) {
            logger.error("查询 MCP Server 列表失败", e);
            return ResultMessage.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 添加 MCP Server
     */
    @PostMapping("")
    @Operation(summary = "添加 MCP Server", description = "创建新的 MCP Server 配置")
    public ResultMessage add(SysMcpServer mcpServer) {
        try {
            mcpServer.setUserId(CmsUtils.getUserId());
            int rows = mcpServerService.add(mcpServer);
            if (rows > 0) {
                return ResultMessage.success("添加成功");
            }
            return ResultMessage.error("添加失败");
        } catch (IllegalArgumentException e) {
            return ResultMessage.error(e.getMessage());
        } catch (Exception e) {
            logger.error("添加 MCP Server 失败", e);
            return ResultMessage.error("添加失败: " + e.getMessage());
        }
    }

    /**
     * 编辑 MCP Server
     */
    @PutMapping("")
    @Operation(summary = "编辑 MCP Server", description = "更新已有的 MCP Server 配置")
    public ResultMessage update(SysMcpServer mcpServer) {
        try {
            int rows = mcpServerService.update(mcpServer);
            if (rows > 0) {
                return ResultMessage.success("更新成功");
            }
            return ResultMessage.error("更新失败");
        } catch (IllegalArgumentException e) {
            return ResultMessage.error(e.getMessage());
        } catch (Exception e) {
            logger.error("更新 MCP Server 失败", e);
            return ResultMessage.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除 MCP Server
     */
    @DeleteMapping("/{serverId}")
    @Operation(summary = "删除 MCP Server", description = "删除指定的 MCP Server 配置并清理工具注册")
    public ResultMessage delete(@PathVariable Long serverId) {
        try {
            int rows = mcpServerService.delete(serverId);
            if (rows > 0) {
                return ResultMessage.success("删除成功");
            }
            return ResultMessage.error("删除失败");
        } catch (Exception e) {
            logger.error("删除 MCP Server 失败", e);
            return ResultMessage.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 测试 MCP Server 连接
     */
    @PostMapping("/test")
    @Operation(summary = "测试连接", description = "测试 MCP Server 连接是否可用，返回工具列表")
    public ResultMessage testConnection(SysMcpServer mcpServer) {
        try {
            Map<String, Object> result = mcpServerService.testConnection(mcpServer);
            boolean success = (boolean) result.get("success");
            if (success) {
                return ResultMessage.success("连接成功", result);
            } else {
                return ResultMessage.error((String) result.get("message"));
            }
        } catch (Exception e) {
            logger.error("测试 MCP Server 连接失败", e);
            return ResultMessage.error("测试失败: " + e.getMessage());
        }
    }

    /**
     * 刷新所有 MCP Server 工具注册
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新 MCP Server", description = "重新加载所有已启用的 MCP Server 并更新工具注册")
    public ResultMessage refresh() {
        try {
            mcpServerService.refreshAll();
            return ResultMessage.success("刷新成功");
        } catch (Exception e) {
            logger.error("刷新 MCP Server 失败", e);
            return ResultMessage.error("刷新失败: " + e.getMessage());
        }
    }
}

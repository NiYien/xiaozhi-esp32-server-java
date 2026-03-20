package com.xiaozhi.service;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysMcpServer;

import java.util.List;
import java.util.Map;

/**
 * MCP Server 配置管理服务
 */
public interface SysMcpServerService {

    /**
     * 添加 MCP Server
     *
     * @param mcpServer MCP Server 配置
     * @return 影响行数
     */
    int add(SysMcpServer mcpServer);

    /**
     * 更新 MCP Server
     *
     * @param mcpServer MCP Server 配置
     * @return 影响行数
     */
    int update(SysMcpServer mcpServer);

    /**
     * 删除 MCP Server
     *
     * @param serverId 服务器ID
     * @return 影响行数
     */
    int delete(Long serverId);

    /**
     * 查询 MCP Server 列表
     *
     * @param mcpServer 查询条件
     * @param pageFilter 分页参数
     * @return MCP Server 列表
     */
    List<SysMcpServer> query(SysMcpServer mcpServer, PageFilter pageFilter);

    /**
     * 根据 ID 查询
     *
     * @param serverId 服务器ID
     * @return MCP Server 配置
     */
    SysMcpServer selectById(Long serverId);

    /**
     * 测试 MCP Server 连接
     *
     * @param mcpServer MCP Server 配置
     * @return 测试结果，包含工具列表等信息
     */
    Map<String, Object> testConnection(SysMcpServer mcpServer);

    /**
     * 刷新所有 MCP Server 工具注册
     */
    void refreshAll();
}

package com.xiaozhi.dao;

import com.xiaozhi.entity.SysMcpServer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MCP Server 配置 数据层
 */
@Mapper
public interface McpServerMapper {

    /**
     * 查询 MCP Server 列表
     */
    List<SysMcpServer> query(SysMcpServer mcpServer);

    /**
     * 根据 ID 查询
     */
    SysMcpServer selectById(@Param("serverId") Long serverId);

    /**
     * 根据 serverCode 查询
     */
    SysMcpServer selectByServerCode(@Param("serverCode") String serverCode);

    /**
     * 查询所有已启用的 MCP Server
     */
    List<SysMcpServer> selectEnabled();

    /**
     * 新增 MCP Server
     */
    int add(SysMcpServer mcpServer);

    /**
     * 更新 MCP Server
     */
    int update(SysMcpServer mcpServer);

    /**
     * 删除 MCP Server
     */
    int delete(@Param("serverId") Long serverId);
}

package com.xiaozhi.service.impl;

import com.github.pagehelper.PageHelper;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.dao.McpServerMapper;
import com.xiaozhi.entity.SysMcpServer;
import com.xiaozhi.mcp.McpServerLoader;
import com.xiaozhi.service.SysMcpServerService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP Server 配置管理服务实现
 */
@Service
public class SysMcpServerServiceImpl extends BaseServiceImpl implements SysMcpServerService {

    private static final Logger logger = LoggerFactory.getLogger(SysMcpServerServiceImpl.class);

    @Resource
    private McpServerMapper mcpServerMapper;

    @Lazy
    @Resource
    private McpServerLoader mcpServerLoader;

    @Override
    public int add(SysMcpServer mcpServer) {
        // 自动生成 serverCode（如果未提供）
        if (!StringUtils.hasText(mcpServer.getServerCode())) {
            mcpServer.setServerCode(generateServerCode());
        }

        // 校验 serverCode 唯一性
        SysMcpServer existing = mcpServerMapper.selectByServerCode(mcpServer.getServerCode());
        if (existing != null) {
            throw new IllegalArgumentException("服务器标识已存在: " + mcpServer.getServerCode());
        }

        // 设置默认值
        if (mcpServer.getTransportType() == null) {
            mcpServer.setTransportType("sse");
        }
        if (mcpServer.getAuthType() == null) {
            mcpServer.setAuthType("none");
        }
        if (mcpServer.getEnabled() == null) {
            mcpServer.setEnabled(1);
        }

        Date now = new Date();
        mcpServer.setCreateTime(now);
        mcpServer.setUpdateTime(now);

        return mcpServerMapper.add(mcpServer);
    }

    @Override
    public int update(SysMcpServer mcpServer) {
        // 如果修改了 serverCode，校验唯一性
        if (StringUtils.hasText(mcpServer.getServerCode())) {
            SysMcpServer existing = mcpServerMapper.selectByServerCode(mcpServer.getServerCode());
            if (existing != null && !existing.getServerId().equals(mcpServer.getServerId())) {
                throw new IllegalArgumentException("服务器标识已存在: " + mcpServer.getServerCode());
            }
        }

        // 查询更新前的旧数据，用于检测状态变化
        SysMcpServer oldServer = mcpServerMapper.selectById(mcpServer.getServerId());

        mcpServer.setUpdateTime(new Date());
        int result = mcpServerMapper.update(mcpServer);

        // 根据 enabled 状态变化自动加载/卸载 MCP Server
        if (oldServer != null && result > 0) {
            Integer oldEnabled = oldServer.getEnabled();
            Integer newEnabled = mcpServer.getEnabled();

            if (newEnabled != null && oldEnabled != null && !newEnabled.equals(oldEnabled)) {
                // enabled 状态发生变化
                if (newEnabled == 0) {
                    // 禁用：卸载 Server 并清理工具注册
                    logger.info("MCP Server [{}] 被禁用，自动卸载工具", oldServer.getServerName());
                    mcpServerLoader.unloadServer(oldServer.getServerCode());
                } else if (newEnabled == 1) {
                    // 启用：加载 Server 并注册工具
                    logger.info("MCP Server [{}] 被启用，自动加载工具", oldServer.getServerName());
                    // 合并更新后的完整信息用于加载
                    SysMcpServer updatedServer = mcpServerMapper.selectById(mcpServer.getServerId());
                    mcpServerLoader.loadServer(updatedServer);
                }
            } else if (oldEnabled != null && oldEnabled == 1
                    && (newEnabled == null || newEnabled == 1)) {
                // enabled 未变化且 Server 处于启用状态，检查其他关键字段是否变化
                boolean configChanged = false;
                if (StringUtils.hasText(mcpServer.getServerUrl())
                        && !mcpServer.getServerUrl().equals(oldServer.getServerUrl())) {
                    configChanged = true;
                }
                if (mcpServer.getTransportType() != null
                        && !mcpServer.getTransportType().equals(oldServer.getTransportType())) {
                    configChanged = true;
                }
                if (mcpServer.getAuthType() != null
                        && !mcpServer.getAuthType().equals(oldServer.getAuthType())) {
                    configChanged = true;
                }
                if (mcpServer.getAuthToken() != null
                        && !mcpServer.getAuthToken().equals(oldServer.getAuthToken())) {
                    configChanged = true;
                }
                if (configChanged) {
                    logger.info("MCP Server [{}] 配置变更，自动重新加载工具", oldServer.getServerName());
                    SysMcpServer updatedServer = mcpServerMapper.selectById(mcpServer.getServerId());
                    mcpServerLoader.loadServer(updatedServer);
                }
            }
        }

        return result;
    }

    @Override
    public int delete(Long serverId) {
        // 删除前先清理工具注册
        SysMcpServer mcpServer = mcpServerMapper.selectById(serverId);
        if (mcpServer != null) {
            mcpServerLoader.unloadServer(mcpServer.getServerCode());
        }
        return mcpServerMapper.delete(serverId);
    }

    @Override
    public List<SysMcpServer> query(SysMcpServer mcpServer, PageFilter pageFilter) {
        if (pageFilter != null) {
            PageHelper.startPage(pageFilter.getStart(), pageFilter.getLimit());
        }
        List<SysMcpServer> list = mcpServerMapper.query(mcpServer);

        // 脱敏 authToken
        list.forEach(this::maskAuthToken);

        return list;
    }

    @Override
    public SysMcpServer selectById(Long serverId) {
        return mcpServerMapper.selectById(serverId);
    }

    @Override
    public Map<String, Object> testConnection(SysMcpServer mcpServer) {
        return mcpServerLoader.testConnection(mcpServer);
    }

    @Override
    public void refreshAll() {
        mcpServerLoader.reloadAll();
    }

    /**
     * 生成 serverCode
     */
    private String generateServerCode() {
        return "mcp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 脱敏 authToken（保留前4位 + ***）
     */
    private void maskAuthToken(SysMcpServer mcpServer) {
        String authToken = mcpServer.getAuthToken();
        if (StringUtils.hasText(authToken)) {
            if (authToken.length() > 4) {
                mcpServer.setAuthToken(authToken.substring(0, 4) + "***");
            } else {
                mcpServer.setAuthToken("***");
            }
        }
    }
}

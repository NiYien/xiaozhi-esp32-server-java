package com.xiaozhi.service.impl;

import com.xiaozhi.base.BaseUnitTest;
import com.xiaozhi.dao.McpToolExcludeMapper;
import com.xiaozhi.entity.SysMcpToolExclude;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * McpToolExcludeServiceImpl 单元测试
 * 测试工具排除的启用/禁用切换、全局/角色排除合并
 */
class McpToolExcludeServiceTest extends BaseUnitTest {

    @Mock
    private McpToolExcludeMapper mcpToolExcludeMapper;

    private McpToolExcludeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new McpToolExcludeServiceImpl();
        ReflectionTestUtils.setField(service, "mcpToolExcludeMapper", mcpToolExcludeMapper);
    }

    // ========== toggleRoleToolStatus ==========

    @Test
    void toggleRoleToolStatus_enable_removesToolFromExcludeList() {
        // 已有排除配置包含 toolA 和 toolB
        SysMcpToolExclude config = new SysMcpToolExclude();
        config.setId(1L);
        config.setExcludeTools("[\"toolA\",\"toolB\"]");

        when(mcpToolExcludeMapper.selectByCondition("role", "mcp_server", "server1", "10"))
                .thenReturn(List.of(config));

        // 启用 toolA，应从排除列表移除
        service.toggleRoleToolStatus(10, "toolA", "server1", true);

        // 应该更新配置，排除列表只剩 toolB
        ArgumentCaptor<SysMcpToolExclude> captor = ArgumentCaptor.forClass(SysMcpToolExclude.class);
        verify(mcpToolExcludeMapper).update(captor.capture());
        assertTrue(captor.getValue().getExcludeTools().contains("toolB"));
        assertFalse(captor.getValue().getExcludeTools().contains("toolA"));
    }

    @Test
    void toggleRoleToolStatus_enable_deletesConfigWhenListEmpty() {
        // 排除列表只有一个工具
        SysMcpToolExclude config = new SysMcpToolExclude();
        config.setId(1L);
        config.setExcludeTools("[\"toolA\"]");

        when(mcpToolExcludeMapper.selectByCondition("role", "mcp_server", "server1", "10"))
                .thenReturn(List.of(config));

        // 启用 toolA，排除列表为空后应删除配置
        service.toggleRoleToolStatus(10, "toolA", "server1", true);

        verify(mcpToolExcludeMapper).delete(1L);
        verify(mcpToolExcludeMapper, never()).update(any());
    }

    @Test
    void toggleRoleToolStatus_disable_createsNewConfig() {
        // 没有现有配置
        when(mcpToolExcludeMapper.selectByCondition("role", "mcp_server", "server1", "10"))
                .thenReturn(Collections.emptyList());

        // 禁用 toolA，应创建新配置
        service.toggleRoleToolStatus(10, "toolA", "server1", false);

        ArgumentCaptor<SysMcpToolExclude> captor = ArgumentCaptor.forClass(SysMcpToolExclude.class);
        verify(mcpToolExcludeMapper).add(captor.capture());
        assertEquals("role", captor.getValue().getExcludeType());
        assertEquals("10", captor.getValue().getBindKey());
        assertTrue(captor.getValue().getExcludeTools().contains("toolA"));
    }

    @Test
    void toggleRoleToolStatus_disable_addsToExistingList() {
        // 已有排除配置包含 toolA
        SysMcpToolExclude config = new SysMcpToolExclude();
        config.setId(1L);
        config.setExcludeTools("[\"toolA\"]");

        when(mcpToolExcludeMapper.selectByCondition("role", "mcp_server", "server1", "10"))
                .thenReturn(List.of(config));

        // 禁用 toolB，应追加到排除列表
        service.toggleRoleToolStatus(10, "toolB", "server1", false);

        ArgumentCaptor<SysMcpToolExclude> captor = ArgumentCaptor.forClass(SysMcpToolExclude.class);
        verify(mcpToolExcludeMapper).update(captor.capture());
        String excludeTools = captor.getValue().getExcludeTools();
        assertTrue(excludeTools.contains("toolA"));
        assertTrue(excludeTools.contains("toolB"));
    }

    // ========== toggleGlobalToolStatus ==========

    @Test
    void toggleGlobalToolStatus_disable_createsNewGlobalConfig() {
        when(mcpToolExcludeMapper.selectByCondition("global", "mcp_server", "server1", "0"))
                .thenReturn(Collections.emptyList());

        service.toggleGlobalToolStatus("toolX", "server1", false);

        ArgumentCaptor<SysMcpToolExclude> captor = ArgumentCaptor.forClass(SysMcpToolExclude.class);
        verify(mcpToolExcludeMapper).add(captor.capture());
        assertEquals("global", captor.getValue().getExcludeType());
        assertEquals("0", captor.getValue().getBindKey());
        assertTrue(captor.getValue().getExcludeTools().contains("toolX"));
    }

    @Test
    void toggleGlobalToolStatus_enable_removesFromGlobalList() {
        SysMcpToolExclude config = new SysMcpToolExclude();
        config.setId(2L);
        config.setExcludeTools("[\"toolX\",\"toolY\"]");

        when(mcpToolExcludeMapper.selectByCondition("global", "mcp_server", "server1", "0"))
                .thenReturn(List.of(config));

        service.toggleGlobalToolStatus("toolX", "server1", true);

        ArgumentCaptor<SysMcpToolExclude> captor = ArgumentCaptor.forClass(SysMcpToolExclude.class);
        verify(mcpToolExcludeMapper).update(captor.capture());
        assertFalse(captor.getValue().getExcludeTools().contains("toolX"));
        assertTrue(captor.getValue().getExcludeTools().contains("toolY"));
    }

    // ========== getExcludedTools ==========

    @Test
    void getExcludedTools_mergesGlobalAndRoleExclusions() {
        // 注意：getExcludedTools 内部调用了 private 方法 getGlobalExcludedTools 和 getRoleExcludedTools
        // 因为 @Cacheable 在非 Spring 容器环境下不生效，这些方法会直接执行

        // 全局排除配置
        SysMcpToolExclude globalConfig = new SysMcpToolExclude();
        globalConfig.setExcludeTools("[\"globalTool\"]");
        when(mcpToolExcludeMapper.selectByCondition("global", null, null, "0"))
                .thenReturn(List.of(globalConfig));

        // 角色排除配置
        SysMcpToolExclude roleConfig = new SysMcpToolExclude();
        roleConfig.setExcludeTools("[\"roleTool\"]");
        when(mcpToolExcludeMapper.selectByCondition("role", null, null, "5"))
                .thenReturn(List.of(roleConfig));

        Set<String> result = service.getExcludedTools(1, 5);

        assertTrue(result.contains("globalTool"), "应包含全局排除的工具");
        assertTrue(result.contains("roleTool"), "应包含角色排除的工具");
        assertEquals(2, result.size());
    }

    @Test
    void getExcludedTools_nullRoleId_returnsOnlyGlobal() {
        SysMcpToolExclude globalConfig = new SysMcpToolExclude();
        globalConfig.setExcludeTools("[\"globalTool\"]");
        when(mcpToolExcludeMapper.selectByCondition("global", null, null, "0"))
                .thenReturn(List.of(globalConfig));

        Set<String> result = service.getExcludedTools(1, null);

        assertTrue(result.contains("globalTool"));
        assertEquals(1, result.size());
        // 不应查询角色排除
        verify(mcpToolExcludeMapper, never()).selectByCondition(eq("role"), any(), any(), any());
    }
}

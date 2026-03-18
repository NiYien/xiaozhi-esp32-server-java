package com.xiaozhi.service.impl;

import com.xiaozhi.base.BaseUnitTest;
import com.xiaozhi.dao.ConfigMapper;
import com.xiaozhi.entity.SysAgent;
import com.xiaozhi.entity.SysConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SysAgentServiceImpl 单元测试
 * 测试 Dify/XingChen 智能体获取、数据库同步、名称过滤
 */
class SysAgentServiceTest extends BaseUnitTest {

    @Mock
    private ConfigMapper configMapper;

    @Mock
    private HttpClient httpClient;

    private SysAgentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SysAgentServiceImpl();
        ReflectionTestUtils.setField(service, "configMapper", configMapper);
        ReflectionTestUtils.setField(service, "httpClient", httpClient);
    }

    // ========== query 路由 ==========

    @Test
    void query_unknownProvider_returnsEmptyList() {
        SysAgent agent = new SysAgent();
        agent.setProvider("unknown");

        List<SysAgent> result = service.query(agent);

        assertTrue(result.isEmpty());
    }

    // ========== getDifyAgents ==========

    @Test
    void query_dify_existingLlmConfig_returnsAgentDirectly() {
        SysAgent queryAgent = new SysAgent();
        queryAgent.setProvider("dify");

        // agent 配置
        SysConfig agentConfig = new SysConfig();
        agentConfig.setConfigId(1);
        agentConfig.setConfigType("agent");
        agentConfig.setApiKey("dify-key-1");
        agentConfig.setApiUrl("http://dify.local/v1");
        agentConfig.setUserId(100);

        // 已有的 llm 配置（与 agent 配置共享 apiKey）
        SysConfig llmConfig = new SysConfig();
        llmConfig.setConfigId(2);
        llmConfig.setConfigType("llm");
        llmConfig.setApiKey("dify-key-1");
        llmConfig.setConfigName("My Dify Bot");
        llmConfig.setConfigDesc("A test bot");
        llmConfig.setProvider("dify");

        List<SysConfig> allConfigs = List.of(agentConfig, llmConfig);
        when(configMapper.query(any(SysConfig.class))).thenReturn(allConfigs);

        List<SysAgent> result = service.query(queryAgent);

        assertEquals(1, result.size());
        assertEquals("My Dify Bot", result.get(0).getAgentName());
        assertEquals(2, result.get(0).getConfigId());
        // 不应调用 API
        verifyNoInteractions(httpClient);
    }

    @Test
    void query_dify_noConfigs_returnsEmptyList() {
        SysAgent queryAgent = new SysAgent();
        queryAgent.setProvider("dify");

        when(configMapper.query(any(SysConfig.class))).thenReturn(Collections.emptyList());

        List<SysAgent> result = service.query(queryAgent);

        assertTrue(result.isEmpty());
    }

    @Test
    void query_dify_nameFilter_filtersResults() {
        SysAgent queryAgent = new SysAgent();
        queryAgent.setProvider("dify");
        queryAgent.setAgentName("test");

        SysConfig agentConfig = new SysConfig();
        agentConfig.setConfigId(1);
        agentConfig.setConfigType("agent");
        agentConfig.setApiKey("key-1");
        agentConfig.setApiUrl("http://dify.local/v1");

        SysConfig llmConfigMatch = new SysConfig();
        llmConfigMatch.setConfigId(2);
        llmConfigMatch.setConfigType("llm");
        llmConfigMatch.setApiKey("key-1");
        llmConfigMatch.setConfigName("Test Bot");
        llmConfigMatch.setProvider("dify");

        when(configMapper.query(any(SysConfig.class))).thenReturn(List.of(agentConfig, llmConfigMatch));

        List<SysAgent> result = service.query(queryAgent);

        // "Test Bot" 包含 "test"（大小写不敏感），应匹配
        assertEquals(1, result.size());
        assertEquals("Test Bot", result.get(0).getAgentName());
    }

    @Test
    void query_dify_nameFilter_excludesNonMatching() {
        SysAgent queryAgent = new SysAgent();
        queryAgent.setProvider("dify");
        queryAgent.setAgentName("xyz");

        SysConfig agentConfig = new SysConfig();
        agentConfig.setConfigId(1);
        agentConfig.setConfigType("agent");
        agentConfig.setApiKey("key-1");
        agentConfig.setApiUrl("http://dify.local/v1");

        SysConfig llmConfig = new SysConfig();
        llmConfig.setConfigId(2);
        llmConfig.setConfigType("llm");
        llmConfig.setApiKey("key-1");
        llmConfig.setConfigName("Test Bot");
        llmConfig.setProvider("dify");

        when(configMapper.query(any(SysConfig.class))).thenReturn(List.of(agentConfig, llmConfig));

        List<SysAgent> result = service.query(queryAgent);

        // "Test Bot" 不包含 "xyz"，应被过滤
        assertTrue(result.isEmpty());
    }

    // ========== getXingChenAgents ==========

    @Test
    void query_xingchen_existingLlmConfig_returnsAgent() {
        SysAgent queryAgent = new SysAgent();
        queryAgent.setProvider("xingchen");

        SysConfig agentConfig = new SysConfig();
        agentConfig.setConfigId(1);
        agentConfig.setConfigType("agent");
        agentConfig.setApiKey("xc-key-1");
        agentConfig.setApiUrl("http://xingchen.local/v1");

        SysConfig llmConfig = new SysConfig();
        llmConfig.setConfigId(3);
        llmConfig.setConfigType("llm");
        llmConfig.setApiKey("xc-key-1");
        llmConfig.setConfigName("XC Bot");
        llmConfig.setConfigDesc("XingChen bot");
        llmConfig.setProvider("xingchen");

        when(configMapper.query(any(SysConfig.class))).thenReturn(List.of(agentConfig, llmConfig));

        List<SysAgent> result = service.query(queryAgent);

        assertEquals(1, result.size());
        assertEquals("XC Bot", result.get(0).getAgentName());
        assertEquals("xingchen", result.get(0).getProvider());
    }

    // ========== getDifyAgents — 新增代理（API 调用）路径 ==========

    @SuppressWarnings("unchecked")
    @Test
    void query_dify_noLlmConfig_callsApiAndCreatesLlmConfig() throws Exception {
        SysAgent queryAgent = new SysAgent();
        queryAgent.setProvider("dify");

        // 只有 agent 配置，没有对应的 llm 配置
        SysConfig agentConfig = new SysConfig();
        agentConfig.setConfigId(1);
        agentConfig.setConfigType("agent");
        agentConfig.setApiKey("new-dify-key");
        agentConfig.setApiUrl("http://dify.local/v1");
        agentConfig.setUserId(100);

        when(configMapper.query(any(SysConfig.class))).thenReturn(List.of(agentConfig));

        // Mock info API 响应
        HttpResponse<String> infoResponse = mock(HttpResponse.class);
        when(infoResponse.statusCode()).thenReturn(200);
        when(infoResponse.body()).thenReturn("{\"name\":\"New Bot\",\"description\":\"A new bot\"}");

        // Mock meta API 响应
        HttpResponse<String> metaResponse = mock(HttpResponse.class);
        when(metaResponse.statusCode()).thenReturn(200);
        when(metaResponse.body()).thenReturn("{\"tool_icons\":{}}");

        // httpClient.send 按调用顺序返回 info 和 meta 响应
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(infoResponse, metaResponse);

        List<SysAgent> result = service.query(queryAgent);

        assertEquals(1, result.size());
        assertEquals("New Bot", result.get(0).getAgentName());
        assertEquals("A new bot", result.get(0).getAgentDesc());
        // 应创建新的 llm 配置到数据库
        ArgumentCaptor<SysConfig> captor = ArgumentCaptor.forClass(SysConfig.class);
        verify(configMapper).add(captor.capture());
        SysConfig savedConfig = captor.getValue();
        assertEquals("llm", savedConfig.getConfigType());
        assertEquals("dify", savedConfig.getProvider());
        assertEquals("new-dify-key", savedConfig.getApiKey());
        assertEquals("New Bot", savedConfig.getConfigName());
    }

    @SuppressWarnings("unchecked")
    @Test
    void query_dify_apiFailure_returnsAgentWithFallbackName() throws Exception {
        SysAgent queryAgent = new SysAgent();
        queryAgent.setProvider("dify");

        SysConfig agentConfig = new SysConfig();
        agentConfig.setConfigId(1);
        agentConfig.setConfigType("agent");
        agentConfig.setApiKey("fail-key");
        agentConfig.setApiUrl("http://dify.local/v1");
        agentConfig.setConfigName("Fallback Name");

        when(configMapper.query(any(SysConfig.class))).thenReturn(List.of(agentConfig));

        // API 调用抛出异常
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("Connection refused"));

        List<SysAgent> result = service.query(queryAgent);

        // 应返回带有 fallback 名称的 agent
        assertEquals(1, result.size());
        assertEquals("Fallback Name", result.get(0).getAgentName());
        assertEquals("无法连接到DIFY API", result.get(0).getAgentDesc());
        // 重构后：API失败时仍会创建llm配置（缓存fallback结果，避免重复请求失败API）
        verify(configMapper, times(1)).add(any());
    }
}

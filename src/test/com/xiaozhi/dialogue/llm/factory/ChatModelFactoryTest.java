package com.xiaozhi.dialogue.llm.factory;

import com.xiaozhi.base.BaseUnitTest;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.service.SysRoleService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ChatModelFactory 单元测试
 * 测试 provider 查找（大小写无关）、unknown provider 回退 OpenAI、Session→Device→Role→Config 链路
 */
class ChatModelFactoryTest extends BaseUnitTest {

    @Mock
    private SysConfigService configService;

    @Mock
    private SysRoleService roleService;

    @Mock
    private ChatModelProvider openaiProvider;

    @Mock
    private ChatModelProvider ollamaProvider;

    @Mock
    private ChatModel mockChatModel;

    private ChatModelFactory chatModelFactory;

    @BeforeEach
    void setUp() {
        // 设置 provider 名称
        when(openaiProvider.getProviderName()).thenReturn("openai");
        when(ollamaProvider.getProviderName()).thenReturn("ollama");

        // 构造工厂 - 通过构造函数注入 providers
        chatModelFactory = new ChatModelFactory(List.of(openaiProvider, ollamaProvider));
        ReflectionTestUtils.setField(chatModelFactory, "configService", configService);
        ReflectionTestUtils.setField(chatModelFactory, "roleService", roleService);
    }

    @Test
    void takeChatModel_byRole_resolvesConfig() {
        SysRole role = new SysRole();
        role.setModelId(1);

        SysConfig config = new SysConfig().setProvider("openai").setConfigId(1);
        when(configService.selectConfigById(1)).thenReturn(config);
        when(openaiProvider.createChatModel(config, role)).thenReturn(mockChatModel);

        ChatModel result = chatModelFactory.takeChatModel(role);

        assertSame(mockChatModel, result);
        verify(configService).selectConfigById(1);
        verify(openaiProvider).createChatModel(config, role);
    }

    @Test
    void takeChatModel_byRoleId_loadsRoleThenConfig() {
        SysRole role = new SysRole();
        role.setModelId(5);

        SysConfig config = new SysConfig().setProvider("ollama").setConfigId(5);
        when(roleService.selectRoleById(42)).thenReturn(role);
        when(configService.selectConfigById(5)).thenReturn(config);
        when(ollamaProvider.createChatModel(config, role)).thenReturn(mockChatModel);

        ChatModel result = chatModelFactory.takeChatModel(42);

        assertSame(mockChatModel, result);
        verify(roleService).selectRoleById(42);
    }

    @Test
    void takeChatModel_bySession_followsDeviceRoleChain() {
        SysDevice device = new SysDevice();
        device.setRoleId(10);

        ChatSession session = mock(ChatSession.class);
        when(session.getSysDevice()).thenReturn(device);

        SysRole role = new SysRole();
        role.setModelId(20);

        SysConfig config = new SysConfig().setProvider("openai").setConfigId(20);
        when(roleService.selectRoleById(10)).thenReturn(role);
        when(configService.selectConfigById(20)).thenReturn(config);
        when(openaiProvider.createChatModel(config, role)).thenReturn(mockChatModel);

        ChatModel result = chatModelFactory.takeChatModel(session);

        assertSame(mockChatModel, result);
        verify(session).getSysDevice();
        verify(roleService).selectRoleById(10);
    }

    @Test
    void takeChatModel_providerLookupCaseInsensitive() {
        SysRole role = new SysRole();
        role.setModelId(1);

        // provider 名称为大写 "OPENAI"
        SysConfig config = new SysConfig().setProvider("OPENAI").setConfigId(1);
        when(configService.selectConfigById(1)).thenReturn(config);
        when(openaiProvider.createChatModel(config, role)).thenReturn(mockChatModel);

        ChatModel result = chatModelFactory.takeChatModel(role);

        assertSame(mockChatModel, result, "Provider 查找应忽略大小写");
    }

    @Test
    void takeChatModel_unknownProvider_fallsBackToOpenAI() {
        SysRole role = new SysRole();
        role.setModelId(1);

        SysConfig config = new SysConfig().setProvider("unknown-provider").setConfigId(1);
        when(configService.selectConfigById(1)).thenReturn(config);
        when(openaiProvider.createChatModel(config, role)).thenReturn(mockChatModel);

        ChatModel result = chatModelFactory.takeChatModel(role);

        assertSame(mockChatModel, result, "未知 provider 应回退到 OpenAI");
        verify(openaiProvider).createChatModel(config, role);
    }

    @Test
    void takeChatModel_noProviderAtAll_throwsException() {
        // 创建一个没有任何 provider 的工厂
        ChatModelFactory emptyFactory = new ChatModelFactory(List.of());
        ReflectionTestUtils.setField(emptyFactory, "configService", configService);
        ReflectionTestUtils.setField(emptyFactory, "roleService", roleService);

        SysRole role = new SysRole();
        role.setModelId(1);
        SysConfig config = new SysConfig().setProvider("openai").setConfigId(1);
        when(configService.selectConfigById(1)).thenReturn(config);

        assertThrows(IllegalArgumentException.class, () -> emptyFactory.takeChatModel(role));
    }

    @Test
    void takeChatModel_nullModelId_throwsAssertionError() {
        SysRole role = new SysRole();
        role.setModelId(null);

        assertThrows(IllegalArgumentException.class, () -> chatModelFactory.takeChatModel(role),
                "modelId 为 null 应抛出异常");
    }

    @Test
    void takeVisionModel_queriesVisionType() {
        SysConfig config = new SysConfig().setProvider("openai").setConfigId(1);
        when(configService.selectModelType("vision")).thenReturn(config);
        when(openaiProvider.createChatModel(eq(config), any(SysRole.class))).thenReturn(mockChatModel);

        ChatModel result = chatModelFactory.takeVisionModel();

        assertSame(mockChatModel, result);
        verify(configService).selectModelType("vision");
    }

    @Test
    void takeIntentModel_queriesIntentType() {
        SysConfig config = new SysConfig().setProvider("openai").setConfigId(1);
        when(configService.selectModelType("intent")).thenReturn(config);
        when(openaiProvider.createChatModel(eq(config), any(SysRole.class))).thenReturn(mockChatModel);

        ChatModel result = chatModelFactory.takeIntentModel();

        assertSame(mockChatModel, result);
        verify(configService).selectModelType("intent");
    }
}

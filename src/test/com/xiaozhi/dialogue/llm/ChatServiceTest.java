package com.xiaozhi.dialogue.llm;

import com.xiaozhi.base.BaseUnitTest;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dialogue.llm.factory.ChatModelFactory;
import com.xiaozhi.dialogue.llm.memory.Conversation;
import com.xiaozhi.dialogue.llm.memory.ConversationFactory;
import com.xiaozhi.dialogue.service.*;
import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.dialogue.stt.factory.SttServiceFactory;
import com.xiaozhi.dialogue.tts.TtsService;
import com.xiaozhi.dialogue.tts.factory.TtsServiceFactory;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.mcp.McpSessionManager;
import com.xiaozhi.service.SysConfigService;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.service.SysRoleService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ChatService 单元测试
 * 测试 buildPersona 正确组装 STT + TTS + ChatModel + Player
 */
class ChatServiceTest extends BaseUnitTest {

    @Mock
    private SysConfigService configService;
    @Mock
    private ChatModelFactory chatModelFactory;
    @Mock
    private TtsServiceFactory ttsFactory;
    @Mock
    private SttServiceFactory sttFactory;
    @Mock
    private ConversationFactory conversationFactory;
    @Mock
    private SysRoleService roleService;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private MessageService messageService;
    @Mock
    private SysMessageService sysMessageService;
    @Mock
    private McpSessionManager mcpSessionManager;
    @Mock
    private GoodbyeMessageSupplier goodbyeMessages;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService();
        ReflectionTestUtils.setField(chatService, "configService", configService);
        ReflectionTestUtils.setField(chatService, "chatModelFactory", chatModelFactory);
        ReflectionTestUtils.setField(chatService, "ttsFactory", ttsFactory);
        ReflectionTestUtils.setField(chatService, "sttFactory", sttFactory);
        ReflectionTestUtils.setField(chatService, "conversationFactory", conversationFactory);
        ReflectionTestUtils.setField(chatService, "roleService", roleService);
        ReflectionTestUtils.setField(chatService, "sessionManager", sessionManager);
        ReflectionTestUtils.setField(chatService, "messageService", messageService);
        ReflectionTestUtils.setField(chatService, "sysMessageService", sysMessageService);
        ReflectionTestUtils.setField(chatService, "mcpSessionManager", mcpSessionManager);
        ReflectionTestUtils.setField(chatService, "goodbyeMessages", goodbyeMessages);
        // aecService 为 null（@Autowired(required = false)）
    }

    @Test
    void buildPersona_assemblsAllComponents() {
        ChatSession session = mock(ChatSession.class);
        when(session.getPlayer()).thenReturn(null);
        when(session.getSessionId()).thenReturn("session-1");

        SysDevice device = new SysDevice();
        device.setDeviceId("dev-1");

        SysRole role = new SysRole();
        role.setRoleId(1);
        role.setRoleName("小智");
        role.setSttId(10);
        role.setTtsId(20);
        role.setVoiceName("zh-CN-XiaoxiaoNeural");

        // STT 配置
        SysConfig sttConfig = new SysConfig();
        sttConfig.setProvider("aliyun");
        when(configService.selectConfigById(10)).thenReturn(sttConfig);
        SttService mockStt = mock(SttService.class);
        when(sttFactory.getSttService(sttConfig)).thenReturn(mockStt);

        // TTS 配置
        SysConfig ttsConfig = new SysConfig();
        ttsConfig.setProvider("edge");
        when(configService.selectConfigById(20)).thenReturn(ttsConfig);
        TtsService mockTts = mock(TtsService.class);
        when(ttsFactory.getTtsService(ttsConfig, "zh-CN-XiaoxiaoNeural", 1.0f, 1.0f)).thenReturn(mockTts);

        // Conversation
        Conversation mockConversation = mock(Conversation.class);
        when(conversationFactory.initConversation(device, role, "session-1")).thenReturn(mockConversation);

        // ChatModel
        ChatModel mockChatModel = mock(ChatModel.class);
        when(chatModelFactory.takeChatModel(session)).thenReturn(mockChatModel);

        Persona persona = chatService.buildPersona(session, device, role);

        assertNotNull(persona);
        assertSame(mockStt, persona.getSttService());
        assertSame(mockChatModel, persona.getChatModel());
        assertSame(mockConversation, persona.getConversation());
        // Player 应被创建并设置到 session
        verify(session).setPlayer(any(Player.class));
        verify(session).setPersona(persona);
    }

    @Test
    void buildPersona_nullDevice_throwsException() {
        ChatSession session = mock(ChatSession.class);
        SysRole role = new SysRole();

        assertThrows(IllegalArgumentException.class,
                () -> chatService.buildPersona(session, null, role));
    }

    @Test
    void buildPersona_nullRole_throwsException() {
        ChatSession session = mock(ChatSession.class);
        SysDevice device = new SysDevice();

        assertThrows(IllegalArgumentException.class,
                () -> chatService.buildPersona(session, device, null));
    }

    @Test
    void buildPersona_nullSttId_usesDefaultVosk() {
        ChatSession session = mock(ChatSession.class);
        when(session.getPlayer()).thenReturn(null);
        when(session.getSessionId()).thenReturn("session-2");

        SysDevice device = new SysDevice();
        device.setDeviceId("dev-2");

        SysRole role = new SysRole();
        role.setRoleId(2);
        role.setRoleName("默认角色");
        role.setSttId(null);  // 没有配置 STT
        role.setTtsId(null);  // 没有配置 TTS

        SttService defaultStt = mock(SttService.class);
        when(sttFactory.getSttService(null)).thenReturn(defaultStt);

        TtsService defaultTts = mock(TtsService.class);
        when(ttsFactory.getTtsService(null, null, 1.0f, 1.0f)).thenReturn(defaultTts);

        Conversation mockConversation = mock(Conversation.class);
        when(conversationFactory.initConversation(device, role, "session-2")).thenReturn(mockConversation);

        ChatModel mockChatModel = mock(ChatModel.class);
        when(chatModelFactory.takeChatModel(session)).thenReturn(mockChatModel);

        Persona persona = chatService.buildPersona(session, device, role);

        assertNotNull(persona);
        // STT 应使用默认 vosk（传 null 参数）
        verify(sttFactory).getSttService(null);
        assertSame(defaultStt, persona.getSttService());
    }
}

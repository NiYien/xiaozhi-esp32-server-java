package com.xiaozhi.communication.common;

import com.xiaozhi.base.BaseUnitTest;
import com.xiaozhi.communication.domain.ListenMessage;
import com.xiaozhi.dialogue.service.DialogueService;
import com.xiaozhi.dialogue.service.Player;
import com.xiaozhi.dialogue.service.VadService;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.enums.ListenMode;
import com.xiaozhi.enums.ListenState;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.service.SysRoleService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MessageHandler 单元测试
 * 测试连接建立后设备初始化、未绑定设备处理
 */
class MessageHandlerTest extends BaseUnitTest {

    @Mock
    private SysDeviceService deviceService;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private SysRoleService roleService;

    @Mock
    private VadService vadService;

    @Mock
    private DialogueService dialogueService;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private BoundDeviceInitializer boundDeviceInitializer;

    private MessageHandler messageHandler;

    @BeforeEach
    void setUp() {
        messageHandler = new MessageHandler();
        ReflectionTestUtils.setField(messageHandler, "deviceService", deviceService);
        ReflectionTestUtils.setField(messageHandler, "sessionManager", sessionManager);
        ReflectionTestUtils.setField(messageHandler, "roleService", roleService);
        ReflectionTestUtils.setField(messageHandler, "vadService", vadService);
        ReflectionTestUtils.setField(messageHandler, "dialogueService", dialogueService);
        ReflectionTestUtils.setField(messageHandler, "applicationContext", applicationContext);
        ReflectionTestUtils.setField(messageHandler, "boundDeviceInitializer", boundDeviceInitializer);
        // aecService 默认为 null（@Autowired(required = false)）
    }

    // ========== afterConnection ==========

    @Test
    void afterConnection_boundDevice_initializesPersona() {
        String deviceId = "device-001";
        String sessionId = "session-001";

        ChatSession chatSession = mock(ChatSession.class);
        when(chatSession.getSessionId()).thenReturn(sessionId);

        SysDevice device = new SysDevice();
        device.setDeviceId(deviceId);
        device.setRoleId(5);

        when(deviceService.selectDeviceById(deviceId)).thenReturn(device);

        messageHandler.afterConnection(chatSession, deviceId);

        // 验证会话注册
        verify(sessionManager).registerSession(sessionId, chatSession);
        // 验证设备初始化委托给 BoundDeviceInitializer
        verify(boundDeviceInitializer).initializeDevice(eq(chatSession), any(SysDevice.class));
    }

    @Test
    void afterConnection_unboundDevice_skipsPersonaInit() {
        String deviceId = "device-new";
        String sessionId = "session-002";

        ChatSession chatSession = mock(ChatSession.class);
        when(chatSession.getSessionId()).thenReturn(sessionId);

        // 未绑定设备没有 roleId
        SysDevice device = new SysDevice();
        device.setDeviceId(deviceId);
        device.setRoleId(null);

        when(deviceService.selectDeviceById(deviceId)).thenReturn(device);

        messageHandler.afterConnection(chatSession, deviceId);

        verify(sessionManager).registerSession(sessionId, chatSession);
        // 未绑定设备不应初始化
        verify(boundDeviceInitializer, never()).initializeDevice(any(), any());
    }

    @Test
    void afterConnection_deviceNotInDb_createsNewDevice() {
        String deviceId = "device-unknown";
        String sessionId = "session-003";

        ChatSession chatSession = mock(ChatSession.class);
        when(chatSession.getSessionId()).thenReturn(sessionId);

        // 数据库中不存在该设备
        when(deviceService.selectDeviceById(deviceId)).thenReturn(null);

        messageHandler.afterConnection(chatSession, deviceId);

        verify(sessionManager).registerSession(sessionId, chatSession);
        // 新设备不应初始化
        verify(boundDeviceInitializer, never()).initializeDevice(any(), any());
    }

    // ========== handleUnboundDevice ==========

    @Test
    void handleUnboundDevice_nullDevice_returnsFalse() {
        boolean result = messageHandler.handleUnboundDevice("session-1", null);
        assertFalse(result);
    }

    @Test
    void handleUnboundDevice_nullDeviceId_returnsFalse() {
        SysDevice device = new SysDevice();
        device.setDeviceId(null);

        boolean result = messageHandler.handleUnboundDevice("session-1", device);
        assertFalse(result);
    }

    @Test
    void handleUnboundDevice_virtualDevice_autoBindsSuccessfully() throws Exception {
        String deviceId = "user_chat_42";
        String sessionId = "session-v1";

        SysDevice device = new SysDevice();
        device.setDeviceId(deviceId);

        // 用户有一个默认角色
        SysRole defaultRole = new SysRole();
        defaultRole.setRoleId(10);
        defaultRole.setIsDefault("1");
        when(roleService.query(any(), isNull())).thenReturn(List.of(defaultRole));

        // 设备添加成功
        when(deviceService.add(any(SysDevice.class))).thenReturn(1);

        // 重新查询返回绑定后的设备
        SysDevice boundDevice = new SysDevice();
        boundDevice.setDeviceId(deviceId);
        boundDevice.setRoleId(10);
        when(deviceService.selectDeviceById(deviceId)).thenReturn(boundDevice);

        // 会话存在且打开
        ChatSession chatSession = mock(ChatSession.class);
        when(chatSession.isOpen()).thenReturn(true);
        when(sessionManager.getSession(sessionId)).thenReturn(chatSession);

        boolean result = messageHandler.handleUnboundDevice(sessionId, device);

        assertTrue(result, "虚拟设备自动绑定应返回 true");
        verify(deviceService).add(any(SysDevice.class));
    }

    // ========== handleMessage / handleListenMessage ==========

    @Test
    void handleMessage_listenStart_initializesVadSession() {
        String sessionId = "session-listen-1";

        ChatSession chatSession = mock(ChatSession.class);
        when(chatSession.getSessionId()).thenReturn(sessionId);
        when(chatSession.isInWakeupResponse()).thenReturn(false);
        when(sessionManager.getSession(sessionId)).thenReturn(chatSession);

        Player player = mock(Player.class);
        when(player.getFunctionAfterChat()).thenReturn(null);
        when(chatSession.getPlayer()).thenReturn(player);

        // 构造 ListenMessage: state=Start, mode=Auto
        ListenMessage message = new ListenMessage();
        message.setState(ListenState.Start);
        message.setMode(ListenMode.Auto);

        messageHandler.handleMessage(message, sessionId);

        // 验证 VAD 会话初始化
        verify(vadService).initSession(sessionId);
        // 验证模式被设置到 chatSession
        verify(chatSession).setMode(ListenMode.Auto);
    }

    @Test
    void handleMessage_listenStop_closesAudioStream() {
        String sessionId = "session-listen-2";

        ChatSession chatSession = mock(ChatSession.class);
        when(chatSession.getSessionId()).thenReturn(sessionId);
        when(sessionManager.getSession(sessionId)).thenReturn(chatSession);

        Player player = mock(Player.class);
        when(player.getFunctionAfterChat()).thenReturn(null);
        when(chatSession.getPlayer()).thenReturn(player);

        ListenMessage message = new ListenMessage();
        message.setState(ListenState.Stop);
        message.setMode(ListenMode.Manual);

        messageHandler.handleMessage(message, sessionId);

        // 验证停止监听时的清理操作
        verify(sessionManager).completeAudioStream(sessionId);
        verify(sessionManager).closeAudioStream(sessionId);
        verify(sessionManager).setStreamingState(sessionId, false);
        verify(vadService).resetSession(sessionId);
    }

    @Test
    void handleMessage_listenText_delegatesToDialogueService() {
        String sessionId = "session-listen-3";

        ChatSession chatSession = mock(ChatSession.class);
        when(chatSession.getSessionId()).thenReturn(sessionId);
        when(sessionManager.getSession(sessionId)).thenReturn(chatSession);

        Player player = mock(Player.class);
        when(player.getFunctionAfterChat()).thenReturn(null);
        when(chatSession.getPlayer()).thenReturn(player);

        ListenMessage message = new ListenMessage();
        message.setState(ListenState.Text);
        message.setMode(ListenMode.Auto);
        message.setText("你好小智");

        messageHandler.handleMessage(message, sessionId);

        // 验证文本消息被传递给 DialogueService
        verify(dialogueService).handleText(chatSession, "你好小智");
    }
}

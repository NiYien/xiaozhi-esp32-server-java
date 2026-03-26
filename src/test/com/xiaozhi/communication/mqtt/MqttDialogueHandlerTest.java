package com.xiaozhi.communication.mqtt;

import com.xiaozhi.base.BaseUnitTest;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.MessageHandler;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.domain.Message;
import com.xiaozhi.communication.udp.MqttUdpSession;
import com.xiaozhi.communication.udp.UdpAudioServer;
import com.xiaozhi.communication.udp.UdpProperties;
import com.xiaozhi.dialogue.service.DialogueService;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.event.ChatAbortEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MqttDialogueHandler 单元测试
 * 通过捕获 subscribe 回调间接测试 handleMessage / handleHello 等私有方法
 */
@DisplayName("MqttDialogueHandler - MQTT 对话消息处理器测试")
class MqttDialogueHandlerTest extends BaseUnitTest {

    @Mock
    private MqttService mqttService;

    @Mock
    private MqttProperties mqttProperties;

    @Mock
    private MessageHandler messageHandler;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private DialogueService dialogueService;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private UdpAudioServer udpAudioServer;

    @Mock
    private UdpProperties udpProperties;

    @InjectMocks
    private MqttDialogueHandler handler;

    @Captor
    private ArgumentCaptor<MqttService.MqttMessageListener> listenerCaptor;

    private MqttService.MqttMessageListener listener;

    private static final String TOPIC_PREFIX = "xiaozhi";
    private static final String USER_ID = "1";
    private static final String DEVICE_ID = "dev-001";
    private static final String STATUS_TOPIC = TOPIC_PREFIX + "/" + USER_ID + "/device/" + DEVICE_ID + "/status";
    private static final String COMMAND_TOPIC = TOPIC_PREFIX + "/" + USER_ID + "/device/" + DEVICE_ID + "/command";

    @BeforeEach
    void setUp() {
        when(mqttProperties.getTopicPrefix()).thenReturn(TOPIC_PREFIX);

        handler.init();

        verify(mqttService).subscribe(anyString(), eq(1), listenerCaptor.capture());
        listener = listenerCaptor.getValue();
    }

    @Test
    @DisplayName("init - 订阅设备状态 topic")
    void init_subscribesToStatusTopic() {
        // setUp 中已验证 subscribe 被调用，这里额外验证 topic 格式
        verify(mqttService).subscribe(eq(TOPIC_PREFIX + "/+/device/+/status"), eq(1), any());
    }

    @Test
    @DisplayName("handleMessage - 管理类消息（online）被忽略")
    void handleMessage_managementType_ignored() {
        String payload = "{\"type\":\"online\",\"timestamp\":1234567890}";

        listener.onMessage(STATUS_TOPIC, payload);

        verify(messageHandler, never()).handleMessage(any(Message.class), anyString());
        verify(messageHandler, never()).afterConnection(any(), anyString());
    }

    @Test
    @DisplayName("handleMessage - hello 消息创建 MqttUdpSession")
    void handleMessage_helloType_createsSession() {
        when(mqttService.buildCommandTopic(USER_ID, DEVICE_ID)).thenReturn(COMMAND_TOPIC);
        when(udpProperties.getExternalIp()).thenReturn("192.168.1.100");
        when(udpProperties.getPort()).thenReturn(8888);
        when(sessionManager.getSessionByDeviceId(DEVICE_ID)).thenReturn(null);

        String payload = "{\"type\":\"hello\"}";
        listener.onMessage(STATUS_TOPIC, payload);

        // 验证 afterConnection 被调用，参数是 MqttUdpSession
        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(messageHandler).afterConnection(sessionCaptor.capture(), eq(DEVICE_ID));
        assertInstanceOf(MqttUdpSession.class, sessionCaptor.getValue());

        // 验证 MQTT publish 被调用（hello 响应）
        ArgumentCaptor<String> publishPayloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(mqttService).publish(eq(COMMAND_TOPIC), publishPayloadCaptor.capture(), eq(1));

        // 验证响应 JSON 包含关键字段
        String responseJson = publishPayloadCaptor.getValue();
        assertTrue(responseJson.contains("\"transport\":\"udp\""), "响应应包含 transport=udp");
        assertTrue(responseJson.contains("\"session_id\""), "响应应包含 session_id");
        assertTrue(responseJson.contains("\"audio_params\""), "响应应包含 audio_params");
        assertTrue(responseJson.contains("\"key\""), "响应应包含 udp.key");
        assertTrue(responseJson.contains("\"nonce\""), "响应应包含 udp.nonce");
    }

    @Test
    @DisplayName("handleMessage - listen 消息委托给 MessageHandler")
    void handleMessage_listenType_delegatesToMessageHandler() {
        ChatSession mockSession = mock(ChatSession.class);
        when(mockSession.getSessionId()).thenReturn("session-1");
        when(sessionManager.getSessionByDeviceId(DEVICE_ID)).thenReturn(mockSession);

        String payload = "{\"type\":\"listen\",\"state\":\"start\",\"mode\":\"auto\"}";
        listener.onMessage(STATUS_TOPIC, payload);

        verify(messageHandler).handleMessage(any(Message.class), eq("session-1"));
    }

    @Test
    @DisplayName("handleMessage - goodbye 消息只调用 handleMessage，不调用 afterConnectionClosed")
    void handleMessage_goodbyeType_delegatesToMessageHandler() {
        ChatSession mockSession = mock(ChatSession.class);
        when(mockSession.getSessionId()).thenReturn("session-1");
        when(sessionManager.getSessionByDeviceId(DEVICE_ID)).thenReturn(mockSession);

        String payload = "{\"type\":\"goodbye\"}";
        listener.onMessage(STATUS_TOPIC, payload);

        verify(messageHandler).handleMessage(any(Message.class), eq("session-1"));
        verify(messageHandler, never()).afterConnectionClosed(anyString());
    }

    @Test
    @DisplayName("handleMessage - topic 格式不足 5 段不抛异常")
    void handleMessage_invalidTopic_logsWarning() {
        String invalidTopic = "xiaozhi/device/status";
        String payload = "{\"type\":\"listen\"}";

        assertDoesNotThrow(() -> listener.onMessage(invalidTopic, payload));
        verify(messageHandler, never()).handleMessage(any(Message.class), anyString());
    }

    @Test
    @DisplayName("handleMessage - 无活跃 session 时忽略非 hello 消息")
    void handleMessage_noActiveSession_ignoresNonHello() {
        when(sessionManager.getSessionByDeviceId(DEVICE_ID)).thenReturn(null);

        String payload = "{\"type\":\"listen\",\"state\":\"start\"}";

        assertDoesNotThrow(() -> listener.onMessage(STATUS_TOPIC, payload));
        verify(messageHandler, never()).handleMessage(any(Message.class), anyString());
    }

    @Test
    @DisplayName("handleHello - 设备已有旧 session 时先中止再关闭")
    void handleHello_existingSession_abortsAndCloses() {
        // 模拟已有旧 session
        ChatSession oldSession = mock(ChatSession.class);
        lenient().when(oldSession.getSessionId()).thenReturn("old-session");
        when(sessionManager.getSessionByDeviceId(DEVICE_ID)).thenReturn(oldSession);

        when(mqttService.buildCommandTopic(USER_ID, DEVICE_ID)).thenReturn(COMMAND_TOPIC);
        when(udpProperties.getExternalIp()).thenReturn("10.0.0.1");
        when(udpProperties.getPort()).thenReturn(9999);

        String payload = "{\"type\":\"hello\"}";
        listener.onMessage(STATUS_TOPIC, payload);

        // 验证发布了 ChatAbortEvent
        verify(applicationContext).publishEvent(any(ChatAbortEvent.class));
        // 验证关闭了旧 session
        verify(sessionManager).closeSession(oldSession);
        // 验证仍然创建了新 session
        verify(messageHandler).afterConnection(any(MqttUdpSession.class), eq(DEVICE_ID));
    }
}

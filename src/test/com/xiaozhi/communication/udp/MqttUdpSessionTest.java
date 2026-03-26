package com.xiaozhi.communication.udp;

import com.xiaozhi.base.BaseUnitTest;
import com.xiaozhi.communication.mqtt.MqttService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MqttUdpSession 单元测试
 * 验证 MQTT 文本消息发送、UDP 加密音频发送、会话状态查询和关闭逻辑
 */
@DisplayName("MqttUdpSession - MQTT+UDP 混合会话测试")
class MqttUdpSessionTest extends BaseUnitTest {

    @Mock
    private MqttService mqttService;

    @Mock
    private UdpAudioServer udpAudioServer;

    @Mock
    private UdpSessionContext udpContext;

    private MqttUdpSession session;

    private static final String SESSION_ID = "test-session-123";
    private static final String RESPONSE_TOPIC = "xiaozhi/1/device/dev-001/command";
    private static final int SSRC = 0x12345678;

    @BeforeEach
    void setUp() {
        lenient().when(udpContext.getSsrc()).thenReturn(SSRC);
        session = new MqttUdpSession(SESSION_ID, mqttService, udpAudioServer, udpContext, RESPONSE_TOPIC);
    }

    @Test
    @DisplayName("sendTextMessage - 通过 MQTT 发布到 command topic")
    void sendTextMessage_publishesToCommandTopic() {
        String message = "{\"type\":\"tts\",\"state\":\"start\"}";

        session.sendTextMessage(message);

        verify(mqttService).publish(RESPONSE_TOPIC, message, 1);
    }

    @Test
    @DisplayName("sendBinaryMessage - 通过 UDP 发送加密音频")
    void sendBinaryMessage_sendsEncryptedViaUdp() {
        byte[] data = new byte[]{0x01, 0x02, 0x03, 0x04};

        session.sendBinaryMessage(data);

        verify(udpAudioServer).sendEncrypted(udpContext, data);
    }

    @Test
    @DisplayName("isOpen - MQTT 已连接且 UDP 活跃时返回 true")
    void isOpen_bothActive_returnsTrue() {
        when(mqttService.isConnected()).thenReturn(true);
        when(udpContext.isActive()).thenReturn(true);

        assertTrue(session.isOpen());
    }

    @Test
    @DisplayName("isOpen - MQTT 断开时返回 false")
    void isOpen_mqttDisconnected_returnsFalse() {
        when(mqttService.isConnected()).thenReturn(false);
        // udpContext.isActive() 不会被调用（&& 短路），不需要 stub

        assertFalse(session.isOpen());
    }

    @Test
    @DisplayName("isOpen - UDP 非活跃时返回 false")
    void isOpen_udpInactive_returnsFalse() {
        when(mqttService.isConnected()).thenReturn(true);
        when(udpContext.isActive()).thenReturn(false);

        assertFalse(session.isOpen());
    }

    @Test
    @DisplayName("close - 停用 UDP 上下文并注销会话")
    void close_deactivatesAndUnregisters() {
        session.close();

        verify(udpContext).deactivate();
        verify(udpAudioServer).unregisterSession(SSRC);
    }

    @Test
    @DisplayName("getSessionId - 返回构造函数传入的值")
    void getSessionId_returnsConstructorValue() {
        assertEquals(SESSION_ID, session.getSessionId());
    }
}

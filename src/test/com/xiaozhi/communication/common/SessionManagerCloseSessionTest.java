package com.xiaozhi.communication.common;

import com.xiaozhi.base.BaseUnitTest;
import com.xiaozhi.communication.server.websocket.WebSocketSession;
import com.xiaozhi.communication.udp.MqttUdpSession;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.event.ChatSessionCloseEvent;
import com.xiaozhi.service.SysDeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SessionManager.closeSession(ChatSession) 回归测试
 * 验证不同 ChatSession 子类型（普通 mock、WebSocketSession、MqttUdpSession）
 * 都能执行完整的清理流程：remove + close + publishEvent + clearAudioSinks
 */
@DisplayName("SessionManager.closeSession - instanceof 修复回归测试")
class SessionManagerCloseSessionTest extends BaseUnitTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private SysDeviceService deviceService;

    private SessionManager sessionManager;

    private static final String SESSION_ID = "close-test-session";
    private static final String DEVICE_ID = "close-test-device";

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
        ReflectionTestUtils.setField(sessionManager, "applicationContext", applicationContext);
        ReflectionTestUtils.setField(sessionManager, "deviceService", deviceService);
        ReflectionTestUtils.setField(sessionManager, "checkInactiveSession", false);
        ReflectionTestUtils.setField(sessionManager, "inactiveTimeOutSeconds", 60);
    }

    private ChatSession createMockSession(String sessionId, String deviceId) {
        ChatSession session = mock(ChatSession.class);
        lenient().when(session.getSessionId()).thenReturn(sessionId);
        if (deviceId != null) {
            SysDevice device = new SysDevice();
            device.setDeviceId(deviceId);
            lenient().when(session.getSysDevice()).thenReturn(device);
        }
        return session;
    }

    @Test
    @DisplayName("closeSession - 普通 ChatSession mock 执行完整清理")
    void closeSession_anySessionType_executesFullCleanup() {
        ChatSession session = createMockSession(SESSION_ID, DEVICE_ID);
        sessionManager.registerSession(SESSION_ID, session);

        sessionManager.closeSession(session);

        // 验证 session 已从 registry 中移除
        assertNull(sessionManager.getSession(SESSION_ID));
        // 验证 close 被调用
        verify(session).close();
        // 验证发布了 ChatSessionCloseEvent
        verify(applicationContext).publishEvent(any(ChatSessionCloseEvent.class));
        // 验证 clearAudioSinks 被调用
        verify(session).clearAudioSinks();
    }

    @Test
    @DisplayName("closeSession - WebSocketSession mock 执行完整清理（回归测试）")
    void closeSession_webSocketSession_executesFullCleanup() {
        WebSocketSession session = mock(WebSocketSession.class);
        lenient().when(session.getSessionId()).thenReturn(SESSION_ID);
        SysDevice device = new SysDevice();
        device.setDeviceId(DEVICE_ID);
        lenient().when(session.getSysDevice()).thenReturn(device);

        sessionManager.registerSession(SESSION_ID, session);

        sessionManager.closeSession(session);

        assertNull(sessionManager.getSession(SESSION_ID));
        verify(session).close();
        verify(applicationContext).publishEvent(any(ChatSessionCloseEvent.class));
        verify(session).clearAudioSinks();
    }

    @Test
    @DisplayName("closeSession - MqttUdpSession mock 执行完整清理")
    void closeSession_mqttUdpSession_executesFullCleanup() {
        MqttUdpSession session = mock(MqttUdpSession.class);
        lenient().when(session.getSessionId()).thenReturn(SESSION_ID);
        SysDevice device = new SysDevice();
        device.setDeviceId(DEVICE_ID);
        lenient().when(session.getSysDevice()).thenReturn(device);

        sessionManager.registerSession(SESSION_ID, session);

        sessionManager.closeSession(session);

        assertNull(sessionManager.getSession(SESSION_ID));
        verify(session).close();
        verify(applicationContext).publishEvent(any(ChatSessionCloseEvent.class));
        verify(session).clearAudioSinks();
    }

    @Test
    @DisplayName("closeSession - null 输入不抛异常")
    void closeSession_null_doesNotThrow() {
        assertDoesNotThrow(() -> sessionManager.closeSession((ChatSession) null));
    }
}

package com.xiaozhi.communication.common;

import com.xiaozhi.base.BaseUnitTest;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.event.ChatSessionOpenEvent;
import com.xiaozhi.event.DeviceOnlineEvent;
import com.xiaozhi.service.SysDeviceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Sinks;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SessionManager 单元测试
 * 测试会话注册/移除/关闭、设备查找、音频流管理、验证码幂等性、活动时间更新
 */
class SessionManagerTest extends BaseUnitTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private SysDeviceService deviceService;

    private SessionManager sessionManager;

    private static final String SESSION_ID = "test-session-1";
    private static final String DEVICE_ID = "device-001";

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
        ReflectionTestUtils.setField(sessionManager, "applicationContext", applicationContext);
        ReflectionTestUtils.setField(sessionManager, "deviceService", deviceService);
        ReflectionTestUtils.setField(sessionManager, "checkInactiveSession", false);
        ReflectionTestUtils.setField(sessionManager, "inactiveTimeOutSeconds", 60);
    }

    // ---- 辅助方法 ----

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

    // ---- 测试用例 ----

    @Test
    void registerSession_storesSessionAndPublishesEvent() {
        ChatSession session = createMockSession(SESSION_ID, DEVICE_ID);

        sessionManager.registerSession(SESSION_ID, session);

        assertSame(session, sessionManager.getSession(SESSION_ID));
        verify(applicationContext).publishEvent(any(ChatSessionOpenEvent.class));
    }

    @Test
    void removeSession_removesFromStore() {
        ChatSession session = createMockSession(SESSION_ID, DEVICE_ID);
        sessionManager.registerSession(SESSION_ID, session);

        sessionManager.removeSession(SESSION_ID);

        assertNull(sessionManager.getSession(SESSION_ID));
    }

    @Test
    void closeSession_bySessionId_closesExistingSession() {
        // closeSession(String) 查找并调用 closeSession(ChatSession)
        // 对于非 WebSocketSession 的 ChatSession，只 clearAudioSinks
        ChatSession session = createMockSession(SESSION_ID, DEVICE_ID);
        sessionManager.registerSession(SESSION_ID, session);

        sessionManager.closeSession(SESSION_ID);

        verify(session).clearAudioSinks();
    }

    @Test
    void closeSession_nullSession_doesNotThrow() {
        assertDoesNotThrow(() -> sessionManager.closeSession((ChatSession) null));
    }

    @Test
    void getSessionByDeviceId_findsMatchingSession() {
        ChatSession session = createMockSession(SESSION_ID, DEVICE_ID);
        sessionManager.registerSession(SESSION_ID, session);

        ChatSession found = sessionManager.getSessionByDeviceId(DEVICE_ID);

        assertSame(session, found);
    }

    @Test
    void getSessionByDeviceId_noMatch_returnsNull() {
        ChatSession session = createMockSession(SESSION_ID, DEVICE_ID);
        sessionManager.registerSession(SESSION_ID, session);

        assertNull(sessionManager.getSessionByDeviceId("nonexistent-device"));
    }

    @Test
    void createAudioStream_thenSendAndComplete() {
        ChatSession session = createMockSession(SESSION_ID, DEVICE_ID);
        sessionManager.registerSession(SESSION_ID, session);

        // createAudioStream 会创建一个 Sinks.Many 并设置到 session
        sessionManager.createAudioStream(SESSION_ID);

        // 验证 setAudioSinks 被调用（传入非 null 的 Sinks）
        ArgumentCaptor<Sinks.Many> captor = ArgumentCaptor.forClass(Sinks.Many.class);
        verify(session).setAudioSinks(captor.capture());
        assertNotNull(captor.getValue());
    }

    @Test
    void sendAudioData_withActiveSink_emitsData() {
        ChatSession session = createMockSession(SESSION_ID, DEVICE_ID);
        Sinks.Many<byte[]> sink = Sinks.many().multicast().onBackpressureBuffer();
        when(session.getAudioSinks()).thenReturn(sink);
        sessionManager.registerSession(SESSION_ID, session);

        byte[] data = new byte[]{1, 2, 3};
        sessionManager.sendAudioData(SESSION_ID, data);

        // 验证 sink 收到了数据
        sink.asFlux().take(1).subscribe(received -> assertArrayEquals(data, received));
    }

    @Test
    void completeAudioStream_completesTheSink() {
        ChatSession session = createMockSession(SESSION_ID, DEVICE_ID);
        Sinks.Many<byte[]> sink = Sinks.many().multicast().onBackpressureBuffer();
        when(session.getAudioSinks()).thenReturn(sink);
        sessionManager.registerSession(SESSION_ID, session);

        sessionManager.completeAudioStream(SESSION_ID);

        // 完成后再发送应该失败
        Sinks.EmitResult result = sink.tryEmitNext(new byte[]{1});
        assertNotEquals(Sinks.EmitResult.OK, result);
    }

    @Test
    void markCaptchaGeneration_firstCall_returnsTrue() {
        assertTrue(sessionManager.markCaptchaGeneration(DEVICE_ID));
    }

    @Test
    void markCaptchaGeneration_duplicateCall_returnsFalse() {
        sessionManager.markCaptchaGeneration(DEVICE_ID);

        assertFalse(sessionManager.markCaptchaGeneration(DEVICE_ID));
    }

    @Test
    void markCaptchaGeneration_afterUnmark_returnsTrue() {
        sessionManager.markCaptchaGeneration(DEVICE_ID);
        sessionManager.unmarkCaptchaGeneration(DEVICE_ID);

        assertTrue(sessionManager.markCaptchaGeneration(DEVICE_ID));
    }

    @Test
    void updateLastActivity_updatesSessionTimestamp() {
        ChatSession session = createMockSession(SESSION_ID, DEVICE_ID);
        sessionManager.registerSession(SESSION_ID, session);

        sessionManager.updateLastActivity(SESSION_ID);

        verify(session).setLastActivityTime(any(Instant.class));
    }

    @Test
    void registerDevice_setsDeviceAndPublishesEvent() {
        ChatSession session = createMockSession(SESSION_ID, null);
        sessionManager.registerSession(SESSION_ID, session);

        SysDevice device = new SysDevice();
        device.setDeviceId(DEVICE_ID);
        sessionManager.registerDevice(SESSION_ID, device);

        verify(session).setSysDevice(device);
        verify(applicationContext).publishEvent(any(DeviceOnlineEvent.class));
    }

    @Test
    void getDeviceConfig_existingSession_returnsDevice() {
        ChatSession session = createMockSession(SESSION_ID, DEVICE_ID);
        sessionManager.registerSession(SESSION_ID, session);

        SysDevice result = sessionManager.getDeviceConfig(SESSION_ID);

        assertNotNull(result);
        assertEquals(DEVICE_ID, result.getDeviceId());
    }

    @Test
    void getDeviceConfig_noSession_returnsNull() {
        assertNull(sessionManager.getDeviceConfig("nonexistent"));
    }
}

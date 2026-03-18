package com.xiaozhi.dialogue.service;

import com.xiaozhi.base.BaseUnitTest;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dialogue.vad.VadModel.InferenceResult;
import com.xiaozhi.dialogue.vad.impl.SileroVadModel;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysRoleService;
import com.xiaozhi.utils.OpusProcessor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * VadService 单元测试
 * 测试 VAD 状态机：initSession、语音/静音检测、preBuffer、GRU 重置等
 */
class VadServiceTest extends BaseUnitTest {

    @Mock
    private SileroVadModel vadModel;

    @Mock
    private SysRoleService roleService;

    @Mock
    private SessionManager sessionManager;

    @InjectMocks
    private VadService vadService;

    private static final String SESSION_ID = "test-session-1";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(vadService, "preBufferMs", 500);
        ReflectionTestUtils.setField(vadService, "tailKeepMs", 300);
    }

    // ---- 辅助方法 ----

    /**
     * 创建假 PCM 数据（16bit LE mono），samples 个采样点
     */
    private byte[] fakePcm(int samples, short amplitude) {
        byte[] pcm = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            pcm[i * 2] = (byte) (amplitude & 0xFF);
            pcm[i * 2 + 1] = (byte) ((amplitude >> 8) & 0xFF);
        }
        return pcm;
    }

    /**
     * 模拟 processAudio：mock OpusProcessor 返回指定 PCM，mock SileroVadModel 返回指定概率
     */
    private VadService.VadResult processWithProb(float probability, byte[] pcm) {
        // 配置设备和角色（使用默认阈值）
        SysDevice device = new SysDevice();
        device.setRoleId(1);
        when(sessionManager.getDeviceConfig(SESSION_ID)).thenReturn(device);

        SysRole role = new SysRole();
        when(roleService.selectRoleById(1)).thenReturn(role);

        // mock SileroVadModel 返回指定概率
        when(vadModel.infer(any(float[].class), any(float[][][].class)))
                .thenReturn(new InferenceResult(probability, new float[2][1][128]));

        try (MockedConstruction<OpusProcessor> mocked = mockConstruction(OpusProcessor.class,
                (mock, context) -> when(mock.opusToPcm(any(byte[].class))).thenReturn(pcm))) {
            return vadService.processAudio(SESSION_ID, new byte[]{1, 2, 3});
        }
    }

    // ---- 测试用例 ----

    @Test
    void initSession_createsVadState() {
        vadService.initSession(SESSION_ID);

        assertTrue(vadService.isSessionInitialized(SESSION_ID));
    }

    @Test
    void initSession_existingSession_resetsState() {
        vadService.initSession(SESSION_ID);
        vadService.initSession(SESSION_ID);

        assertTrue(vadService.isSessionInitialized(SESSION_ID));
    }

    @Test
    void processAudio_uninitializedSession_returnsNull() {
        VadService.VadResult result = vadService.processAudio("nonexistent", new byte[]{1});
        assertNull(result);
    }

    @Test
    void processAudio_silenceFrames_returnNoSpeech() {
        vadService.initSession(SESSION_ID);

        // 低概率 + 低能量 → NO_SPEECH
        byte[] silentPcm = fakePcm(960, (short) 0);
        VadService.VadResult result = processWithProb(0.1f, silentPcm);

        assertEquals(VadService.VadStatus.NO_SPEECH, result.getStatus());
    }

    @Test
    void processAudio_singleSpeechFrame_doesNotTriggerStart() {
        vadService.initSession(SESSION_ID);

        // 第一帧高概率 → 不应触发 SPEECH_START（需要连续2帧）
        byte[] loudPcm = fakePcm(960, (short) 10000);
        VadService.VadResult result = processWithProb(0.9f, loudPcm);

        // 单帧语音，consecutiveSpeechFrames=1 < 2，不触发 SPEECH_START
        assertEquals(VadService.VadStatus.NO_SPEECH, result.getStatus());
    }

    @Test
    void processAudio_twoConsecutiveSpeechFrames_triggersSpeechStart() {
        vadService.initSession(SESSION_ID);

        byte[] loudPcm = fakePcm(960, (short) 10000);

        // 第一帧：语音概率高但不满足连续2帧
        processWithProb(0.9f, loudPcm);

        // 第二帧：连续第2帧语音 → SPEECH_START
        VadService.VadResult result = processWithProb(0.9f, loudPcm);

        assertEquals(VadService.VadStatus.SPEECH_START, result.getStatus());
        assertNotNull(result.getProcessedData());
    }

    @Test
    void processAudio_continuousSpeech_returnsSpeechContinue() {
        vadService.initSession(SESSION_ID);

        byte[] loudPcm = fakePcm(960, (short) 10000);

        // 触发 SPEECH_START（连续2帧）
        processWithProb(0.9f, loudPcm);
        processWithProb(0.9f, loudPcm);

        // 继续语音 → SPEECH_CONTINUE
        VadService.VadResult result = processWithProb(0.9f, loudPcm);

        assertEquals(VadService.VadStatus.SPEECH_CONTINUE, result.getStatus());
    }

    @Test
    void processAudio_silenceAfterSpeech_eventuallyReturnsSpeechEnd() {
        vadService.initSession(SESSION_ID);

        byte[] loudPcm = fakePcm(960, (short) 10000);
        byte[] silentPcm = fakePcm(960, (short) 0);

        // 触发 SPEECH_START
        processWithProb(0.9f, loudPcm);
        processWithProb(0.9f, loudPcm);

        // 发送足够多的静音帧以超过默认 silenceTimeoutMs=800ms
        // 每帧 960 samples / 16000Hz = 60ms，需要至少 800/60 ≈ 14帧
        // 但实际超时是基于 System.currentTimeMillis，因此需要模拟时间流逝
        // 使用静音帧使 state.setSpeaking(false) → silenceTime 被设定

        // 先发几帧静音让 silenceTime 被设定
        VadService.VadResult result = null;
        for (int i = 0; i < 5; i++) {
            result = processWithProb(0.1f, silentPcm);
        }

        // 此时应该是 SPEECH_CONTINUE（静音时间还不够长）
        assertEquals(VadService.VadStatus.SPEECH_CONTINUE, result.getStatus());

        // 等待超过静音超时后再发送静音帧
        try {
            Thread.sleep(900);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        result = processWithProb(0.1f, silentPcm);
        assertEquals(VadService.VadStatus.SPEECH_END, result.getStatus());
    }

    @Test
    void processAudio_30SilenceFrames_resetsGruState() {
        vadService.initSession(SESSION_ID);

        byte[] silentPcm = fakePcm(960, (short) 0);

        // 发送30帧静音，触发GRU状态重置
        for (int i = 0; i < 30; i++) {
            processWithProb(0.1f, silentPcm);
        }

        // 验证 SileroVadModel.infer 被调用了（每帧至少1次），说明处理正常
        verify(vadModel, atLeast(30)).infer(any(float[].class), any(float[][][].class));

        // 重置后应该还能正常工作
        VadService.VadResult result = processWithProb(0.1f, silentPcm);
        assertEquals(VadService.VadStatus.NO_SPEECH, result.getStatus());
    }

    @Test
    void resetVadModelState_clearsGruState() {
        vadService.initSession(SESSION_ID);

        // resetVadModelState 不应抛出异常
        assertDoesNotThrow(() -> vadService.resetVadModelState(SESSION_ID));
    }

    @Test
    void resetVadModelState_uninitializedSession_noError() {
        assertDoesNotThrow(() -> vadService.resetVadModelState("nonexistent"));
    }

    @Test
    void getPcmData_afterSpeechStart_containsData() {
        vadService.initSession(SESSION_ID);

        byte[] loudPcm = fakePcm(960, (short) 10000);

        // 触发 SPEECH_START
        processWithProb(0.9f, loudPcm);
        processWithProb(0.9f, loudPcm);

        // 继续语音积累 PCM
        processWithProb(0.9f, loudPcm);

        List<byte[]> pcmData = vadService.getPcmData(SESSION_ID);
        assertNotNull(pcmData);
        assertFalse(pcmData.isEmpty());
    }

    @Test
    void getPcmData_uninitializedSession_returnsEmpty() {
        List<byte[]> pcmData = vadService.getPcmData("nonexistent");
        assertNotNull(pcmData);
        assertTrue(pcmData.isEmpty());
    }

    @Test
    void resetSession_removesState() {
        vadService.initSession(SESSION_ID);
        assertTrue(vadService.isSessionInitialized(SESSION_ID));

        vadService.resetSession(SESSION_ID);
        assertFalse(vadService.isSessionInitialized(SESSION_ID));
    }
}

package com.xiaozhi.dialogue.service;

import com.xiaozhi.base.BaseUnitTest;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.llm.intent.IntentDetector;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.service.SysRoleService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Mockito.*;

/**
 * DialogueService 单元测试
 * 测试 processAudioData 守卫条件、VAD 状态处理、abortDialogue、handleText
 */
class DialogueServiceTest extends BaseUnitTest {

    @Mock
    private ChatService chatService;

    @Mock
    private MessageService messageService;

    @Mock
    private VadService vadService;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private SysRoleService roleService;

    @Mock
    private IntentDetector intentDetector;

    @Mock
    private SysDeviceService sysDeviceService;

    @InjectMocks
    private DialogueService dialogueService;

    @Mock
    private ChatSession session;

    @Mock
    private Persona persona;

    @Mock
    private Synthesizer synthesizer;

    @Mock
    private Player player;

    private static final String SESSION_ID = "test-session-1";

    @BeforeEach
    void setUp() {
        lenient().when(session.getSessionId()).thenReturn(SESSION_ID);
        lenient().when(session.getPersona()).thenReturn(persona);
        lenient().when(session.getPlayer()).thenReturn(player);
        lenient().when(persona.getSynthesizer()).thenReturn(synthesizer);
    }

    // ---- processAudioData 守卫条件 ----

    @Test
    void processAudioData_nullSession_doesNothing() {
        dialogueService.processAudioData(null, new byte[]{1});
        verifyNoInteractions(vadService);
    }

    @Test
    void processAudioData_nullOpusData_doesNothing() {
        dialogueService.processAudioData(session, null);
        verifyNoInteractions(vadService);
    }

    @Test
    void processAudioData_emptyOpusData_doesNothing() {
        dialogueService.processAudioData(session, new byte[0]);
        verifyNoInteractions(vadService);
    }

    @Test
    void processAudioData_inWakeupResponse_skips() {
        when(session.isInWakeupResponse()).thenReturn(true);

        dialogueService.processAudioData(session, new byte[]{1});

        verifyNoInteractions(vadService);
    }

    @Test
    void processAudioData_playerHasFunctionAfterChat_skips() {
        when(session.isInWakeupResponse()).thenReturn(false);
        when(player.getFunctionAfterChat()).thenReturn(() -> {});

        dialogueService.processAudioData(session, new byte[]{1});

        verifyNoInteractions(vadService);
    }

    @Test
    void processAudioData_noDevice_skips() {
        when(session.isInWakeupResponse()).thenReturn(false);
        when(player.getFunctionAfterChat()).thenReturn(null);
        when(session.getSysDevice()).thenReturn(null);

        dialogueService.processAudioData(session, new byte[]{1});

        verifyNoInteractions(vadService);
    }

    @Test
    void processAudioData_speechStart_createsStreamAndStartsStt() {
        SysDevice device = new SysDevice();
        device.setRoleId(1);
        when(session.isInWakeupResponse()).thenReturn(false);
        when(player.getFunctionAfterChat()).thenReturn(null);
        when(session.getSysDevice()).thenReturn(device);

        VadService.VadResult vadResult = new VadService.VadResult(
                VadService.VadStatus.SPEECH_START, new byte[]{1, 2});
        when(vadService.processAudio(SESSION_ID, new byte[]{1})).thenReturn(vadResult);
        when(persona.isActive()).thenReturn(false);

        dialogueService.processAudioData(session, new byte[]{1});

        // 验证创建了音频流
        verify(sessionManager).closeAudioStream(SESSION_ID);
        verify(sessionManager).createAudioStream(SESSION_ID);
        verify(sessionManager).setStreamingState(SESSION_ID, true);
        verify(sessionManager).updateLastActivity(SESSION_ID);
    }

    @Test
    void processAudioData_speechStart_withActiveTts_abortsWithVadReason() {
        SysDevice device = new SysDevice();
        device.setRoleId(1);
        when(session.isInWakeupResponse()).thenReturn(false);
        when(player.getFunctionAfterChat()).thenReturn(null);
        when(session.getSysDevice()).thenReturn(device);

        VadService.VadResult vadResult = new VadService.VadResult(
                VadService.VadStatus.SPEECH_START, new byte[]{1, 2});
        when(vadService.processAudio(SESSION_ID, new byte[]{1})).thenReturn(vadResult);
        when(persona.isActive()).thenReturn(true);

        dialogueService.processAudioData(session, new byte[]{1});

        // 当 persona 活跃时，应该中止当前对话
        // abort 会调用 synthesizer.cancel() + player.stop() + sendTtsMessage
        verify(synthesizer).cancel();
        verify(player).stop();
        verify(messageService).sendTtsMessage(session, null, "stop");
    }

    @Test
    void processAudioData_speechContinue_sendsAudioData() {
        SysDevice device = new SysDevice();
        device.setRoleId(1);
        when(session.isInWakeupResponse()).thenReturn(false);
        when(player.getFunctionAfterChat()).thenReturn(null);
        when(session.getSysDevice()).thenReturn(device);
        when(sessionManager.isStreaming(SESSION_ID)).thenReturn(true);

        byte[] audioData = new byte[]{1, 2, 3};
        VadService.VadResult vadResult = new VadService.VadResult(
                VadService.VadStatus.SPEECH_CONTINUE, audioData);
        when(vadService.processAudio(SESSION_ID, new byte[]{1})).thenReturn(vadResult);

        dialogueService.processAudioData(session, new byte[]{1});

        verify(sessionManager).sendAudioData(SESSION_ID, audioData);
    }

    @Test
    void processAudioData_speechEnd_completesStream() {
        SysDevice device = new SysDevice();
        device.setRoleId(1);
        when(session.isInWakeupResponse()).thenReturn(false);
        when(player.getFunctionAfterChat()).thenReturn(null);
        when(session.getSysDevice()).thenReturn(device);
        when(sessionManager.isStreaming(SESSION_ID)).thenReturn(true);

        VadService.VadResult vadResult = new VadService.VadResult(
                VadService.VadStatus.SPEECH_END, new byte[]{1});
        when(vadService.processAudio(SESSION_ID, new byte[]{1})).thenReturn(vadResult);

        dialogueService.processAudioData(session, new byte[]{1});

        verify(sessionManager).completeAudioStream(SESSION_ID);
        verify(sessionManager).setStreamingState(SESSION_ID, false);
    }

    // ---- abortDialogue ----

    @Test
    void abortDialogue_normalReason_closesAudioStream() {
        dialogueService.abortDialogue(session, "user-interrupt");

        verify(sessionManager).closeAudioStream(SESSION_ID);
        verify(sessionManager).setStreamingState(SESSION_ID, false);
        verify(synthesizer).cancel();
        verify(player).stop();
        verify(messageService).sendTtsMessage(session, null, "stop");
    }

    @Test
    void abortDialogue_vadReason_doesNotCloseAudioStream() {
        dialogueService.abortDialogue(session, "检测到vad");

        // "检测到vad" 场景：不关闭音频流（因为 startStt 已创建新的）
        verify(sessionManager, never()).closeAudioStream(SESSION_ID);
        verify(sessionManager, never()).setStreamingState(eq(SESSION_ID), eq(false));

        // 但仍然取消合成和播放
        verify(synthesizer).cancel();
        verify(player).stop();
    }

    @Test
    void abortDialogue_withFunctionAfterChat_executesAndClearsCallback() {
        Runnable callback = mock(Runnable.class);
        when(player.getFunctionAfterChat()).thenReturn(callback);

        dialogueService.abortDialogue(session, "test");

        verify(callback).run();
        verify(player).setFunctionAfterChat(null);
    }

    // ---- handleText ----

    @Test
    void handleText_exitIntent_sendsGoodbye() {
        SysDevice device = new SysDevice();
        when(sessionManager.getDeviceConfig(SESSION_ID)).thenReturn(device);
        when(intentDetector.detectIntent("再见")).thenReturn(new IntentDetector.ExitIntent());

        dialogueService.handleText(session, "再见");

        // 退出意图 → sendGoodbyeMessage
        verify(persona).sendGoodbyeMessage();
    }

    @Test
    void handleText_normalText_callsPersonaChat() {
        SysDevice device = new SysDevice();
        when(sessionManager.getDeviceConfig(SESSION_ID)).thenReturn(device);
        when(intentDetector.detectIntent("你好")).thenReturn(null);

        dialogueService.handleText(session, "你好");

        verify(persona).chat("你好");
    }

    @Test
    void handleText_noDevice_skips() {
        when(sessionManager.getDeviceConfig(SESSION_ID)).thenReturn(null);

        dialogueService.handleText(session, "你好");

        verifyNoInteractions(intentDetector);
    }
}

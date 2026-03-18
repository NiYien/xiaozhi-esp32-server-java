package com.xiaozhi.dialogue.llm;

import com.xiaozhi.base.BaseUnitTest;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.memory.Conversation;
import com.xiaozhi.dialogue.service.GoodbyeMessageSupplier;
import com.xiaozhi.dialogue.service.Persona;
import com.xiaozhi.dialogue.service.Player;
import com.xiaozhi.dialogue.service.Synthesizer;
import com.xiaozhi.dialogue.stt.SttService;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysMessageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Persona 单元测试
 * 测试 chat 流程、isActive、wakeUp、sendGoodbyeMessage
 */
class PersonaTest extends BaseUnitTest {

    @Mock
    private ChatSession session;

    @Mock
    private SttService sttService;

    @Mock
    private ChatModel chatModel;

    @Mock
    private GoodbyeMessageSupplier goodbyeMessages;

    @Mock
    private Synthesizer synthesizer;

    @Mock
    private Player player;

    @Mock
    private Conversation conversation;

    @Mock
    private SysMessageService messageService;

    private Persona persona;

    @BeforeEach
    void setUp() {
        lenient().when(session.getToolCallbacks()).thenReturn(new ArrayList<>());
        lenient().when(session.drainToolCallDetails()).thenReturn(new ArrayList<>());

        persona = Persona.builder()
                .session(session)
                .sttService(sttService)
                .chatModel(chatModel)
                .goodbyeMessages(goodbyeMessages)
                .synthesizer(synthesizer)
                .player(player)
                .conversation(conversation)
                .messageService(messageService)
                .build();

        // 让 session.getPersona() 返回当前 persona
        lenient().when(session.getPersona()).thenReturn(persona);
    }

    // ---- 辅助方法 ----

    /**
     * 创建一个简单的 mock ChatResponse 流
     */
    private Flux<ChatResponse> createChatResponseFlux(String text) {
        AssistantMessage assistantMessage = new AssistantMessage(text);
        ChatGenerationMetadata metadata = mock(ChatGenerationMetadata.class);
        lenient().when(metadata.getFinishReason()).thenReturn("STOP");
        Generation generation = new Generation(assistantMessage, metadata);
        ChatResponse response = new ChatResponse(List.of(generation));
        return Flux.just(response);
    }

    // ---- isActive ----

    @Test
    void isActive_synthesizerActive_returnsTrue() {
        when(synthesizer.isActive()).thenReturn(true);

        assertTrue(persona.isActive());
    }

    @Test
    void isActive_playerHasContent_returnsTrue() {
        when(synthesizer.isActive()).thenReturn(false);
        when(player.hasContent()).thenReturn(true);

        assertTrue(persona.isActive());
    }

    @Test
    void isActive_bothIdle_returnsFalse() {
        when(synthesizer.isActive()).thenReturn(false);
        when(player.hasContent()).thenReturn(false);

        assertFalse(persona.isActive());
    }

    @Test
    void isActive_nullSynthesizer_checksPlayer() {
        Persona personaNoSynth = persona.toBuilder().synthesizer(null).build();
        when(player.hasContent()).thenReturn(true);

        assertTrue(personaNoSynth.isActive());
    }

    @Test
    void isActive_nullSynthesizerAndPlayer_returnsFalse() {
        Persona personaEmpty = persona.toBuilder()
                .synthesizer(null)
                .player(null)
                .build();

        assertFalse(personaEmpty.isActive());
    }

    // ---- chat(String) ----

    @Test
    void chat_string_invokesLlmAndSynthesizer() {
        Flux<ChatResponse> responseFlux = createChatResponseFlux("你好！");
        when(chatModel.stream(any(Prompt.class))).thenReturn(responseFlux);
        when(conversation.messages()).thenReturn(new ArrayList<>());

        persona.chat("你好");

        // 验证 conversation 收到了 UserMessage
        ArgumentCaptor<UserMessage> captor = ArgumentCaptor.forClass(UserMessage.class);
        verify(conversation).add(captor.capture());
        assertEquals("你好", captor.getValue().getText());

        // 验证 synthesizer 被调用
        verify(synthesizer).synthesize(any(Flux.class));
    }

    // ---- wakeUp ----

    @Test
    void wakeUp_callsChatWithFunctionCallDisabled() {
        SysRole role = new SysRole();
        when(conversation.getRole()).thenReturn(role);

        Flux<ChatResponse> responseFlux = createChatResponseFlux("早上好！");
        when(chatModel.stream(any(Prompt.class))).thenReturn(responseFlux);
        when(conversation.messages()).thenReturn(new ArrayList<>());

        persona.wakeUp("你好小智");

        // 验证 synthesizer 被调用
        verify(synthesizer).synthesize(any(Flux.class));

        // 验证 conversation 收到了 UserMessage
        verify(conversation).add(any(UserMessage.class));
    }

    @Test
    void wakeUp_nullConversation_throwsException() {
        Persona personaNoConv = persona.toBuilder().conversation(null).build();

        assertThrows(IllegalArgumentException.class, () -> personaNoConv.wakeUp("你好"));
    }

    // ---- sendGoodbyeMessage ----

    @Test
    void sendGoodbyeMessage_withSupplier_synthesizesDirectly() {
        when(goodbyeMessages.get()).thenReturn("拜拜啦！");

        persona.sendGoodbyeMessage();

        // 验证设置了 functionAfterChat（关闭session的回调）
        verify(player).setFunctionAfterChat(any(Runnable.class));

        // 验证直接合成告别语，不经过 LLM
        verify(synthesizer).synthesize(eq("拜拜啦！"));
        verifyNoInteractions(chatModel);
    }

    @Test
    void sendGoodbyeMessage_withoutSupplier_fallsBackToChat() {
        Persona personaNoGoodbye = persona.toBuilder().goodbyeMessages(null).build();

        Flux<ChatResponse> responseFlux = createChatResponseFlux("再见！");
        when(chatModel.stream(any(Prompt.class))).thenReturn(responseFlux);
        when(conversation.messages()).thenReturn(new ArrayList<>());

        personaNoGoodbye.sendGoodbyeMessage();

        // 没有 supplier → 走 chat("我有事先忙了，再见！", false)
        verify(player).setFunctionAfterChat(any(Runnable.class));
        verify(synthesizer).synthesize(any(Flux.class));
    }

    @Test
    void sendGoodbyeMessage_nullSession_doesNothing() {
        Persona personaNoSession = persona.toBuilder().session(null).build();

        assertDoesNotThrow(personaNoSession::sendGoodbyeMessage);
        verifyNoInteractions(player);
    }

    @Test
    void sendGoodbyeMessage_functionAfterChat_closesSession() {
        when(goodbyeMessages.get()).thenReturn("再见");

        persona.sendGoodbyeMessage();

        // 捕获设置的回调并执行
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(player).setFunctionAfterChat(captor.capture());

        Runnable afterChat = captor.getValue();
        afterChat.run();

        // 验证回调执行了清理操作
        verify(session).setPersona(null);
        verify(session).setPlayer(null);
        verify(conversation).clear();
        verify(session).close();
    }
}

package com.xiaozhi.dialogue.service;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.llm.intent.IntentDetector;
import com.xiaozhi.dialogue.llm.intent.IntentDetector.UserIntent;
import com.xiaozhi.dialogue.service.VadService.VadStatus;
import com.xiaozhi.dialogue.voiceprint.VoiceprintRecognitionService;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.event.ChatAbortEvent;
import com.xiaozhi.service.*;

import com.xiaozhi.utils.AudioUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 对话处理服务
 * 负责处理语音识别和对话生成的业务逻辑
 * 核心对话逻辑已委托给 Persona，DialogueService 主要负责：
 * 1. 音频数据接收与VAD处理
 * 2. STT流式识别的启动与音频流管理
 * 3. 唤醒词处理
 * 4. 对话中止（abort）
 */
@Service
public class DialogueService{
    private static final Logger logger = LoggerFactory.getLogger(DialogueService.class);

    private static final String ABORT_REASON_VAD_INTERRUPT = "检测到vad";

    @Resource
    private ChatService chatService;

    @Resource
    private MessageService messageService;

    @Resource
    private VadService vadService;

    @Resource
    private SessionManager sessionManager;

    @Resource
    private SysRoleService roleService;

    @Resource
    private IntentDetector intentDetector;

    @Autowired
    private SysDeviceService sysDeviceService;

    @Resource
    private VoiceprintRecognitionService voiceprintRecognitionService;


    @org.springframework.context.event.EventListener
    public void onApplicationEvent(ChatAbortEvent event) {
        ChatSession chatSession = event.getSession();
        String reason = event.getReason();
        abortDialogue(chatSession, reason);
    }

    /**
     * 处理音频数据
     */
    public void processAudioData(ChatSession session, byte[] opusData) {
        if (session == null || opusData == null || opusData.length == 0) {
            return;
        }
        String sessionId = session.getSessionId();

        try {
            // 如果正在唤醒响应中,忽略音频数据,避免被唤醒词误触发VAD
            if (session.isInWakeupResponse()) {
                return;
            }

            // 如果播放器正在执行后续回调（如告别语播放中），忽略音频数据
            Player player = session.getPlayer();
            if (player != null && player.getFunctionAfterChat() != null) {
                return;
            }

            SysDevice device = session.getSysDevice();
            // 如果设备未注册或未绑定，忽略音频数据
            if (device == null || ObjectUtils.isEmpty(device.getRoleId())) {
                return;
            }

            // 处理VAD
            VadService.VadResult vadResult = vadService.processAudio(sessionId, opusData);
            if (vadResult == null || vadResult.getStatus() == VadStatus.ERROR
                    || vadResult.getProcessedData() == null) {
                return;
            }

            // 检测到语音活动，更新最后活动时间
            sessionManager.updateLastActivity(sessionId);
            // 根据VAD状态处理
            handleVadStatus(session, vadResult);
        } catch (Exception e) {
            logger.error("处理音频数据失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 根据VAD状态分发处理
     */
    private void handleVadStatus(ChatSession session, VadService.VadResult vadResult) {
        switch (vadResult.getStatus()) {
            case SPEECH_START:
                handleSpeechStart(session, vadResult.getProcessedData());
                break;

            case SPEECH_CONTINUE:
                handleSpeechContinue(session.getSessionId(), vadResult.getProcessedData());
                break;

            case SPEECH_END:
                handleSpeechEnd(session.getSessionId());
                break;

            default:
                break;
        }
    }

    /**
     * 处理语音开始：启动STT并中止当前播放
     */
    private void handleSpeechStart(ChatSession session, byte[] processedData) {
        String sessionId = session.getSessionId();
        // 先启动STT（同步创建音频流），确保流已准备好
        startStt(session, sessionId, processedData);
        // 再触发abort停止当前播放中的TTS
        // 通过Persona.isActive()综合判断整个管道是否活跃（LLM/TTS/Player任一层）
        Persona persona = session.getPersona();
        if (persona != null && persona.isActive()) {
            abortDialogue(session, ABORT_REASON_VAD_INTERRUPT);
        }
    }

    /**
     * 处理语音继续：发送数据到流式识别
     */
    private void handleSpeechContinue(String sessionId, byte[] processedData) {
        if (sessionManager.isStreaming(sessionId)) {
            sessionManager.sendAudioData(sessionId, processedData);
        }
    }

    /**
     * 处理语音结束：完成流式识别，并记录 speechEndAt 时间戳作为 TTFS 起点
     */
    private void handleSpeechEnd(String sessionId) {
        if (sessionManager.isStreaming(sessionId)) {
            // D4: 记录 speechEndAt 时间戳，作为 TTFS 端到端延迟的起点
            ChatSession session = sessionManager.getSession(sessionId);
            if (session != null) {
                session.setSpeechEndAt(Instant.now());
            }
            sessionManager.completeAudioStream(sessionId);
            sessionManager.setStreamingState(sessionId, false);
        }
    }

    /**
     * 启动语音识别
     * 同步创建音频流（避免竞态条件），然后在虚拟线程中执行识别和对话。
     * C1: 声纹识别在STT启动前同时启动（使用CompletableFuture+虚拟线程），
     * 两者并行执行，STT完成后获取声纹结果并在LLM调用前绑定到session。
     */
    private void startStt(
            ChatSession session,
            String sessionId,
            byte[] initialAudio) {
        Assert.notNull(session, "session不能为空");

        // 同步部分：先创建音频流和设置状态，避免竞态条件
        // 这样可以确保后续的SPEECH_CONTINUE能正确发送数据
        sessionManager.closeAudioStream(sessionId);
        sessionManager.createAudioStream(sessionId);
        sessionManager.setStreamingState(sessionId, true);

        Thread.startVirtualThread(() -> {
            try {
                // 重置上一轮的 profiling 时间戳
                session.resetProfilingTimestamps();

                // 发送初始音频数据
                if (initialAudio != null && initialAudio.length > 0) {
                    sessionManager.sendAudioData(sessionId, initialAudio);
                }

                if (sessionManager.getAudioStream(sessionId) == null) {
                    return;
                }

                Persona persona = session.getPersona();
                if (persona == null || persona.getSttService() == null) {
                    return;
                }

                // C1: 在STT启动前同时启动声纹识别（与STT并行执行，利用虚拟线程）
                // 声纹识别从VAD收集的PCM数据中提取嵌入向量，与STT无数据依赖，可完全并行
                CompletableFuture<VoiceprintRecognitionService.RecognitionResult> voiceprintFuture =
                        startVoiceprintRecognition(session);

                // 记录 STT 调用开始时刻
                session.setSttStartedAt(Instant.now());

                // 1. STT识别（手动调用，不走persona.chat(audioFlux)，以便在STT和LLM之间插入意图检测）
                String finalText = persona.getSttService().streamRecognition(
                        sessionManager.getAudioStream(sessionId).asFlux());
                if (!StringUtils.hasText(finalText)) {
                    return;
                }

                // 记录 STT 完成时刻（用于 TTFS 分段耗时计算）
                session.setSttCompletedAt(Instant.now());

                // 发送STT识别结果到设备
                persona.getPlayer().sendStt(finalText);

                // STT完成后获取声纹识别结果（此时声纹识别应已完成或即将完成）
                // W2: 在LLM调用之前确保声纹结果已绑定到session
                if (voiceprintFuture != null) {
                    try {
                        VoiceprintRecognitionService.RecognitionResult result = voiceprintFuture.join();
                        if (result != null) {
                            logger.info("声纹识别结果: userId={}, name={}, 相似度={}",
                                    result.userId(), result.voiceprintName(),
                                    String.format("%.4f", result.similarity()));
                            // W2: 将识别到的说话人ID和名称注入到会话属性，供LLM对话使用
                            session.setAttribute("speakerId", result.voiceprintId());
                            session.setAttribute("speakerName", result.voiceprintName());
                        }
                    } catch (Exception e) {
                        logger.debug("声纹识别结果获取失败（不影响主流程）: {}", e.getMessage());
                    }
                }

                // 生成用户音频保存路径
                Instant userInstant = Instant.now();
                Path userAudioPath = session.getAudioPath(MessageType.USER.getValue(), userInstant);

                // 2. 意图检测：在LLM之前拦截明确意图（如"退出"），避免不必要的LLM调用
                UserIntent intent = intentDetector.detectIntent(finalText);
                if (intent != null) {
                    handleIntent(session, intent, finalText);
                } else {
                    // 3. 无明确意图，走LLM+TTS流程
                    persona.chat(finalText);
                }

                // 无论是否走LLM，都保存用户音频
                saveUserAudio(session, userAudioPath);

            } catch (Exception e) {
                logger.error("流式识别错误: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 启动声纹识别（异步，与STT/LLM流程并行）
     * 从VAD收集的PCM数据中提取说话人嵌入向量，与缓存中的声纹进行比对。
     *
     * @param session 聊天会话
     * @return 声纹识别结果的Future，声纹功能不可用时返回null
     */
    private CompletableFuture<VoiceprintRecognitionService.RecognitionResult> startVoiceprintRecognition(
            ChatSession session) {
        if (!voiceprintRecognitionService.isAvailable()) {
            return null;
        }

        SysDevice device = session.getSysDevice();
        if (device == null) {
            return null;
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 从VAD获取本轮PCM数据
                List<byte[]> pcmFrames = vadService.getPcmData(session.getSessionId());
                if (pcmFrames == null || pcmFrames.isEmpty()) {
                    return null;
                }

                // 合并PCM帧
                int totalSize = pcmFrames.stream().mapToInt(frame -> frame.length).sum();
                byte[] fullPcmData = new byte[totalSize];
                int offset = 0;
                for (byte[] frame : pcmFrames) {
                    System.arraycopy(frame, 0, fullPcmData, offset, frame.length);
                    offset += frame.length;
                }

                // 提取嵌入向量
                float[] embedding = voiceprintRecognitionService.extractEmbedding(fullPcmData);
                if (embedding == null) {
                    return null;
                }

                // 执行声纹比对
                return voiceprintRecognitionService.recognize(device.getDeviceId(), embedding);
            } catch (Exception e) {
                logger.debug("声纹识别失败（不影响主流程）: {}", e.getMessage());
                return null;
            }
        }, runnable -> Thread.startVirtualThread(runnable));
    }

    /**
     * 处理语音唤醒
     */
    public void handleWakeWord(ChatSession session, String text) {
        logger.info("检测到唤醒词: {}", text);
        try {
            // 设置唤醒响应状态,在响应期间忽略VAD检测
            session.setInWakeupResponse(true);

            SysDevice device = session.getSysDevice();
            if (device == null) {
                return;
            }

            session.getPersona().wakeUp(text);
        } catch (Exception e) {
            logger.error("处理唤醒词失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理文本消息交互
     *
     * @param session
     * @param inputText    输入文本
     */
    public void handleText(ChatSession session, String inputText ) {
        String sessionId = session.getSessionId();

        try {
            SysDevice device = sessionManager.getDeviceConfig(sessionId);
            if (device == null) {
                return;
            }
            sessionManager.updateLastActivity(sessionId);
            // 发送识别结果
            messageService.sendSttMessage(session, inputText);

            logger.info("处理聊天文字输入: \"{}\"", inputText);

            // 优先检测用户意图，如果检测到明确意图则直接处理，不走LLM
            UserIntent intent = intentDetector.detectIntent(inputText);
            if (intent != null) {
                handleIntent(session, intent, inputText);
                return;
            }

            session.getPersona().chat(inputText);

        } catch (Exception e) {
            logger.error("处理文本消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理检测到的用户意图
     *
     * @param session 聊天会话
     * @param intent 检测到的意图
     * @param userInput 用户输入文本
     */
    private void handleIntent(ChatSession session, IntentDetector.UserIntent intent, String userInput) {

        logger.info("处理用户意图: type={}, input=\"{}\"", intent.getType(), userInput);

        switch (intent.getType()) {
            case "EXIT":
                // 处理退出意图
                sendGoodbyeMessage(session);
                break;

            default:
                logger.warn("未知的意图类型: {}", intent.getType());
                break;
        }
    }

    /**
     * 发送告别语并在播放完成后关闭会话
     * 委托给Persona处理告别流程
     *
     * @param session WebSocket会话
     */
    public void sendGoodbyeMessage(ChatSession session) {
        if (session == null) {
            return;
        }
        Persona persona = session.getPersona();
        if (persona != null) {
            persona.sendGoodbyeMessage();
        } else {
            session.close();
        }
    }

    /**
     * 中止当前对话
     * 先取消Synthesizer的上游Flux订阅，再停止Player。
     * 如果不先取消Synthesizer，DialogueHelper会继续分句并调用player.play(newFlux)，
     * 导致音频重叠或播放被清空后又有新音频进来。
     */
    public void abortDialogue(ChatSession session, String reason) {
        try {
            String sessionId = session.getSessionId();
            logger.info("中止对话 - SessionId: {}, Reason: {}", sessionId, reason);

            // 关闭音频流
            // 注意：当reason是"检测到vad"时，不关闭音频流和重置streaming状态
            // 因为这是用户打断TTS继续说话，startStt已经创建了新的音频流
            if (!ABORT_REASON_VAD_INTERRUPT.equals(reason)) {
                sessionManager.closeAudioStream(sessionId);
                sessionManager.setStreamingState(sessionId, false);
            }

            // 先取消语音合成器的上游Flux订阅，停止产生新的音频数据
            Persona persona = session.getPersona();
            if (persona != null && persona.getSynthesizer() != null) {
                persona.getSynthesizer().cancel();
            }

            // 再终止音频播放，清空播放队列
            Player player = session.getPlayer();
            if(player!=null){
                player.stop();
            }

            // 无论player是否存在，都需要发送stop消息通知设备进入聆听状态
            // 这是因为设备可能在还未创建player时就发送了abort消息
            messageService.sendTtsMessage(session, null, "stop");

            // 如果在goodbye流程中被打断（functionAfterChat已设置），
            // 需要执行清理回调（关闭session等），并清除回调防止重复执行
            if (player != null) {
                Runnable afterChat = player.getFunctionAfterChat();
                if (afterChat != null) {
                    player.setFunctionAfterChat(null);
                    afterChat.run();
                }
            }
        } catch (Exception e) {
            logger.error("中止对话失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 保存用户音频数据为WAV文件
     */
    private void saveUserAudio(ChatSession session, Path path) {
        List<byte[]> pcmFrames = vadService.getPcmData(session.getSessionId());
        if (pcmFrames == null || pcmFrames.isEmpty()) {
            return;
        }
        int totalSize = pcmFrames.stream().mapToInt(frame -> frame.length).sum();
        byte[] fullPcmData = new byte[totalSize];
        int offset = 0;
        for (byte[] frame : pcmFrames) {
            System.arraycopy(frame, 0, fullPcmData, offset, frame.length);
            offset += frame.length;
        }
        AudioUtils.saveAsWav(path, fullPcmData);
        logger.debug("用户音频已保存: {}", path);
    }

}

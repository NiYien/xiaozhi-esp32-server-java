package com.xiaozhi.dialogue.service;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.llm.intent.IntentDetector;
import com.xiaozhi.dialogue.llm.intent.IntentDetector.UserIntent;
import com.xiaozhi.dialogue.service.VadService.VadStatus;
import com.xiaozhi.dialogue.voiceprint.VoiceprintRecognitionService;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.event.ChatAbortEvent;
import com.xiaozhi.service.*;

import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.UserAudioWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

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

    @Autowired
    private WakeupWordService deviceWakeupService;

    @Resource
    private com.xiaozhi.dao.MessageMapper messageMapper;


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
     * 声纹识别在STT完成后同步执行，此时VAD的PCM数据完整（2-5秒），确保声纹提取成功率。
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

                // 声纹识别：STT完成后同步执行，此时VAD的PCM数据完整
                try {
                    VoiceprintRecognitionService.RecognitionResult voiceprintResult =
                            performVoiceprintRecognition(session);
                    if (voiceprintResult != null) {
                        logger.info("声纹识别结果: userId={}, name={}, 相似度={}",
                                voiceprintResult.userId(), voiceprintResult.voiceprintName(),
                                String.format("%.4f", voiceprintResult.similarity()));
                        session.setAttribute("speakerId", voiceprintResult.voiceprintId());
                        session.setAttribute("speakerName", voiceprintResult.voiceprintName());
                    }
                } catch (Exception e) {
                    logger.debug("声纹识别失败（不影响主流程）: {}", e.getMessage());
                }

                // 保存用户音频为 OGG Opus 文件
                Instant userInstant = Instant.now();
                String userAudioPath = saveUserAudioAsOpus(session, userInstant);

                // 将用户音频路径设置到 session，供 Persona 在保存消息时使用
                if (userAudioPath != null) {
                    session.setAttribute("pendingUserAudioPath", userAudioPath);
                    // 计算 audioGroup
                    String audioGroup = computeAudioGroup(session);
                    session.setAttribute("pendingAudioGroup", audioGroup);
                }

                // 2. 意图检测：在LLM之前拦截明确意图（如"退出"），避免不必要的LLM调用
                UserIntent intent = intentDetector.detectIntent(finalText);
                if (intent != null) {
                    handleIntent(session, intent, finalText);
                } else {
                    // 3. 无明确意图，走LLM+TTS流程
                    persona.chat(finalText);
                }

                // 清除临时属性
                session.setAttribute("pendingUserAudioPath", null);
                session.setAttribute("pendingAudioGroup", null);

            } catch (Exception e) {
                logger.error("流式识别错误: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 执行声纹识别（同步）
     * 从VAD收集的PCM数据中提取说话人嵌入向量，与缓存中的声纹进行比对。
     * 在STT完成后调用，此时VAD已收集到完整的PCM数据（2-5秒），确保声纹提取成功率。
     *
     * @param session 聊天会话
     * @return 声纹识别结果，声纹功能不可用时返回null
     */
    private VoiceprintRecognitionService.RecognitionResult performVoiceprintRecognition(
            ChatSession session) {
        if (!voiceprintRecognitionService.isAvailable()) {
            return null;
        }

        SysDevice device = session.getSysDevice();
        if (device == null) {
            return null;
        }

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
    }

    /**
     * 处理语音唤醒
     * 根据唤醒词文本查找映射的角色，实现不同唤醒词激活不同角色
     */
    public void handleWakeWord(ChatSession session, String text) {
        logger.info("检测到唤醒词: {}", text);
        // 设置唤醒响应状态,在响应期间忽略VAD检测
        session.setInWakeupResponse(true);
        try {
            SysDevice device = session.getSysDevice();
            if (device == null) {
                return;
            }

            // 根据唤醒词查找映射的角色ID
            Integer targetRoleId = deviceWakeupService.getRoleIdByWakeupWord(device.getDeviceId(), text);
            if (targetRoleId == null) {
                // 未匹配，回退到设备默认角色
                targetRoleId = device.getRoleId();
                logger.debug("唤醒词 '{}' 未匹配到映射，使用默认角色 roleId: {}", text, targetRoleId);
            } else {
                logger.info("唤醒词 '{}' 匹配到角色 roleId: {}", text, targetRoleId);
            }

            // 切换角色前，停止当前角色的播放（避免音频混合）
            Persona currentPersona = session.getPersona();
            if (currentPersona != null && currentPersona.getPlayer() != null) {
                currentPersona.getPlayer().stop();
            }

            // 从 PersonaRegistry 获取或创建目标角色的 Persona
            PersonaRegistry registry = session.getPersonaRegistry();
            final Integer roleId = targetRoleId;
            Persona persona = registry.getOrCreate(roleId, () -> {
                // 需要从数据库加载角色信息并构建 Persona
                SysRole role = roleService.selectRoleById(roleId);
                if (role == null) {
                    logger.error("角色不存在，roleId: {}", roleId);
                    return null;
                }
                return chatService.buildPersona(session, device, role);
            });

            if (persona == null) {
                logger.error("无法创建 Persona，roleId: {}", roleId);
                return;
            }

            // 激活目标角色
            registry.activate(roleId);

            persona.wakeUp(text);
        } catch (Exception e) {
            logger.error("处理唤醒词失败: {}", e.getMessage(), e);
        } finally {
            // 确保无论正常返回还是异常，都重置唤醒响应状态
            session.setInWakeupResponse(false);
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
     * 保存用户音频数据为 OGG Opus 文件（16kHz）
     * 从 VadService 获取缓存的原始 Opus 帧，写入文件并更新数据库
     *
     * @param session 聊天会话
     * @param instant 用户消息时间戳
     * @return 音频文件路径字符串，保存失败返回 null
     */
    private String saveUserAudioAsOpus(ChatSession session, Instant instant) {
        try {
            List<byte[]> opusFrames = vadService.getOpusFrames(session.getSessionId());
            if (opusFrames == null || opusFrames.isEmpty()) {
                return null;
            }

            SysDevice device = session.getSysDevice();
            if (device == null || device.getRoleId() == null) {
                return null;
            }

            Path opusPath = UserAudioWriter.generatePath(
                    device.getDeviceId(), device.getRoleId(), instant);

            boolean saved = UserAudioWriter.writeOpusFile(opusFrames, opusPath);
            if (!saved) {
                return null;
            }

            logger.debug("用户音频已保存为 Opus: {}", opusPath);
            return opusPath.toString();
        } catch (Exception e) {
            logger.error("保存用户音频失败（不影响对话流程）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 计算 audioGroup：
     * 查询同设备同会话最近一条 user 消息的时间戳，
     * 间隔 < 2 秒则复用其 audioGroup，否则生成新 UUID
     */
    private String computeAudioGroup(ChatSession session) {
        try {
            SysDevice device = session.getSysDevice();
            if (device == null) {
                return UUID.randomUUID().toString();
            }

            Persona persona = session.getPersona();
            String sessionIdStr = (persona != null && persona.getConversation() != null)
                    ? persona.getConversation().sessionId()
                    : session.getSessionId();

            SysMessage lastUserMsg = messageMapper.findLastUserMessage(
                    device.getDeviceId(), sessionIdStr);

            if (lastUserMsg != null && lastUserMsg.getCreateTime() != null) {
                long intervalMs = System.currentTimeMillis() - lastUserMsg.getCreateTime().getTime();
                if (intervalMs < 2000 && lastUserMsg.getAudioGroup() != null) {
                    return lastUserMsg.getAudioGroup();
                }
            }
        } catch (Exception e) {
            logger.debug("计算 audioGroup 失败，使用新 UUID: {}", e.getMessage());
        }
        return UUID.randomUUID().toString();
    }

}

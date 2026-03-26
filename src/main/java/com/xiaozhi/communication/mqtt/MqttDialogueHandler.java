package com.xiaozhi.communication.mqtt;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.MessageHandler;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.domain.*;
import com.xiaozhi.communication.udp.MqttUdpSession;
import com.xiaozhi.communication.udp.UdpAudioServer;
import com.xiaozhi.communication.udp.UdpProperties;
import com.xiaozhi.communication.udp.UdpSessionContext;
import com.xiaozhi.dialogue.service.DialogueService;
import com.xiaozhi.event.ChatAbortEvent;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MQTT 对话消息处理器
 * 监听设备发来的对话控制消息（hello/listen/abort/goodbye/iot/mcp），
 * 管理 MQTT+UDP 对话生命周期，复用 MessageHandler 的现有对话逻辑
 */
@Component
@ConditionalOnProperty(name = "xiaozhi.mqtt.enabled", havingValue = "true")
public class MqttDialogueHandler {

    private static final Logger logger = LoggerFactory.getLogger(MqttDialogueHandler.class);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 管理类消息类型，由 MqttDeviceStatusListener 处理，MqttDialogueHandler 忽略 */
    private static final Set<String> MANAGEMENT_TYPES = Set.of("online", "offline", "heartbeat", "sensor");

    @Resource
    private MqttService mqttService;

    @Resource
    private MqttProperties mqttProperties;

    @Resource
    private MessageHandler messageHandler;

    @Resource
    private SessionManager sessionManager;

    @Resource
    private DialogueService dialogueService;

    @Resource
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private UdpAudioServer udpAudioServer;

    @Autowired(required = false)
    private UdpProperties udpProperties;

    @PostConstruct
    public void init() {
        // 订阅设备状态 topic，与 MqttDeviceStatusListener 共享
        String statusTopicFilter = mqttProperties.getTopicPrefix() + "/+/device/+/status";
        mqttService.subscribe(statusTopicFilter, 1, this::handleMessage);
        logger.info("MqttDialogueHandler 已订阅: {}", statusTopicFilter);

        // 设置 UdpAudioServer 的音频回调
        if (udpAudioServer != null) {
            udpAudioServer.setAudioCallback(this::handleUdpAudio);
            logger.info("UDP 音频回调已注册");
        }
    }

    /**
     * MQTT 消息入口：解析 type 字段，分发到 hello 处理或通用消息处理
     */
    private void handleMessage(String topic, String payload) {
        try {
            // 快速提取 type 字段
            MqttMessage mqttMessage = JsonUtil.fromJson(payload, MqttMessage.class);
            if (mqttMessage == null || mqttMessage.getType() == null) {
                return;
            }

            String type = mqttMessage.getType();

            // 忽略管理类消息（由 MqttDeviceStatusListener 处理）
            if (MANAGEMENT_TYPES.contains(type)) {
                return;
            }

            // 从 topic 解析 userId 和 deviceId
            // 格式: xiaozhi/{userId}/device/{deviceId}/status
            String[] parts = topic.split("/");
            if (parts.length < 5) {
                logger.warn("无效的 topic 格式: {}", topic);
                return;
            }
            String userId = parts[1];
            String deviceId = parts[3];

            if ("hello".equals(type)) {
                handleHello(topic, payload, userId, deviceId);
            } else {
                handleDialogueMessage(payload, deviceId, type);
            }
        } catch (Exception e) {
            logger.error("MqttDialogueHandler 处理消息异常 - topic: {}", topic, e);
        }
    }

    /**
     * 处理 hello 消息：创建 MqttUdpSession，回复 hello 响应（含 UDP 参数）
     */
    private void handleHello(String topic, String payload, String userId, String deviceId) {
        logger.info("收到 MQTT hello - DeviceId: {}", deviceId);

        // 如果设备已有活跃对话 session，先中止进行中的对话再关闭
        ChatSession existingSession = sessionManager.getSessionByDeviceId(deviceId);
        if (existingSession != null) {
            logger.info("设备重新连接，关闭旧 session - DeviceId: {}, OldSessionId: {}",
                    deviceId, existingSession.getSessionId());
            applicationContext.publishEvent(new ChatAbortEvent(existingSession, "设备重新连接"));
            sessionManager.closeSession(existingSession);
        }

        // 分配 session_id 和 SSRC
        String sessionId = UUID.randomUUID().toString();
        int ssrc = SECURE_RANDOM.nextInt();

        // 生成 AES-128 密钥（16 字节）
        byte[] aesKey = new byte[16];
        SECURE_RANDOM.nextBytes(aesKey);

        // 生成结构化初始 nonce: [0]=0x01, [1]=0x00, [4:7]=SSRC, 其余随机
        byte[] nonce = new byte[16];
        SECURE_RANDOM.nextBytes(nonce);
        nonce[0] = 0x01;
        nonce[1] = 0x00;
        nonce[4] = (byte) ((ssrc >> 24) & 0xFF);
        nonce[5] = (byte) ((ssrc >> 16) & 0xFF);
        nonce[6] = (byte) ((ssrc >> 8) & 0xFF);
        nonce[7] = (byte) (ssrc & 0xFF);

        // 构建 command topic（用于下发消息给设备）
        String commandTopic = mqttService.buildCommandTopic(userId, deviceId);

        // 创建 UdpSessionContext 并注册
        UdpSessionContext udpContext = new UdpSessionContext(sessionId, ssrc, aesKey, nonce);
        if (udpAudioServer != null) {
            udpAudioServer.registerSession(udpContext);
        }

        // 创建 MqttUdpSession
        MqttUdpSession session = new MqttUdpSession(sessionId, mqttService,
                udpAudioServer, udpContext, commandTopic);

        // 调用 messageHandler.afterConnection 复用设备初始化逻辑
        messageHandler.afterConnection(session, deviceId);

        // 标记设备 MQTT 在线，确保前端管理页面状态实时更新
        sessionManager.setMqttOnline(deviceId, true);

        // 构造 hello 响应
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "hello");
        response.put("transport", "udp");
        response.put("session_id", sessionId);

        Map<String, Object> audioParams = new LinkedHashMap<>();
        audioParams.put("sample_rate", 24000);
        audioParams.put("channels", 1);
        audioParams.put("format", "opus");
        audioParams.put("frame_duration", 60);
        response.put("audio_params", audioParams);

        // UDP 连接参数
        Map<String, Object> udpParams = new LinkedHashMap<>();
        String externalIp = udpProperties != null ? udpProperties.getExternalIp() : null;
        if (externalIp == null || externalIp.isEmpty()) {
            logger.warn("UDP external-ip 未配置，设备将无法建立 UDP 连接");
            externalIp = "";
        }
        udpParams.put("server", externalIp);
        udpParams.put("port", udpProperties != null ? udpProperties.getPort() : 8888);
        udpParams.put("key", bytesToHex(aesKey));
        udpParams.put("nonce", bytesToHex(nonce));
        response.put("udp", udpParams);

        // 通过 MQTT command topic 发布 hello 响应
        String responseJson = JsonUtil.toJson(response);
        mqttService.publish(commandTopic, responseJson, 1);

        logger.info("MQTT hello 响应已发送 - DeviceId: {}, SessionId: {}, SSRC: 0x{}",
                deviceId, sessionId, Integer.toHexString(ssrc));
    }

    /**
     * 处理非 hello 的对话消息：解析为 Message 子类，委托 MessageHandler
     */
    private void handleDialogueMessage(String payload, String deviceId, String type) {
        // 查找设备对应的 session
        ChatSession chatSession = sessionManager.getSessionByDeviceId(deviceId);
        if (chatSession == null) {
            logger.debug("收到消息但设备无活跃 session - DeviceId: {}, type: {}", deviceId, type);
            return;
        }

        String sessionId = chatSession.getSessionId();

        try {
            // 使用 Jackson 多态反序列化 MQTT JSON 为 Message 子类
            Message msg = JsonUtil.fromJson(payload, Message.class);
            if (msg == null || msg instanceof HelloMessage || msg instanceof UnknownMessage) {
                return;
            }

            // goodbye 消息处理完后需要额外关闭连接
            if (msg instanceof GoodbyeMessage) {
                messageHandler.handleMessage(msg, sessionId);
                // handleGoodbyeMessage 内部已调用 sessionManager.closeSession
                // 不再重复调用 afterConnectionClosed，避免双重关闭
                sessionManager.setMqttOnline(deviceId, false);
                return;
            }

            // 其他消息委托 MessageHandler
            messageHandler.handleMessage(msg, sessionId);

        } catch (Exception e) {
            logger.error("处理对话消息异常 - DeviceId: {}, type: {}", deviceId, type, e);
        }
    }

    /**
     * UDP 音频数据回调：收到解密音频后，传递给对话引擎
     */
    private void handleUdpAudio(String sessionId, byte[] decryptedOpusData) {
        ChatSession chatSession = sessionManager.getSession(sessionId);
        if (chatSession == null || !chatSession.isOpen()) {
            return;
        }
        dialogueService.processAudioData(chatSession, decryptedOpusData);
    }

    /**
     * 字节数组转 hex 字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}

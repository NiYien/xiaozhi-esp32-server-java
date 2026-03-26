package com.xiaozhi.communication.udp;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.mqtt.MqttService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MQTT+UDP 会话
 * MQTT 通道传输 JSON 控制消息，UDP 通道传输加密 Opus 音频数据
 * 与 WebSocketSession 平级，继承 ChatSession 抽象基类
 */
public class MqttUdpSession extends ChatSession {

    private static final Logger logger = LoggerFactory.getLogger(MqttUdpSession.class);

    private final MqttService mqttService;
    private final UdpAudioServer udpAudioServer;
    private final UdpSessionContext udpContext;
    private final String responseTopic;

    public MqttUdpSession(String sessionId, MqttService mqttService,
                          UdpAudioServer udpAudioServer, UdpSessionContext udpContext,
                          String responseTopic) {
        super(sessionId);
        this.mqttService = mqttService;
        this.udpAudioServer = udpAudioServer;
        this.udpContext = udpContext;
        this.responseTopic = responseTopic;
    }

    @Override
    public boolean isOpen() {
        return mqttService.isConnected() && udpContext.isActive();
    }

    @Override
    public void close() {
        try {
            udpContext.deactivate();
            if (udpAudioServer != null) {
                udpAudioServer.unregisterSession(udpContext.getSsrc());
            }
            logger.info("MqttUdpSession 已关闭 - SessionId: {}, SSRC: 0x{}",
                    getSessionId(), Integer.toHexString(udpContext.getSsrc()));
        } catch (Exception e) {
            logger.error("关闭 MqttUdpSession 异常 - SessionId: {}", getSessionId(), e);
        }
    }

    @Override
    public void sendTextMessage(String message) {
        try {
            mqttService.publish(responseTopic, message, 1);
        } catch (Exception e) {
            logger.error("发送 MQTT 文本消息失败 - SessionId: {}, topic: {}", getSessionId(), responseTopic, e);
        }
    }

    @Override
    public void sendBinaryMessage(byte[] message) {
        try {
            udpAudioServer.sendEncrypted(udpContext, message);
        } catch (Exception e) {
            logger.error("发送 UDP 音频失败 - SessionId: {}", getSessionId(), e);
        }
    }

    public UdpSessionContext getUdpContext() {
        return udpContext;
    }

    public String getResponseTopic() {
        return responseTopic;
    }
}

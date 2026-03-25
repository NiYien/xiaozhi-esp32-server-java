package com.xiaozhi.communication.mqtt;

import jakarta.annotation.PreDestroy;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Eclipse Paho MQTT v5 Client 的 MqttService 实现
 * 负责与外部 MQTT Broker 的连接、发布、订阅等操作
 */
public class PahoMqttService implements MqttService, MqttCallback {
    private static final Logger logger = LoggerFactory.getLogger(PahoMqttService.class);

    private final com.xiaozhi.communication.mqtt.MqttProperties mqttProperties;
    private MqttAsyncClient mqttClient;

    /**
     * 存储订阅的监听器映射，用于消息分发
     */
    private final Map<String, MqttMessageListener> listenerMap = new ConcurrentHashMap<>();

    public PahoMqttService(com.xiaozhi.communication.mqtt.MqttProperties mqttProperties) {
        this.mqttProperties = mqttProperties;
    }

    /**
     * 初始化并连接 MQTT Broker
     */
    public void connect() {
        try {
            String clientId = mqttProperties.getClientIdPrefix() + "-" + UUID.randomUUID().toString().substring(0, 8);
            mqttClient = new MqttAsyncClient(
                    mqttProperties.getBrokerUrl(),
                    clientId,
                    new MemoryPersistence()
            );

            mqttClient.setCallback(this);

            MqttConnectionOptions options = new MqttConnectionOptions();
            options.setConnectionTimeout(mqttProperties.getConnectionTimeout());
            options.setKeepAliveInterval(mqttProperties.getKeepAliveInterval());
            options.setAutomaticReconnect(mqttProperties.isAutomaticReconnect());
            options.setMaxReconnectDelay(mqttProperties.getMaxReconnectDelay() * 1000);
            options.setCleanStart(mqttProperties.isCleanStart());

            if (mqttProperties.getUsername() != null && !mqttProperties.getUsername().isEmpty()) {
                options.setUserName(mqttProperties.getUsername());
            }
            if (mqttProperties.getPassword() != null && !mqttProperties.getPassword().isEmpty()) {
                options.setPassword(mqttProperties.getPassword().getBytes(StandardCharsets.UTF_8));
            }

            logger.info("正在连接 MQTT Broker: {} (clientId: {})", mqttProperties.getBrokerUrl(), clientId);
            mqttClient.connect(options).waitForCompletion(mqttProperties.getConnectionTimeout() * 1000L);
            logger.info("MQTT Broker 连接成功: {}", mqttProperties.getBrokerUrl());

        } catch (MqttException e) {
            logger.error("连接 MQTT Broker 失败: {}", mqttProperties.getBrokerUrl(), e);
        }
    }

    @PreDestroy
    public void disconnect() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                logger.info("正在断开 MQTT Broker 连接...");
                mqttClient.disconnect().waitForCompletion(5000);
                mqttClient.close();
                logger.info("MQTT Broker 已断开连接");
            } catch (MqttException e) {
                logger.error("断开 MQTT Broker 连接时发生错误", e);
            }
        }
    }

    @Override
    public void publish(String topic, String payload, int qos) {
        if (!isConnected()) {
            logger.warn("MQTT 未连接，无法发布消息 - Topic: {}", topic);
            return;
        }
        try {
            org.eclipse.paho.mqttv5.common.MqttMessage message = new org.eclipse.paho.mqttv5.common.MqttMessage();
            message.setPayload(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            mqttClient.publish(topic, message);
            logger.debug("MQTT 消息已发布 - Topic: {}, Payload: {}", topic, payload);
        } catch (MqttException e) {
            logger.error("MQTT 消息发布失败 - Topic: {}", topic, e);
        }
    }

    @Override
    public void subscribe(String topicFilter, int qos, MqttMessageListener listener) {
        if (!isConnected()) {
            logger.warn("MQTT 未连接，无法订阅 - TopicFilter: {}", topicFilter);
            return;
        }
        try {
            MqttSubscription subscription = new MqttSubscription(topicFilter, qos);
            mqttClient.subscribe(new MqttSubscription[]{subscription}, null, null,
                    new IMqttMessageListener[]{(t, m) -> {}}, new org.eclipse.paho.mqttv5.common.packet.MqttProperties());
            listenerMap.put(topicFilter, listener);
            logger.info("MQTT 已订阅 - TopicFilter: {}, QoS: {}", topicFilter, qos);
        } catch (MqttException e) {
            logger.error("MQTT 订阅失败 - TopicFilter: {}", topicFilter, e);
        }
    }

    @Override
    public void unsubscribe(String topicFilter) {
        if (!isConnected()) {
            return;
        }
        try {
            mqttClient.unsubscribe(topicFilter);
            listenerMap.remove(topicFilter);
            logger.info("MQTT 已取消订阅 - TopicFilter: {}", topicFilter);
        } catch (MqttException e) {
            logger.error("MQTT 取消订阅失败 - TopicFilter: {}", topicFilter, e);
        }
    }

    @Override
    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    @Override
    public String getClientId() {
        return mqttClient != null ? mqttClient.getClientId() : "";
    }

    @Override
    public String buildCommandTopic(String userId, String deviceId) {
        return "%s/%s/device/%s/command".formatted(mqttProperties.getTopicPrefix(), userId, deviceId);
    }

    @Override
    public String buildStatusTopic(String userId, String deviceId) {
        return "%s/%s/device/%s/status".formatted(mqttProperties.getTopicPrefix(), userId, deviceId);
    }

    @Override
    public String buildNotifyTopic(String userId, String deviceId) {
        return "%s/%s/device/%s/notify".formatted(mqttProperties.getTopicPrefix(), userId, deviceId);
    }

    @Override
    public String buildSensorTopic(String userId, String deviceId) {
        return "%s/%s/device/%s/sensor".formatted(mqttProperties.getTopicPrefix(), userId, deviceId);
    }

    @Override
    public String buildGroupCommandTopic(String userId, String groupId) {
        return "%s/%s/group/%s/command".formatted(mqttProperties.getTopicPrefix(), userId, groupId);
    }

    @Override
    public String buildGroupNotifyTopic(String userId, String groupId) {
        return "%s/%s/group/%s/notify".formatted(mqttProperties.getTopicPrefix(), userId, groupId);
    }

    // ========== MqttCallback 实现 ==========

    @Override
    public void disconnected(MqttDisconnectResponse disconnectResponse) {
        logger.warn("MQTT 连接断开: {}", disconnectResponse.getReasonString());
    }

    @Override
    public void mqttErrorOccurred(MqttException exception) {
        logger.error("MQTT 错误", exception);
    }

    @Override
    public void messageArrived(String topic, org.eclipse.paho.mqttv5.common.MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        logger.debug("MQTT 收到消息 - Topic: {}, Payload: {}", topic, payload);

        // 遍历监听器，匹配 Topic 并分发消息
        for (Map.Entry<String, MqttMessageListener> entry : listenerMap.entrySet()) {
            if (topicMatches(entry.getKey(), topic)) {
                try {
                    entry.getValue().onMessage(topic, payload);
                } catch (Exception e) {
                    logger.error("处理 MQTT 消息时发生错误 - Topic: {}", topic, e);
                }
            }
        }
    }

    @Override
    public void deliveryComplete(IMqttToken token) {
        // 消息投递完成回调
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (reconnect) {
            logger.info("MQTT 重新连接成功: {}", serverURI);
            // 重连后重新订阅所有 Topic
            resubscribeAll();
        }
    }

    @Override
    public void authPacketArrived(int reasonCode, MqttProperties properties) {
        // 认证包回调，暂不处理
    }

    /**
     * 重连后重新订阅所有已注册的 Topic
     */
    private void resubscribeAll() {
        for (Map.Entry<String, MqttMessageListener> entry : listenerMap.entrySet()) {
            try {
                MqttSubscription subscription = new MqttSubscription(entry.getKey(), mqttProperties.getQos());
                mqttClient.subscribe(new MqttSubscription[]{subscription}, null, null,
                        new IMqttMessageListener[]{(t, m) -> {}}, new org.eclipse.paho.mqttv5.common.packet.MqttProperties());
                logger.info("MQTT 重连后重新订阅 - TopicFilter: {}", entry.getKey());
            } catch (MqttException e) {
                logger.error("MQTT 重连后重新订阅失败 - TopicFilter: {}", entry.getKey(), e);
            }
        }
    }

    /**
     * 判断 Topic 是否匹配过滤器（支持 + 和 # 通配符）
     *
     * @param filter Topic 过滤器
     * @param topic  实际 Topic
     * @return 是否匹配
     */
    private boolean topicMatches(String filter, String topic) {
        if (filter.equals(topic)) {
            return true;
        }
        String[] filterParts = filter.split("/");
        String[] topicParts = topic.split("/");

        for (int i = 0; i < filterParts.length; i++) {
            if ("#".equals(filterParts[i])) {
                return true;
            }
            if (i >= topicParts.length) {
                return false;
            }
            if (!"+".equals(filterParts[i]) && !filterParts[i].equals(topicParts[i])) {
                return false;
            }
        }
        return filterParts.length == topicParts.length;
    }
}

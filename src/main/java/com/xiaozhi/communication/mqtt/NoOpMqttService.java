package com.xiaozhi.communication.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * MQTT 空实现
 * 当 MQTT 未启用时使用此实现，所有操作为空操作
 */
@Service
public class NoOpMqttService implements MqttService {
    private static final Logger logger = LoggerFactory.getLogger(NoOpMqttService.class);

    @Override
    public void publish(String topic, String payload, int qos) {
        logger.debug("MQTT 未启用，忽略发布消息 - Topic: {}", topic);
    }

    @Override
    public void subscribe(String topicFilter, int qos, MqttMessageListener listener) {
        logger.debug("MQTT 未启用，忽略订阅请求 - TopicFilter: {}", topicFilter);
    }

    @Override
    public void unsubscribe(String topicFilter) {
        logger.debug("MQTT 未启用，忽略取消订阅请求 - TopicFilter: {}", topicFilter);
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public String buildCommandTopic(String userId, String deviceId) {
        return "";
    }

    @Override
    public String buildStatusTopic(String userId, String deviceId) {
        return "";
    }

    @Override
    public String buildNotifyTopic(String userId, String deviceId) {
        return "";
    }
}

package com.xiaozhi.communication.mqtt;

import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT 设备状态监听器
 * 订阅设备状态 Topic，将 MQTT 在线状态同步到 SessionManager（DeviceStateManager）
 */
@Component
@ConditionalOnProperty(name = "xiaozhi.mqtt.enabled", havingValue = "true")
public class MqttDeviceStatusListener {
    private static final Logger logger = LoggerFactory.getLogger(MqttDeviceStatusListener.class);

    @Resource
    private MqttService mqttService;

    @Resource
    private MqttProperties mqttProperties;

    @Resource
    private SessionManager sessionManager;

    /**
     * 设备最后心跳时间映射，用于超时检测
     */
    private final ConcurrentHashMap<String, Instant> lastHeartbeatTime = new ConcurrentHashMap<>();

    /**
     * 初始化时订阅所有设备的状态 Topic
     */
    @PostConstruct
    public void init() {
        // 订阅所有设备的状态上报 Topic: xiaozhi/+/device/+/status
        String statusTopicFilter = mqttProperties.getTopicPrefix() + "/+/device/+/status";
        mqttService.subscribe(statusTopicFilter, 1, this::handleStatusMessage);
        logger.info("已订阅设备状态 Topic: {}", statusTopicFilter);
    }

    /**
     * 处理设备状态上报消息
     *
     * @param topic   消息来源 Topic
     * @param payload 消息内容
     */
    @SuppressWarnings("unchecked")
    private void handleStatusMessage(String topic, String payload) {
        try {
            // 从 Topic 中提取设备ID: xiaozhi/{userId}/device/{deviceId}/status
            String[] parts = topic.split("/");
            if (parts.length < 5) {
                logger.warn("无效的状态 Topic 格式: {}", topic);
                return;
            }
            String deviceId = parts[3];

            // 解析消息
            MqttMessage message = JsonUtil.fromJson(payload, MqttMessage.class);
            if (message == null) {
                return;
            }

            String type = message.getType();
            if ("online".equals(type)) {
                sessionManager.setMqttOnline(deviceId, true);
                lastHeartbeatTime.put(deviceId, Instant.now());
                logger.info("设备 MQTT 上线: {}", deviceId);
            } else if ("offline".equals(type)) {
                sessionManager.setMqttOnline(deviceId, false);
                lastHeartbeatTime.remove(deviceId);
                logger.info("设备 MQTT 离线: {}", deviceId);
            } else if ("heartbeat".equals(type)) {
                // 心跳消息，刷新 MQTT 在线状态和最后心跳时间
                sessionManager.setMqttOnline(deviceId, true);
                lastHeartbeatTime.put(deviceId, Instant.now());
                logger.debug("设备 MQTT 心跳: {}", deviceId);
            }
        } catch (Exception e) {
            logger.error("处理设备状态消息失败 - Topic: {}", topic, e);
        }
    }

    /**
     * 定时扫描心跳超时的设备
     * 超过 3 倍心跳间隔未收到心跳的设备标记为 MQTT 离线
     */
    @Scheduled(fixedRate = 60000)
    public void checkHeartbeatTimeout() {
        long timeoutSeconds = (long) mqttProperties.getKeepAliveInterval() * 3;
        Instant threshold = Instant.now().minusSeconds(timeoutSeconds);

        Iterator<Map.Entry<String, Instant>> iterator = lastHeartbeatTime.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Instant> entry = iterator.next();
            if (entry.getValue().isBefore(threshold)) {
                String deviceId = entry.getKey();
                sessionManager.setMqttOnline(deviceId, false);
                iterator.remove();
                logger.warn("设备 MQTT 心跳超时，标记为离线 - DeviceId: {}, 上次心跳: {}", deviceId, entry.getValue());
            }
        }
    }
}

package com.xiaozhi.communication.mqtt;

import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.entity.SysSensorData;
import com.xiaozhi.service.SensorDataService;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * 设备心跳超时阈值（秒）
     * ESP32 固件默认 MQTT keepalive 为 240 秒，使用 3 倍作为超时判断
     */
    private static final long DEVICE_HEARTBEAT_TIMEOUT_SECONDS = 240 * 3;

    @Resource
    private MqttService mqttService;

    @Resource
    private MqttProperties mqttProperties;

    @Resource
    private SessionManager sessionManager;

    @Autowired(required = false)
    private SensorDataService sensorDataService;

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

        // 订阅所有设备的传感器数据 Topic: xiaozhi/+/device/+/sensor
        String sensorTopicFilter = mqttProperties.getTopicPrefix() + "/+/device/+/sensor";
        mqttService.subscribe(sensorTopicFilter, 1, this::handleSensorMessage);
        logger.info("已订阅设备传感器 Topic: {}", sensorTopicFilter);
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
            } else {
                // ESP32 设备可能不发送显式心跳，收到任何消息都视为在线信号
                sessionManager.setMqttOnline(deviceId, true);
                lastHeartbeatTime.put(deviceId, Instant.now());
                logger.debug("设备 MQTT 消息（视为在线信号）: {} - type: {}", deviceId, type);
            }
        } catch (Exception e) {
            logger.error("处理设备状态消息失败 - Topic: {}", topic, e);
        }
    }

    /**
     * 处理设备传感器数据上报消息
     * Topic 格式: xiaozhi/{userId}/device/{deviceId}/sensor
     *
     * @param topic   消息来源 Topic
     * @param payload 消息内容
     */
    @SuppressWarnings("unchecked")
    private void handleSensorMessage(String topic, String payload) {
        try {
            // 从 Topic 中提取设备ID: xiaozhi/{userId}/device/{deviceId}/sensor
            String[] parts = topic.split("/");
            if (parts.length < 5) {
                logger.warn("无效的传感器 Topic 格式: {}", topic);
                return;
            }
            String deviceId = parts[3];

            // 解析消息
            MqttMessage message = JsonUtil.fromJson(payload, MqttMessage.class);
            if (message == null) {
                return;
            }

            // 同时刷新设备在线状态
            sessionManager.setMqttOnline(deviceId, true);
            lastHeartbeatTime.put(deviceId, Instant.now());

            // 解析传感器数据并存储
            if (sensorDataService != null && message.getPayload() instanceof Map) {
                Map<String, Object> sensorPayload = (Map<String, Object>) message.getPayload();
                SysSensorData sensorData = new SysSensorData();
                sensorData.setDeviceId(deviceId);

                if (sensorPayload.containsKey("temperature")) {
                    sensorData.setTemperature(((Number) sensorPayload.get("temperature")).floatValue());
                }
                if (sensorPayload.containsKey("battery")) {
                    sensorData.setBattery(((Number) sensorPayload.get("battery")).intValue());
                }
                if (sensorPayload.containsKey("freeHeap")) {
                    sensorData.setFreeHeap(((Number) sensorPayload.get("freeHeap")).intValue());
                }
                if (sensorPayload.containsKey("wifiRssi")) {
                    sensorData.setWifiRssi(((Number) sensorPayload.get("wifiRssi")).intValue());
                }
                if (sensorPayload.containsKey("uptime")) {
                    sensorData.setUptime(((Number) sensorPayload.get("uptime")).longValue());
                }

                sensorDataService.save(sensorData);
                logger.debug("已存储设备传感器数据 - DeviceId: {}", deviceId);
            }
        } catch (Exception e) {
            logger.error("处理设备传感器消息失败 - Topic: {}", topic, e);
        }
    }

    /**
     * 获取设备最后心跳时间戳
     *
     * @param deviceId 设备ID
     * @return 最后心跳时间，如果设备没有心跳记录则返回 null
     */
    public Instant getLastHeartbeat(String deviceId) {
        return lastHeartbeatTime.get(deviceId);
    }

    /**
     * 获取所有设备的最后心跳时间映射（只读副本）
     *
     * @return 设备ID → 最后心跳时间的映射
     */
    public Map<String, Instant> getAllLastHeartbeats() {
        return Map.copyOf(lastHeartbeatTime);
    }

    /**
     * 定时扫描心跳超时的设备
     * 超过 3 倍心跳间隔未收到心跳的设备标记为 MQTT 离线
     */
    @Scheduled(fixedRate = 60000)
    public void checkHeartbeatTimeout() {
        // 使用与 ESP32 keepalive（240s）匹配的超时阈值
        long timeoutSeconds = DEVICE_HEARTBEAT_TIMEOUT_SECONDS;
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

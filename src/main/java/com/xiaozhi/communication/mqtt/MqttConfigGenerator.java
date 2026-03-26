package com.xiaozhi.communication.mqtt;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MQTT 配置生成器
 * 根据 deviceId 和 userId 生成设备的 MQTT 配置，用于 OTA 接口下发给 ESP32 设备
 */
@Service
@ConditionalOnProperty(name = "xiaozhi.mqtt.enabled", havingValue = "true")
public class MqttConfigGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MqttConfigGenerator.class);

    @Resource
    private MqttProperties mqttProperties;

    @Resource
    private MqttService mqttService;

    /**
     * 生成设备的 MQTT 配置
     * ESP32 固件的 ota.cc 会解析此配置并存入 NVS，自动连接 Broker
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @return MQTT 配置 Map，包含 endpoint、client_id、username、password、keepalive、publish_topic、subscribe_topic
     *         如果设备认证未配置则返回 null
     */
    public Map<String, Object> generateMqttConfig(String userId, String deviceId) {
        // 检查设备认证是否已配置
        if (mqttProperties.getDeviceUsername() == null || mqttProperties.getDeviceUsername().isEmpty()
                || mqttProperties.getDevicePassword() == null || mqttProperties.getDevicePassword().isEmpty()) {
            logger.warn("MQTT 设备认证未配置（device-username/device-password），OTA 不返回 MQTT 配置");
            return null;
        }

        Map<String, Object> mqttConfig = new LinkedHashMap<>();

        // endpoint 取值：优先 deviceEndpoint，未配置回退 brokerUrl 去协议前缀
        String endpoint;
        String deviceEndpoint = mqttProperties.getDeviceEndpoint();
        if (deviceEndpoint != null && !deviceEndpoint.isEmpty()) {
            endpoint = deviceEndpoint;
        } else {
            String brokerUrl = mqttProperties.getBrokerUrl();
            endpoint = brokerUrl;
            if (brokerUrl.startsWith("tcp://")) {
                endpoint = brokerUrl.substring(6);
            } else if (brokerUrl.startsWith("ssl://")) {
                endpoint = brokerUrl.substring(6);
            }
        }
        mqttConfig.put("endpoint", endpoint);

        // 客户端ID，基于设备ID生成
        mqttConfig.put("client_id", deviceId);

        // 设备认证信息
        mqttConfig.put("username", mqttProperties.getDeviceUsername());
        mqttConfig.put("password", mqttProperties.getDevicePassword());

        // keepalive 间隔（秒），与 ESP32 默认值 240 对齐
        mqttConfig.put("keepalive", 240);

        // 设备发布的 topic（状态上报）
        mqttConfig.put("publish_topic", mqttService.buildStatusTopic(userId, deviceId));

        // 设备订阅的 topic（接收命令）
        mqttConfig.put("subscribe_topic", mqttService.buildCommandTopic(userId, deviceId));

        return mqttConfig;
    }
}

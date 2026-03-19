package com.xiaozhi.communication.mqtt;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 设备唤醒服务
 * 通过 MQTT 发送唤醒命令到设备，设备收到后建立 WebSocket 连接
 */
@Service
public class DeviceWakeupService {
    private static final Logger logger = LoggerFactory.getLogger(DeviceWakeupService.class);

    @Resource
    private MqttService mqttService;

    @Resource
    private MqttProperties mqttProperties;

    @Resource
    private SessionManager sessionManager;

    @Resource
    private SysDeviceService deviceService;

    /**
     * 唤醒指定设备
     * 流程：MQTT publish 唤醒命令 → Broker 转发 → 设备接收 → 设备建立 WebSocket 连接
     *
     * @param deviceId 设备ID
     * @param message  唤醒附带消息（可选）
     * @return 唤醒结果
     */
    public WakeupResult wakeupDevice(String deviceId, String message) {
        // 检查 MQTT 是否可用
        if (!mqttService.isConnected()) {
            logger.warn("MQTT 未连接，无法唤醒设备: {}", deviceId);
            return new WakeupResult(false, "MQTT 服务未启用或未连接");
        }

        // 检查设备是否已经在线（WebSocket 已连接）
        ChatSession existingSession = sessionManager.getSessionByDeviceId(deviceId);
        if (existingSession != null && existingSession.isOpen()) {
            logger.info("设备已在线，无需唤醒: {}", deviceId);
            return new WakeupResult(true, "设备已在线");
        }

        // 查询设备信息以获取用户ID
        SysDevice device = deviceService.selectDeviceById(deviceId);
        if (device == null) {
            logger.warn("设备不存在: {}", deviceId);
            return new WakeupResult(false, "设备不存在");
        }

        if (device.getUserId() == null) {
            logger.warn("设备未绑定用户: {}", deviceId);
            return new WakeupResult(false, "设备未绑定用户");
        }

        // 构建唤醒命令消息
        MqttMessage wakeupMessage = new MqttMessage()
                .setType("wakeup")
                .setTimestamp(System.currentTimeMillis());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "wakeup");
        payload.put("deviceId", deviceId);
        if (message != null && !message.isEmpty()) {
            payload.put("message", message);
        }
        wakeupMessage.setPayload(payload);

        // 发布到设备的命令 Topic
        String commandTopic = mqttService.buildCommandTopic(
                device.getUserId().toString(), deviceId);
        String messageJson = JsonUtil.toJson(wakeupMessage);

        mqttService.publish(commandTopic, messageJson, mqttProperties.getQos());
        logger.info("已发送唤醒命令 - DeviceId: {}, Topic: {}", deviceId, commandTopic);

        return new WakeupResult(true, "唤醒命令已发送");
    }

    /**
     * 向指定设备发送通知消息
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @param text     通知文本内容
     * @return 发送结果
     */
    public WakeupResult notifyDevice(int userId, String deviceId, String text) {
        if (!mqttService.isConnected()) {
            logger.warn("MQTT 未连接，无法发送通知: {}", deviceId);
            return new WakeupResult(false, "MQTT 服务未启用或未连接");
        }

        MqttMessage notifyMessage = new MqttMessage()
                .setType("notify")
                .setTimestamp(System.currentTimeMillis());

        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceId", deviceId);
        payload.put("text", text);
        notifyMessage.setPayload(payload);

        String notifyTopic = mqttService.buildNotifyTopic(String.valueOf(userId), deviceId);
        String messageJson = JsonUtil.toJson(notifyMessage);

        mqttService.publish(notifyTopic, messageJson, mqttProperties.getQos());
        logger.info("已发送通知消息 - DeviceId: {}, Topic: {}", deviceId, notifyTopic);

        return new WakeupResult(true, "通知消息已发送");
    }

    /**
     * 广播消息到所有设备
     *
     * @param text 广播文本内容
     * @return 发送结果
     */
    public WakeupResult broadcast(String text) {
        if (!mqttService.isConnected()) {
            logger.warn("MQTT 未连接，无法广播消息");
            return new WakeupResult(false, "MQTT 服务未启用或未连接");
        }

        MqttMessage broadcastMessage = new MqttMessage()
                .setType("broadcast")
                .setTimestamp(System.currentTimeMillis());

        Map<String, Object> payload = new HashMap<>();
        payload.put("text", text);
        broadcastMessage.setPayload(payload);

        String broadcastTopic = mqttProperties.getTopicPrefix() + "/server/broadcast";
        String messageJson = JsonUtil.toJson(broadcastMessage);

        mqttService.publish(broadcastTopic, messageJson, mqttProperties.getQos());
        logger.info("已发送广播消息 - Topic: {}", broadcastTopic);

        return new WakeupResult(true, "广播消息已发送");
    }

    /**
     * 唤醒结果
     */
    public record WakeupResult(boolean success, String message) {
    }
}

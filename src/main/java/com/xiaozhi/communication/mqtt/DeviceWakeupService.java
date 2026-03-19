package com.xiaozhi.communication.mqtt;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.service.SysDeviceService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    private SessionManager sessionManager;

    @Resource
    private SysDeviceService deviceService;

    /**
     * 唤醒指定设备
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

        // 构建唤醒命令并发布到设备的命令 Topic
        String commandTopic = mqttService.buildCommandTopic(
                device.getUserId().toString(), deviceId);
        String payload = "{\"type\":\"wakeup\",\"message\":\"" + (message != null ? message : "") + "\"}";
        mqttService.publish(commandTopic, payload, 1);
        logger.info("已发送唤醒命令 - DeviceId: {}, Topic: {}", deviceId, commandTopic);

        return new WakeupResult(true, "唤醒命令已发送");
    }

    /**
     * 唤醒结果
     */
    public record WakeupResult(boolean success, String message) {
    }
}

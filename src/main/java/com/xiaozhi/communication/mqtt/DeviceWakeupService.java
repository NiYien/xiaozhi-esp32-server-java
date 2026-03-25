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
import java.util.List;
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
     * 消息格式兼容 ESP32 固件：使用 system 类型 + wakeup command
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

        // 构建 ESP32 兼容的唤醒消息：system 类型 + wakeup command
        Map<String, Object> wakeupMsg = new HashMap<>();
        wakeupMsg.put("type", "system");
        wakeupMsg.put("command", "wakeup");
        if (message != null && !message.isEmpty()) {
            wakeupMsg.put("message", message);
        }

        // 统一发布到设备的 command topic
        String commandTopic = mqttService.buildCommandTopic(
                device.getUserId().toString(), deviceId);
        String messageJson = JsonUtil.toJson(wakeupMsg);

        // 唤醒命令使用 QoS 1 确保可靠送达
        mqttService.publish(commandTopic, messageJson, 1);
        logger.info("已发送唤醒命令 - DeviceId: {}, Topic: {}", deviceId, commandTopic);

        return new WakeupResult(true, "唤醒命令已发送");
    }

    /**
     * 向指定设备发送通知消息
     * 消息格式兼容 ESP32 固件：使用 alert 类型，设备收到后显示消息并播放 TTS
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

        // 构建 ESP32 兼容的通知消息：alert 类型
        Map<String, Object> alertMsg = new HashMap<>();
        alertMsg.put("type", "alert");
        alertMsg.put("status", "info");
        alertMsg.put("message", text);
        alertMsg.put("emotion", "neutral");

        // 统一发布到设备的 command topic（不再使用 notify topic）
        String commandTopic = mqttService.buildCommandTopic(String.valueOf(userId), deviceId);
        String messageJson = JsonUtil.toJson(alertMsg);

        // 通知消息使用 QoS 0（非关键消息，不需要 Broker 缓存）
        mqttService.publish(commandTopic, messageJson, 0);
        logger.info("已发送通知消息 - DeviceId: {}, Topic: {}", deviceId, commandTopic);

        return new WakeupResult(true, "通知消息已发送");
    }

    /**
     * 广播消息到所有设备
     * 消息格式兼容 ESP32 固件：使用 alert 类型
     *
     * @param text 广播文本内容
     * @return 发送结果
     */
    public WakeupResult broadcast(String text) {
        if (!mqttService.isConnected()) {
            logger.warn("MQTT 未连接，无法广播消息");
            return new WakeupResult(false, "MQTT 服务未启用或未连接");
        }

        // 构建 ESP32 兼容的广播消息：alert 类型
        Map<String, Object> alertMsg = new HashMap<>();
        alertMsg.put("type", "alert");
        alertMsg.put("status", "info");
        alertMsg.put("message", text);

        String broadcastTopic = mqttProperties.getTopicPrefix() + "/server/broadcast";
        String messageJson = JsonUtil.toJson(alertMsg);

        // 广播消息使用 QoS 0
        mqttService.publish(broadcastTopic, messageJson, 0);
        logger.info("已发送广播消息 - Topic: {}", broadcastTopic);

        return new WakeupResult(true, "广播消息已发送");
    }

    /**
     * 推送 OTA 更新通知到指定设备
     * 消息通过 command topic 发送，使用 system 类型 + ota_available command
     * 使用 QoS 1 确保消息可靠送达
     *
     * @param deviceId     设备ID
     * @param version      固件版本号
     * @param url          固件下载地址
     * @param releaseNotes 版本说明
     * @param force        是否强制升级
     * @return 推送结果
     */
    public WakeupResult pushOtaNotification(String deviceId, String version, String url,
                                             String releaseNotes, boolean force) {
        if (!mqttService.isConnected()) {
            return new WakeupResult(false, "MQTT 服务未启用或未连接");
        }

        SysDevice device = deviceService.selectDeviceById(deviceId);
        if (device == null) {
            return new WakeupResult(false, "设备不存在");
        }
        if (device.getUserId() == null) {
            return new WakeupResult(false, "设备未绑定用户");
        }

        // 构建 OTA 通知消息，通过 command topic 发送
        Map<String, Object> otaMsg = new HashMap<>();
        otaMsg.put("type", "system");
        otaMsg.put("command", "ota_available");
        Map<String, Object> payload = new HashMap<>();
        payload.put("version", version);
        payload.put("url", url);
        payload.put("releaseNotes", releaseNotes);
        payload.put("force", force);
        otaMsg.put("payload", payload);
        otaMsg.put("timestamp", System.currentTimeMillis());

        String commandTopic = mqttService.buildCommandTopic(
                device.getUserId().toString(), deviceId);
        String messageJson = JsonUtil.toJson(otaMsg);

        // OTA 通知使用 QoS 1 确保离线设备上线后收到
        mqttService.publish(commandTopic, messageJson, 1);
        logger.info("已推送 OTA 通知 - DeviceId: {}, Version: {}, Topic: {}", deviceId, version, commandTopic);

        return new WakeupResult(true, "OTA 通知已推送");
    }

    /**
     * 广播 OTA 更新通知到所有设备
     *
     * @param version      固件版本号
     * @param url          固件下载地址
     * @param releaseNotes 版本说明
     * @param force        是否强制升级
     * @return 推送结果
     */
    public WakeupResult broadcastOtaNotification(String version, String url,
                                                  String releaseNotes, boolean force) {
        if (!mqttService.isConnected()) {
            return new WakeupResult(false, "MQTT 服务未启用或未连接");
        }

        // 构建 OTA 通知广播消息
        Map<String, Object> otaMsg = new HashMap<>();
        otaMsg.put("type", "system");
        otaMsg.put("command", "ota_available");
        Map<String, Object> payload = new HashMap<>();
        payload.put("version", version);
        payload.put("url", url);
        payload.put("releaseNotes", releaseNotes);
        payload.put("force", force);
        otaMsg.put("payload", payload);
        otaMsg.put("timestamp", System.currentTimeMillis());

        String broadcastTopic = mqttProperties.getTopicPrefix() + "/server/broadcast";
        String messageJson = JsonUtil.toJson(otaMsg);

        mqttService.publish(broadcastTopic, messageJson, 1);
        logger.info("已广播 OTA 通知 - Version: {}, Topic: {}", version, broadcastTopic);

        return new WakeupResult(true, "OTA 广播通知已发送");
    }

    /**
     * 推送配置更新到指定设备
     * 消息通过 command topic 发送，使用 system 类型 + config_update command
     * 使用 QoS 1 确保消息可靠送达
     *
     * @param deviceId 设备ID
     * @param config   配置键值对
     * @return 推送结果
     */
    public WakeupResult pushConfig(String deviceId, Map<String, Object> config) {
        if (!mqttService.isConnected()) {
            return new WakeupResult(false, "MQTT 服务未启用或未连接");
        }

        SysDevice device = deviceService.selectDeviceById(deviceId);
        if (device == null) {
            return new WakeupResult(false, "设备不存在");
        }
        if (device.getUserId() == null) {
            return new WakeupResult(false, "设备未绑定用户");
        }

        // 构建配置下发消息
        Map<String, Object> configMsg = new HashMap<>();
        configMsg.put("type", "system");
        configMsg.put("command", "config_update");
        configMsg.put("payload", config);
        configMsg.put("timestamp", System.currentTimeMillis());

        String commandTopic = mqttService.buildCommandTopic(
                device.getUserId().toString(), deviceId);
        String messageJson = JsonUtil.toJson(configMsg);

        // 配置下发使用 QoS 1 确保消息可靠送达
        mqttService.publish(commandTopic, messageJson, 1);
        logger.info("已推送配置更新 - DeviceId: {}, Config: {}, Topic: {}", deviceId, config, commandTopic);

        return new WakeupResult(true, "配置已推送");
    }

    /**
     * 向分组发送唤醒命令
     * 使用分组 command topic，分组内所有订阅设备都会收到
     *
     * @param userId  用户ID
     * @param groupId 分组ID
     * @param message 唤醒附带消息（可选）
     * @return 发送结果
     */
    public WakeupResult wakeupGroup(int userId, int groupId, String message) {
        if (!mqttService.isConnected()) {
            return new WakeupResult(false, "MQTT 服务未启用或未连接");
        }

        Map<String, Object> wakeupMsg = new HashMap<>();
        wakeupMsg.put("type", "system");
        wakeupMsg.put("command", "wakeup");
        if (message != null && !message.isEmpty()) {
            wakeupMsg.put("message", message);
        }

        String groupTopic = mqttService.buildGroupCommandTopic(
                String.valueOf(userId), String.valueOf(groupId));
        String messageJson = JsonUtil.toJson(wakeupMsg);

        // 唤醒命令使用 QoS 1
        mqttService.publish(groupTopic, messageJson, 1);
        logger.info("已发送分组唤醒命令 - GroupId: {}, Topic: {}", groupId, groupTopic);

        return new WakeupResult(true, "分组唤醒命令已发送");
    }

    /**
     * 向分组发送通知消息
     *
     * @param userId  用户ID
     * @param groupId 分组ID
     * @param text    通知文本
     * @return 发送结果
     */
    public WakeupResult notifyGroup(int userId, int groupId, String text) {
        if (!mqttService.isConnected()) {
            return new WakeupResult(false, "MQTT 服务未启用或未连接");
        }

        Map<String, Object> alertMsg = new HashMap<>();
        alertMsg.put("type", "alert");
        alertMsg.put("status", "info");
        alertMsg.put("message", text);
        alertMsg.put("emotion", "neutral");

        // 统一使用 command topic，ESP32 设备只订阅 command topic
        String groupTopic = mqttService.buildGroupCommandTopic(
                String.valueOf(userId), String.valueOf(groupId));
        String messageJson = JsonUtil.toJson(alertMsg);

        // 通知消息使用 QoS 0
        mqttService.publish(groupTopic, messageJson, 0);
        logger.info("已发送分组通知 - GroupId: {}, Topic: {}", groupId, groupTopic);

        return new WakeupResult(true, "分组通知已发送");
    }

    /**
     * 向分组内所有设备逐个推送配置
     *
     * @param deviceIds 分组内设备ID列表
     * @param config    配置键值对
     * @return 推送结果
     */
    public WakeupResult pushConfigToDevices(List<String> deviceIds, Map<String, Object> config) {
        if (!mqttService.isConnected()) {
            return new WakeupResult(false, "MQTT 服务未启用或未连接");
        }

        int successCount = 0;
        for (String deviceId : deviceIds) {
            WakeupResult result = pushConfig(deviceId, config);
            if (result.success()) {
                successCount++;
            }
        }

        return new WakeupResult(true,
                "配置已推送到 " + successCount + "/" + deviceIds.size() + " 个设备");
    }

    /**
     * 唤醒结果
     */
    public record WakeupResult(boolean success, String message) {
    }
}

package com.xiaozhi.controller;

import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.mqtt.DeviceWakeupService;
import com.xiaozhi.communication.mqtt.MqttDeviceStatusListener;
import com.xiaozhi.communication.mqtt.MqttProperties;
import com.xiaozhi.communication.mqtt.MqttService;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysSensorData;
import com.xiaozhi.service.SensorDataService;
import com.xiaozhi.service.SysDeviceGroupService;
import com.xiaozhi.service.SysDeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import cn.dev33.satoken.stp.StpUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * MQTT 管理控制器
 * 提供设备唤醒、OTA 推送、传感器查询、分组控制、配置下发等接口
 */
@RestController
@RequestMapping("/api/mqtt")
@Tag(name = "MQTT管理", description = "MQTT 相关操作")
public class MqttController extends BaseController {

    @Resource
    private MqttService mqttService;

    @Resource
    private MqttProperties mqttProperties;

    @Resource
    private DeviceWakeupService deviceWakeupService;

    @Resource
    private SessionManager sessionManager;

    @Resource
    private SysDeviceService deviceService;

    @Resource
    private SysDeviceGroupService deviceGroupService;

    /**
     * MqttDeviceStatusListener 仅在 MQTT 启用时才存在，因此使用 required=false
     */
    @Autowired(required = false)
    private MqttDeviceStatusListener mqttDeviceStatusListener;

    /**
     * SensorDataService 仅在 MQTT 启用时才存在
     */
    @Autowired(required = false)
    private SensorDataService sensorDataService;

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    // ========== 基础状态查询 ==========

    /**
     * 查询 MQTT 连接状态
     */
    @GetMapping("/status")
    @Operation(summary = "查询MQTT连接状态", description = "返回当前MQTT Broker连接状态")
    public ResultMessage status() {
        try {
            boolean connected = mqttService.isConnected();
            ResultMessage result = ResultMessage.success();
            result.put("data", Map.of("connected", connected));
            return result;
        } catch (Exception e) {
            logger.error("查询MQTT状态失败", e);
            return ResultMessage.error("查询MQTT状态失败");
        }
    }

    /**
     * 查询 MQTT 配置信息（脱敏）
     */
    @GetMapping("/config")
    @Operation(summary = "查询MQTT配置", description = "返回MQTT配置的非敏感信息和当前连接状态")
    public ResultMessage config() {
        try {
            Map<String, Object> configInfo = new LinkedHashMap<>();
            configInfo.put("enabled", mqttProperties.isEnabled());
            configInfo.put("brokerUrl", mqttProperties.getBrokerUrl());
            configInfo.put("topicPrefix", mqttProperties.getTopicPrefix());
            configInfo.put("connected", mqttService.isConnected());
            configInfo.put("clientId", mqttService.getClientId());
            configInfo.put("keepAliveInterval", mqttProperties.getKeepAliveInterval());

            ResultMessage result = ResultMessage.success();
            result.put("data", configInfo);
            return result;
        } catch (Exception e) {
            logger.error("查询MQTT配置失败", e);
            return ResultMessage.error("查询MQTT配置失败");
        }
    }

    /**
     * 查询当前用户所有设备的 MQTT 状态
     */
    @GetMapping("/devices")
    @Operation(summary = "查询设备MQTT状态", description = "返回当前用户所有设备的MQTT状态信息")
    public ResultMessage devices() {
        try {
            int userId = StpUtil.getLoginIdAsInt();

            SysDevice queryParam = new SysDevice();
            queryParam.setUserId(userId);
            List<SysDevice> deviceList = deviceService.query(queryParam, null);

            Map<String, Instant> heartbeatMap = mqttDeviceStatusListener != null
                    ? mqttDeviceStatusListener.getAllLastHeartbeats()
                    : Collections.emptyMap();

            // 获取所有设备的最新传感器数据
            List<String> deviceIds = deviceList.stream().map(SysDevice::getDeviceId).toList();
            Map<String, SysSensorData> sensorMap = Collections.emptyMap();
            if (sensorDataService != null && !deviceIds.isEmpty()) {
                sensorMap = sensorDataService.getLatestByDeviceIds(deviceIds);
            }

            List<Map<String, Object>> deviceStatusList = new ArrayList<>();
            for (SysDevice device : deviceList) {
                String deviceId = device.getDeviceId();
                Map<String, Object> statusInfo = new LinkedHashMap<>();
                statusInfo.put("deviceId", deviceId);
                statusInfo.put("deviceName", device.getDeviceName());
                statusInfo.put("state", sessionManager.getDeviceState(deviceId));
                statusInfo.put("channels", sessionManager.getOnlineChannels(deviceId));

                Instant lastHeartbeat = heartbeatMap.get(deviceId);
                statusInfo.put("lastHeartbeat", lastHeartbeat != null
                        ? DATETIME_FORMATTER.format(lastHeartbeat)
                        : null);

                // 填充传感器数据
                SysSensorData sensor = sensorMap.get(deviceId);
                if (sensor != null) {
                    statusInfo.put("temperature", sensor.getTemperature());
                    statusInfo.put("battery", sensor.getBattery());
                    statusInfo.put("freeHeap", sensor.getFreeHeap());
                    statusInfo.put("wifiRssi", sensor.getWifiRssi());
                }

                deviceStatusList.add(statusInfo);
            }

            ResultMessage result = ResultMessage.success();
            result.put("data", deviceStatusList);
            return result;
        } catch (Exception e) {
            logger.error("查询设备MQTT状态失败", e);
            return ResultMessage.error("查询设备MQTT状态失败");
        }
    }

    // ========== 设备唤醒与通知 ==========

    /**
     * 唤醒指定设备
     */
    @PostMapping("/wakeup/{deviceId}")
    @Operation(summary = "唤醒设备", description = "通过MQTT发送唤醒命令到指定设备")
    public ResultMessage wakeupDevice(@PathVariable String deviceId,
                                      @RequestParam(required = false) String message) {
        try {
            var wakeupResult = deviceWakeupService.wakeupDevice(deviceId, message);
            if (wakeupResult.success()) {
                return ResultMessage.success(wakeupResult.message());
            } else {
                return ResultMessage.error(wakeupResult.message());
            }
        } catch (Exception e) {
            logger.error("唤醒设备失败 - DeviceId: {}", deviceId, e);
            return ResultMessage.error("唤醒设备失败: " + e.getMessage());
        }
    }

    /**
     * 向指定设备发送通知消息
     */
    @PostMapping("/notify/{deviceId}")
    @Operation(summary = "发送通知", description = "通过MQTT向指定设备发送通知消息")
    public ResultMessage notifyDevice(@PathVariable String deviceId,
                                      @RequestParam String text) {
        try {
            int userId = StpUtil.getLoginIdAsInt();
            var result = deviceWakeupService.notifyDevice(userId, deviceId, text);
            if (result.success()) {
                return ResultMessage.success(result.message());
            } else {
                return ResultMessage.error(result.message());
            }
        } catch (Exception e) {
            logger.error("发送通知失败 - DeviceId: {}", deviceId, e);
            return ResultMessage.error("发送通知失败: " + e.getMessage());
        }
    }

    /**
     * 广播消息到所有设备
     */
    @PostMapping("/broadcast")
    @Operation(summary = "广播消息", description = "通过MQTT向所有设备广播消息")
    public ResultMessage broadcast(@RequestParam String text) {
        try {
            var result = deviceWakeupService.broadcast(text);
            if (result.success()) {
                return ResultMessage.success(result.message());
            } else {
                return ResultMessage.error(result.message());
            }
        } catch (Exception e) {
            logger.error("广播消息失败", e);
            return ResultMessage.error("广播消息失败: " + e.getMessage());
        }
    }

    // ========== OTA 推送 ==========

    /**
     * 向指定设备推送 OTA 更新通知
     */
    @PostMapping("/ota/{deviceId}")
    @Operation(summary = "推送OTA通知", description = "通过MQTT向指定设备推送OTA更新通知")
    public ResultMessage pushOta(@PathVariable String deviceId,
                                  @RequestParam String version,
                                  @RequestParam String url,
                                  @RequestParam(required = false, defaultValue = "") String releaseNotes,
                                  @RequestParam(required = false, defaultValue = "false") boolean force) {
        try {
            var result = deviceWakeupService.pushOtaNotification(deviceId, version, url, releaseNotes, force);
            if (result.success()) {
                return ResultMessage.success(result.message());
            } else {
                return ResultMessage.error(result.message());
            }
        } catch (Exception e) {
            logger.error("推送OTA通知失败 - DeviceId: {}", deviceId, e);
            return ResultMessage.error("推送OTA通知失败: " + e.getMessage());
        }
    }

    /**
     * 向所有设备广播 OTA 更新通知
     */
    @PostMapping("/ota/broadcast")
    @Operation(summary = "广播OTA通知", description = "通过MQTT向所有设备广播OTA更新通知")
    public ResultMessage broadcastOta(@RequestParam String version,
                                      @RequestParam String url,
                                      @RequestParam(required = false, defaultValue = "") String releaseNotes,
                                      @RequestParam(required = false, defaultValue = "false") boolean force) {
        try {
            var result = deviceWakeupService.broadcastOtaNotification(version, url, releaseNotes, force);
            if (result.success()) {
                return ResultMessage.success(result.message());
            } else {
                return ResultMessage.error(result.message());
            }
        } catch (Exception e) {
            logger.error("广播OTA通知失败", e);
            return ResultMessage.error("广播OTA通知失败: " + e.getMessage());
        }
    }

    // ========== 传感器数据查询 ==========

    /**
     * 查询设备最新传感器数据
     */
    @GetMapping("/sensor/{deviceId}/latest")
    @Operation(summary = "查询设备最新传感器数据", description = "返回设备最近一条传感器数据")
    public ResultMessage sensorLatest(@PathVariable String deviceId) {
        try {
            if (sensorDataService == null) {
                return ResultMessage.error("MQTT 未启用，传感器服务不可用");
            }
            SysSensorData data = sensorDataService.getLatest(deviceId);
            if (data == null) {
                return ResultMessage.success("无传感器数据", null);
            }
            ResultMessage result = ResultMessage.success();
            result.put("data", data);
            return result;
        } catch (Exception e) {
            logger.error("查询传感器数据失败 - DeviceId: {}", deviceId, e);
            return ResultMessage.error("查询传感器数据失败");
        }
    }

    /**
     * 查询设备历史传感器数据
     */
    @GetMapping("/sensor/{deviceId}")
    @Operation(summary = "查询设备历史传感器数据", description = "返回时间范围内的传感器数据列表")
    public ResultMessage sensorHistory(@PathVariable String deviceId,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime) {
        try {
            if (sensorDataService == null) {
                return ResultMessage.error("MQTT 未启用，传感器服务不可用");
            }
            List<SysSensorData> list = sensorDataService.getHistory(deviceId, startTime, endTime);
            ResultMessage result = ResultMessage.success();
            result.put("data", list);
            return result;
        } catch (Exception e) {
            logger.error("查询传感器历史数据失败 - DeviceId: {}", deviceId, e);
            return ResultMessage.error("查询传感器历史数据失败");
        }
    }

    // ========== 分组控制 ==========

    /**
     * 向分组发送唤醒命令
     */
    @PostMapping("/group/{groupId}/wakeup")
    @Operation(summary = "分组唤醒", description = "通过MQTT向分组内所有设备发送唤醒命令")
    public ResultMessage wakeupGroup(@PathVariable Integer groupId,
                                     @RequestParam(required = false) String message) {
        try {
            int userId = StpUtil.getLoginIdAsInt();
            // 验证分组归属
            var group = deviceGroupService.selectById(groupId);
            if (group == null || !group.getUserId().equals(userId)) {
                return ResultMessage.error("分组不存在或无权操作");
            }
            var result = deviceWakeupService.wakeupGroup(userId, groupId, message);
            if (result.success()) {
                return ResultMessage.success(result.message());
            } else {
                return ResultMessage.error(result.message());
            }
        } catch (Exception e) {
            logger.error("分组唤醒失败 - GroupId: {}", groupId, e);
            return ResultMessage.error("分组唤醒失败: " + e.getMessage());
        }
    }

    /**
     * 向分组发送通知消息
     */
    @PostMapping("/group/{groupId}/notify")
    @Operation(summary = "分组通知", description = "通过MQTT向分组内所有设备发送通知消息")
    public ResultMessage notifyGroup(@PathVariable Integer groupId,
                                     @RequestParam String text) {
        try {
            int userId = StpUtil.getLoginIdAsInt();
            var group = deviceGroupService.selectById(groupId);
            if (group == null || !group.getUserId().equals(userId)) {
                return ResultMessage.error("分组不存在或无权操作");
            }
            var result = deviceWakeupService.notifyGroup(userId, groupId, text);
            if (result.success()) {
                return ResultMessage.success(result.message());
            } else {
                return ResultMessage.error(result.message());
            }
        } catch (Exception e) {
            logger.error("分组通知失败 - GroupId: {}", groupId, e);
            return ResultMessage.error("分组通知失败: " + e.getMessage());
        }
    }

    // ========== 远程配置下发 ==========

    /**
     * 允许下发的配置项白名单
     */
    private static final Set<String> CONFIG_WHITELIST = Set.of(
            "volume", "wakeWord", "ledBrightness", "timezone", "language"
    );

    /**
     * 向指定设备推送配置
     */
    @PostMapping("/config/{deviceId}")
    @Operation(summary = "推送设备配置", description = "通过MQTT向指定设备推送配置变更")
    public ResultMessage pushConfig(@PathVariable String deviceId,
                                     @RequestBody Map<String, Object> config) {
        try {
            // 校验配置项白名单
            String validationError = validateConfigKeys(config);
            if (validationError != null) {
                return ResultMessage.error(validationError);
            }

            var result = deviceWakeupService.pushConfig(deviceId, config);
            if (result.success()) {
                return ResultMessage.success(result.message());
            } else {
                return ResultMessage.error(result.message());
            }
        } catch (Exception e) {
            logger.error("推送配置失败 - DeviceId: {}", deviceId, e);
            return ResultMessage.error("推送配置失败: " + e.getMessage());
        }
    }

    /**
     * 向分组内所有设备推送配置
     */
    @PostMapping("/config/group/{groupId}")
    @Operation(summary = "推送分组配置", description = "通过MQTT向分组内所有设备推送配置变更")
    public ResultMessage pushConfigToGroup(@PathVariable Integer groupId,
                                            @RequestBody Map<String, Object> config) {
        try {
            int userId = StpUtil.getLoginIdAsInt();
            var group = deviceGroupService.selectById(groupId);
            if (group == null || !group.getUserId().equals(userId)) {
                return ResultMessage.error("分组不存在或无权操作");
            }

            // 校验配置项白名单
            String validationError = validateConfigKeys(config);
            if (validationError != null) {
                return ResultMessage.error(validationError);
            }

            List<String> deviceIds = deviceGroupService.getDeviceIds(groupId);
            if (deviceIds.isEmpty()) {
                return ResultMessage.error("分组内无设备");
            }

            var result = deviceWakeupService.pushConfigToDevices(deviceIds, config);
            if (result.success()) {
                return ResultMessage.success(result.message());
            } else {
                return ResultMessage.error(result.message());
            }
        } catch (Exception e) {
            logger.error("推送分组配置失败 - GroupId: {}", groupId, e);
            return ResultMessage.error("推送分组配置失败: " + e.getMessage());
        }
    }

    /**
     * 校验配置项是否在白名单内
     *
     * @param config 配置键值对
     * @return 错误信息，无错误返回 null
     */
    private String validateConfigKeys(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return "配置不能为空";
        }
        List<String> invalidKeys = config.keySet().stream()
                .filter(key -> !CONFIG_WHITELIST.contains(key))
                .toList();
        if (!invalidKeys.isEmpty()) {
            return "不支持的配置项: " + invalidKeys + "，支持的配置项: " + CONFIG_WHITELIST;
        }
        // 校验 volume 范围
        if (config.containsKey("volume")) {
            Object vol = config.get("volume");
            if (vol instanceof Number num) {
                int v = num.intValue();
                if (v < 0 || v > 100) {
                    return "volume 取值范围为 0-100";
                }
            }
        }
        // 校验 ledBrightness 范围
        if (config.containsKey("ledBrightness")) {
            Object brightness = config.get("ledBrightness");
            if (brightness instanceof Number num) {
                int b = num.intValue();
                if (b < 0 || b > 100) {
                    return "ledBrightness 取值范围为 0-100";
                }
            }
        }
        return null;
    }
}

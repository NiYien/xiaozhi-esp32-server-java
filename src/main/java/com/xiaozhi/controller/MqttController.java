package com.xiaozhi.controller;

import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.communication.mqtt.DeviceWakeupService;
import com.xiaozhi.communication.mqtt.MqttService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MQTT 管理控制器
 * 提供设备唤醒、MQTT 状态查询等接口
 */
@RestController
@RequestMapping("/api/mqtt")
@Tag(name = "MQTT管理", description = "MQTT 相关操作")
public class MqttController extends BaseController {

    @Resource
    private MqttService mqttService;

    @Resource
    private DeviceWakeupService deviceWakeupService;

    /**
     * 查询 MQTT 连接状态
     *
     * @return MQTT 连接状态信息
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
     * 唤醒指定设备
     *
     * @param deviceId 设备ID
     * @return 唤醒结果
     */
    @PostMapping("/wakeup/{deviceId}")
    @Operation(summary = "唤醒设备", description = "通过MQTT发送唤醒命令到指定设备")
    public ResultMessage wakeupDevice(@PathVariable String deviceId) {
        try {
            var wakeupResult = deviceWakeupService.wakeupDevice(deviceId);
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
}

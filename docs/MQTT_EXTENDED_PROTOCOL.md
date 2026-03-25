# MQTT 扩展协议文档

本文档定义了服务端 MQTT 扩展功能的 Topic 结构、消息格式和 ESP32 固件改动说明。

## Topic 结构

### 现有 Topic

| Topic | 方向 | 说明 |
|-------|------|------|
| `xiaozhi/{userId}/device/{deviceId}/command` | 服务端 -> 设备 | 命令下发（唤醒、OTA、配置等） |
| `xiaozhi/{userId}/device/{deviceId}/status` | 设备 -> 服务端 | 状态上报（上线、离线、心跳） |
| `xiaozhi/server/broadcast` | 服务端 -> 所有设备 | 广播消息 |

### 新增 Topic

| Topic | 方向 | 说明 |
|-------|------|------|
| `xiaozhi/{userId}/device/{deviceId}/sensor` | 设备 -> 服务端 | 传感器数据上报 |
| `xiaozhi/{userId}/group/{groupId}/command` | 服务端 -> 分组设备 | 分组命令下发 |
| `xiaozhi/{userId}/group/{groupId}/notify` | 服务端 -> 分组设备 | 分组通知 |

> **设计决策**：所有发给设备的消息统一走 `command` topic，通过 `type` + `command` 字段区分消息类型。OTA 通知和配置下发不使用独立 topic。

## 消息格式

### 1. 唤醒命令（command topic）

```json
{
  "type": "system",
  "command": "wakeup",
  "message": "可选的唤醒消息"
}
```

QoS: 1（确保离线设备上线后收到）

### 2. 通知消息（command topic）

```json
{
  "type": "alert",
  "status": "info",
  "message": "通知文本内容",
  "emotion": "neutral"
}
```

QoS: 0（非关键消息）

### 3. OTA 更新通知（command topic）

```json
{
  "type": "system",
  "command": "ota_available",
  "timestamp": 1710000000000,
  "payload": {
    "version": "2.2.5",
    "url": "http://server/firmware/v2.2.5.bin",
    "releaseNotes": "修复若干问题",
    "force": false
  }
}
```

QoS: 1

**ESP32 固件处理流程**：
1. 收到消息后解析 `payload.version`，与当前固件版本比较
2. 如果 `force=true` 或版本更高，调用现有 `Ota::Upgrade(url)` 方法
3. 固件已有 OTA 能力（`ota.cc`），只需新增消息解析逻辑

### 4. 配置下发（command topic）

```json
{
  "type": "system",
  "command": "config_update",
  "timestamp": 1710000000000,
  "payload": {
    "volume": 80,
    "wakeWord": "你好小智",
    "ledBrightness": 50,
    "timezone": "Asia/Shanghai",
    "language": "zh-CN"
  }
}
```

QoS: 1

**支持的配置项**：

| 配置项 | 类型 | 范围 | 说明 |
|--------|------|------|------|
| `volume` | int | 0-100 | 音量 |
| `wakeWord` | string | - | 唤醒词 |
| `ledBrightness` | int | 0-100 | LED 亮度 |
| `timezone` | string | - | 时区 |
| `language` | string | - | 语言 |

**ESP32 固件处理流程**：
1. 收到消息后解析 `payload` 中的配置项
2. 将配置写入对应的 NVS 命名空间（已有 `Settings` 类支持）
3. 立即应用配置（如调整音量、更新唤醒词）

### 5. 传感器数据上报（sensor topic）

```json
{
  "type": "sensor",
  "timestamp": 1710000000000,
  "payload": {
    "temperature": 42.5,
    "battery": 85,
    "freeHeap": 120000,
    "wifiRssi": -45,
    "uptime": 86400
  }
}
```

QoS: 0

**传感器数据字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `temperature` | float | 芯片温度（摄氏度） |
| `battery` | int | 电量百分比（0-100） |
| `freeHeap` | int | 剩余堆内存（字节） |
| `wifiRssi` | int | WiFi 信号强度（dBm） |
| `uptime` | long | 运行时长（秒） |

**ESP32 固件改动**：
- 新增定时任务（建议每 5 分钟）
- 读取数据：`Board::GetBatteryLevel()`、`Board::GetTemperature()`、`SystemInfo::GetFreeHeapSize()`、WiFi RSSI
- 通过 MQTT 发布到 `{publish_topic 替换 status 为 sensor}` 或使用独立配置的 sensor topic

### 6. 分组命令（group command topic）

消息格式与设备 command topic 相同（唤醒、通知等），只是发到分组 topic。

**ESP32 固件改动**：
- 设备启动时除了订阅自己的 command topic，还需额外订阅分组 topic
- 分组信息可通过 OTA CheckVersion 接口下发（在 MQTT 配置中增加 `group_topics` 字段）
- 建议在 `MqttConfigGenerator` 中新增分组 topic 列表下发

## QoS 策略

| 消息类型 | QoS | 说明 |
|----------|-----|------|
| 唤醒命令 | 1 | 关键命令，离线缓存 |
| OTA 通知 | 1 | 关键命令，离线缓存 |
| 配置下发 | 1 | 关键命令，离线缓存 |
| 通知消息 | 0 | 实时通知，不缓存 |
| 广播消息 | 0 | 实时通知，不缓存 |
| 传感器上报 | 0 | 设备端发送，丢失可接受 |

**离线消息要求**：设备连接 MQTT Broker 时应使用 `cleanSession=false`，确保 QoS 1 消息在离线期间由 Broker 缓存。

## ESP32 固件改动总结

### 必须改动

| 功能 | 改动量 | 说明 |
|------|--------|------|
| 传感器数据上报 | 中 | 新增定时任务，读取传感器数据并通过 MQTT 发布 |
| 配置消息解析 | 中 | 在 MQTT 消息处理中新增 `config_update` command 分支，写入 NVS |
| OTA 消息解析 | 小 | 在 MQTT 消息处理中新增 `ota_available` command 分支，调用现有 OTA 方法 |
| 分组 topic 订阅 | 小 | 启动时额外订阅分组 topic（需服务端下发分组信息） |

### 无需改动

| 功能 | 说明 |
|------|------|
| 离线消息队列 | MQTT 协议原生支持，确保 `cleanSession=false` 即可 |
| 唤醒/通知消息格式 | 已兼容现有 `system`/`alert` 类型 |

### 实现建议

1. **传感器上报**：在 `mqtt_protocol.cc` 中新增 `ReportSensorData()` 方法，使用 FreeRTOS 定时器每 300 秒调用一次
2. **配置解析**：在 MQTT 消息回调中检查 `type=="system" && command=="config_update"`，遍历 payload 写入 NVS
3. **OTA 解析**：在 MQTT 消息回调中检查 `type=="system" && command=="ota_available"`，比较版本号后调用 `Ota::Upgrade()`
4. **分组订阅**：在 OTA 接口响应中新增 `subscribe_topics` 数组字段，设备启动时订阅所有 topic

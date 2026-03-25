/**
 * MQTT 配置信息（脱敏）
 */
export interface MqttConfig {
  enabled: boolean
  brokerUrl: string
  topicPrefix: string
  connected: boolean
  clientId: string
  keepAliveInterval: number
}

/**
 * MQTT 设备状态信息
 */
export interface MqttDeviceStatus {
  deviceId: string
  deviceName: string
  /** 设备三态：online / standby / offline */
  state: 'online' | 'standby' | 'offline'
  /** 在线通道：WebSocket / MQTT / WebSocket+MQTT / 离线 */
  channels: string
  /** 最后心跳时间，可能为 null */
  lastHeartbeat: string | null
  /** 温度（摄氏度） */
  temperature?: number | null
  /** 电量百分比 */
  battery?: number | null
  /** 剩余堆内存（字节） */
  freeHeap?: number | null
  /** WiFi信号强度（dBm） */
  wifiRssi?: number | null
}

/**
 * 传感器数据
 */
export interface SensorData {
  id: number
  deviceId: string
  temperature: number | null
  battery: number | null
  freeHeap: number | null
  wifiRssi: number | null
  uptime: number | null
  createTime: string
}

/**
 * 设备分组
 */
export interface DeviceGroup {
  groupId: number
  groupName: string
  description: string | null
  state: string
  deviceCount: number
  createTime: string
  updateTime: string
}

/**
 * MQTT 发送记录
 */
export interface MqttSendLog {
  time: string
  type: string
  target: string
  message: string
  success: boolean
}

/**
 * 远程配置项
 */
export interface DeviceConfig {
  volume?: number
  wakeWord?: string
  ledBrightness?: number
  timezone?: string
  language?: string
}

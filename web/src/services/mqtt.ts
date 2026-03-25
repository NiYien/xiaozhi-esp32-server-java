import request, { http } from './request'
import type { MqttConfig, MqttDeviceStatus, SensorData, DeviceGroup, DeviceConfig } from '@/types/mqtt'

/**
 * 获取 MQTT 配置信息（脱敏）
 */
export function getMqttConfig() {
  return http.get<MqttConfig>('/mqtt/config')
}

/**
 * 获取当前用户所有设备的 MQTT 状态
 */
export function getMqttDevices() {
  return http.get<MqttDeviceStatus[]>('/mqtt/devices')
}

/**
 * 唤醒指定设备
 */
export function wakeupDevice(deviceId: string, message?: string) {
  const params: Record<string, unknown> = {}
  if (message) {
    params.message = message
  }
  return http.post(`/api/mqtt/wakeup/${deviceId}`, params)
}

/**
 * 向指定设备发送通知
 */
export function notifyDevice(deviceId: string, text: string) {
  return http.post(`/api/mqtt/notify/${deviceId}`, { text })
}

/**
 * 广播消息到所有设备
 */
export function broadcast(text: string) {
  return http.post('/mqtt/broadcast', { text })
}

// ========== OTA 推送 ==========

/**
 * 向指定设备推送 OTA 通知
 */
export function pushOta(deviceId: string, version: string, url: string, releaseNotes?: string, force?: boolean) {
  return http.post(`/api/mqtt/ota/${deviceId}`, {
    version,
    url,
    releaseNotes: releaseNotes || '',
    force: force || false,
  })
}

/**
 * 向所有设备广播 OTA 通知
 */
export function broadcastOta(version: string, url: string, releaseNotes?: string, force?: boolean) {
  return http.post('/mqtt/ota/broadcast', {
    version,
    url,
    releaseNotes: releaseNotes || '',
    force: force || false,
  })
}

// ========== 传感器数据 ==========

/**
 * 查询设备最新传感器数据
 */
export function getSensorLatest(deviceId: string) {
  return http.get<SensorData>(`/api/mqtt/sensor/${deviceId}/latest`)
}

/**
 * 查询设备历史传感器数据
 */
export function getSensorHistory(deviceId: string, startTime?: string, endTime?: string) {
  const params: Record<string, string> = {}
  if (startTime) params.startTime = startTime
  if (endTime) params.endTime = endTime
  return http.get<SensorData[]>(`/api/mqtt/sensor/${deviceId}`, params)
}

// ========== 设备分组 ==========

/**
 * 查询分组列表
 */
export function getDeviceGroups() {
  return http.get<DeviceGroup[]>('/deviceGroup')
}

/**
 * 创建分组
 */
export function createDeviceGroup(groupName: string, description?: string) {
  return http.postJSON('/deviceGroup', { groupName, description })
}

/**
 * 更新分组
 */
export function updateDeviceGroup(groupId: number, data: { groupName?: string; description?: string }) {
  return http.putJSON(`/api/deviceGroup/${groupId}`, data)
}

/**
 * 删除分组
 */
export function deleteDeviceGroup(groupId: number) {
  return http.delete(`/api/deviceGroup/${groupId}`)
}

/**
 * 获取分组内设备ID列表
 */
export function getGroupDevices(groupId: number) {
  return http.get<string[]>(`/api/deviceGroup/${groupId}/devices`)
}

/**
 * 批量添加设备到分组
 */
export function addGroupMembers(groupId: number, deviceIds: string[]) {
  return request.post(`/api/deviceGroup/${groupId}/members`, deviceIds, {
    headers: { 'Content-Type': 'application/json;charset=UTF-8' },
  })
}

/**
 * 从分组中移除设备
 */
export function removeGroupMember(groupId: number, deviceId: string) {
  return http.delete(`/api/deviceGroup/${groupId}/member/${deviceId}`)
}

/**
 * 分组唤醒
 */
export function wakeupGroup(groupId: number, message?: string) {
  const params: Record<string, unknown> = {}
  if (message) params.message = message
  return http.post(`/api/mqtt/group/${groupId}/wakeup`, params)
}

/**
 * 分组通知
 */
export function notifyGroup(groupId: number, text: string) {
  return http.post(`/api/mqtt/group/${groupId}/notify`, { text })
}

// ========== 远程配置下发 ==========

/**
 * 向设备推送配置
 */
export function pushDeviceConfig(deviceId: string, config: DeviceConfig) {
  return http.post(`/api/mqtt/config/${deviceId}`, config)
}

/**
 * 向分组推送配置
 */
export function pushGroupConfig(groupId: number, config: DeviceConfig) {
  return http.post(`/api/mqtt/config/group/${groupId}`, config)
}

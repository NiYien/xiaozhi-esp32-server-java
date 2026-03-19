import { http } from './request'
import api from './api'
import type { Firmware, FirmwareQueryParams } from '@/types/firmware'

/**
 * 查询固件列表
 */
export function queryFirmwares(params: Partial<FirmwareQueryParams>) {
  return http.getPage<Firmware>(api.firmware.query, params)
}

/**
 * 删除固件
 */
export function deleteFirmware(firmwareId: number) {
  return http.delete(`${api.firmware.delete}/${firmwareId}`)
}

/**
 * 更新固件信息
 */
export function updateFirmware(firmwareId: number, data: Partial<Firmware>) {
  return http.put(`${api.firmware.update}/${firmwareId}`, data as Record<string, unknown>)
}

/**
 * 设置默认固件
 */
export function setDefaultFirmware(firmwareId: number) {
  return http.put(`${api.firmware.setDefault}/${firmwareId}/default`)
}

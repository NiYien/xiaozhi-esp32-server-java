import { http } from './request'
import api from './api'
import type { SysVoiceprint, VoiceprintStatus } from '@/types/voiceprint'

/**
 * 查询声纹列表
 */
export function queryVoiceprints() {
  return http.get<SysVoiceprint[]>(api.voiceprint.list)
}

/**
 * 注册声纹
 */
export function registerVoiceprint(deviceId: string, name: string, audioFile: File) {
  const formData = new FormData()
  formData.append('deviceId', deviceId)
  formData.append('name', name)
  formData.append('audio', audioFile)
  return http.upload(api.voiceprint.register, formData)
}

/**
 * 删除声纹
 */
export function deleteVoiceprint(voiceprintId: number) {
  return http.delete(`${api.voiceprint.delete}/${voiceprintId}`)
}

/**
 * 查询声纹功能状态
 */
export function getVoiceprintStatus() {
  return http.get<VoiceprintStatus>(api.voiceprint.status)
}

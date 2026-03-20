/**
 * 声纹信息接口
 */
export interface SysVoiceprint {
  voiceprintId: number
  voiceprintName: string
  deviceId: string
  sampleCount: number
  createTime: string
}

/**
 * 声纹功能状态接口
 */
export interface VoiceprintStatus {
  enabled: boolean
  embeddingDim: number
  minSpeechDuration: number
  voiceprintCount: number
}

import { http } from './request'
import request from './request'
import { useUserStore } from '@/store/user'
import type { DataResponse, ListResponse } from '@/types/api'

/**
 * 音色克隆接口
 */
export interface VoiceClone {
  cloneId: number
  userId: number
  cloneName: string
  provider: string
  configId: number
  taskId: string
  voiceId: string
  samplePath: string
  status: 'uploading' | 'training' | 'ready' | 'failed'
  errorMessage: string
  createTime: string
  updateTime: string
}

const API_PREFIX = '/api/voice-clone'

/**
 * 查询音色克隆列表
 */
export function queryVoiceClones(): Promise<DataResponse<VoiceClone[]>> {
  return http.get<VoiceClone[]>(`${API_PREFIX}/list`)
}

/**
 * 上传音频并提交训练
 */
export function uploadVoiceClone(
  file: File,
  cloneName: string,
  provider: string,
  configId: number,
  onProgress?: (percent: number) => void
): Promise<DataResponse<VoiceClone>> {
  return new Promise((resolve, reject) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('cloneName', cloneName)
    formData.append('provider', provider)
    formData.append('configId', String(configId))

    const xhr = new XMLHttpRequest()
    // 使用完整的 API URL，确保与 VITE_API_BASE_URL 前缀一致
    const baseURL = import.meta.env.VITE_API_BASE_URL || ''
    const uploadUrl = baseURL ? `${baseURL}/voice-clone/upload` : `${API_PREFIX}/upload`
    xhr.open('POST', uploadUrl, true)

    // 添加认证 token
    const userStore = useUserStore()
    if (userStore.token) {
      xhr.setRequestHeader('Authorization', `Bearer ${userStore.token}`)
    }

    if (onProgress) {
      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable) {
          const percent = Math.round((event.loaded / event.total) * 100)
          onProgress(percent)
        }
      }
    }

    xhr.onload = function () {
      if (xhr.status === 200) {
        try {
          const response = JSON.parse(xhr.responseText)
          if (response.code === 200) {
            resolve(response)
          } else {
            reject(new Error(response.message || '上传失败'))
          }
        } catch {
          reject(new Error('响应解析失败'))
        }
      } else {
        reject(new Error(`上传失败，状态码: ${xhr.status}`))
      }
    }

    xhr.onerror = function () {
      reject(new Error('网络错误'))
    }

    xhr.send(formData)
  })
}

/**
 * 删除音色克隆
 */
export function deleteVoiceClone(cloneId: number) {
  return http.delete(`${API_PREFIX}/${cloneId}`)
}

/**
 * 试听克隆音色
 */
export function previewVoiceClone(cloneId: number) {
  return http.get<string>(`${API_PREFIX}/preview/${cloneId}`)
}

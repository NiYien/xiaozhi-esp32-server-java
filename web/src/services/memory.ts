import { http } from './request'
import api from './api'
import type { MemoryQueryParams, SummaryMemory, ChatMemory, UserMemory } from '@/types/memory'
import type { MessageQueryParams } from '@/types/message'

/**
 * 查询摘要记忆
 */
export function querySummaryMemory(params: MemoryQueryParams) {
  const { roleId, deviceId, start = 1, limit = 10 } = params
  return http.getPage<SummaryMemory>(
    `${api.memory.summary}/${roleId}/${deviceId}`,
    { start, limit }
  )
}

/**
 * 查询聊天记忆（使用现有的message接口）
 */
export function queryChatMemory(params: {
  roleId: number
  deviceId: string
  start?: number
  limit?: number
  startTime?: string
  endTime?: string
}) {
  const { roleId, deviceId, start = 1, limit = 10, startTime, endTime } = params

  const queryParams: Partial<MessageQueryParams> = {
    start,
    limit,
    deviceId,
    roleId,
  }

  if (startTime) queryParams.startTime = startTime
  if (endTime) queryParams.endTime = endTime

  return http.getPage<ChatMemory>(api.message.query, queryParams)
}

/**
 * 删除摘要记忆
 */
export function deleteSummaryMemory(roleId: number, deviceId: string, summaryId?: number) {
  const url = `${api.memory.summary}/${roleId}/${deviceId}`
  const params = summaryId ? { id: summaryId } : {}
  return http.delete(url, params)
}

/**
 * 查询用户长期记忆
 */
export function queryUserMemory(params: {
  category?: string
  start?: number
  limit?: number
}) {
  return http.getPage<UserMemory>(api.memory.long, params)
}

/**
 * 删除用户长期记忆
 */
export function deleteUserMemory(memoryId: number) {
  return http.delete(`${api.memory.long}/${memoryId}`)
}

/**
 * 更新用户长期记忆
 */
export function updateUserMemory(memoryId: number, content: string, category: string) {
  return http.put(`${api.memory.long}/${memoryId}`, { content, category })
}


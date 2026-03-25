/**
 * 消息信息接口
 */
export interface Message {
  messageId: number
  deviceId: string
  deviceName?: string
  roleId?: number
  roleName?: string
  sender: 'user' | 'assistant'
  message: string
  audioPath?: string
  audioGroup?: string
  audioDuration?: number
  state: string
  messageType: string
  sessionId?: string
  createTime?: string
  updateTime?: string
  audioLoadError?: boolean
}

import type { PageQueryParams } from './api'

/**
 * 消息查询参数
 */
export interface MessageQueryParams extends PageQueryParams {
  deviceId?: string
  deviceName?: string
  roleId?: number
  sender?: string
  startTime?: string
  endTime?: string
  sessionId?: string
  // 重写 start 和 limit 为可选
  start?: number
  limit?: number
}

/**
 * 对话会话项（按 sessionId 分组）
 */
export interface ChatSession {
  sessionId: string
  title: string
  deviceId: string
  deviceName: string
  roleName: string
  messageCount: number
  firstMessageTime: string
  lastMessageTime: string
}

/**
 * 对话列表查询参数
 */
export interface SessionQueryParams extends PageQueryParams {
  deviceId?: string
  startTime?: string
  endTime?: string
  start?: number
  limit?: number
}

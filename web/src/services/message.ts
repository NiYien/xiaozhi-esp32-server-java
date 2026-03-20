import { http } from './request'
import api from './api'
import type { Message, MessageQueryParams, ChatSession, SessionQueryParams } from '@/types/message'

/**
 * 查询消息列表
 */
export function queryMessages(params: Partial<MessageQueryParams>) {
  return http.getPage<Message>(api.message.query, params)
}

/**
 * 查询对话列表（按 sessionId 分组）
 */
export function querySessions(params: Partial<SessionQueryParams>) {
  return http.getPage<ChatSession>(api.message.sessions, params)
}

/**
 * 删除消息
 */
export function deleteMessage(messageId: number | string) {
  return http.delete(`${api.message.delete}/${messageId}`)
}

/**
 * 更新消息
 */
export function updateMessage(data: Partial<Message>) {
  return http.putJSON(`${api.message.update}/${data.messageId}`, data)
}


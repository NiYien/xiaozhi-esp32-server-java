import { http } from './request'
import api from './api'

export interface McpToken {
  id: number
  userId: number
  token: string
  tokenName: string
  enabled: number
  lastUsedAt: string | null
  createTime: string
}

export interface EndpointInfo {
  endpointUrl: string
}

/**
 * 查询 MCP Token 列表
 */
export function queryMcpTokens() {
  return http.get<McpToken[]>(api.mcpToken.query)
}

/**
 * 创建 MCP Token
 */
export function createMcpToken(tokenName: string) {
  return http.post<McpToken>(api.mcpToken.create, { tokenName })
}

/**
 * 启用 MCP Token
 */
export function enableMcpToken(id: number) {
  return http.put(`${api.mcpToken.enable}/${id}/enable`)
}

/**
 * 禁用 MCP Token
 */
export function disableMcpToken(id: number) {
  return http.put(`${api.mcpToken.disable}/${id}/disable`)
}

/**
 * 删除 MCP Token
 */
export function deleteMcpToken(id: number) {
  return http.delete(`${api.mcpToken.delete}/${id}`)
}

/**
 * 获取接入点URL
 */
export function getEndpointUrl() {
  return http.get<EndpointInfo>(api.mcpToken.endpoint)
}

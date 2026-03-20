import { http } from './request'
import api from './api'

export interface McpServer {
  serverId: number
  serverName: string
  serverCode: string
  transportType: string
  serverUrl: string
  authType: string
  authToken: string | null
  enabled: number
  userId: number
  createTime: string
  updateTime: string
}

export interface McpServerQueryParams {
  serverName?: string
  transportType?: string
  enabled?: number
  start?: number
  limit?: number
}

export interface TestConnectionResult {
  success: boolean
  message: string
  toolCount?: number
  tools?: string[]
}

/**
 * 查询 MCP Server 列表
 */
export function queryMcpServers(params: Partial<McpServerQueryParams>) {
  return http.get<McpServer[]>(api.mcpServer.query, params)
}

/**
 * 添加 MCP Server
 */
export function addMcpServer(data: Partial<McpServer>) {
  return http.post(api.mcpServer.add, data as Record<string, unknown>)
}

/**
 * 更新 MCP Server
 */
export function updateMcpServer(data: Partial<McpServer>) {
  return http.put(api.mcpServer.update, data as Record<string, unknown>)
}

/**
 * 删除 MCP Server
 */
export function deleteMcpServer(serverId: number) {
  return http.delete(`${api.mcpServer.delete}/${serverId}`)
}

/**
 * 测试 MCP Server 连接
 */
export function testMcpServerConnection(data: Partial<McpServer>) {
  return http.post<TestConnectionResult>(api.mcpServer.test, data as Record<string, unknown>)
}

/**
 * 刷新所有 MCP Server 工具注册
 */
export function refreshMcpServers() {
  return http.post(api.mcpServer.refresh)
}

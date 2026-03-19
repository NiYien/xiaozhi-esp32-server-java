import { http } from './request'
import api from './api'

/**
 * 查询每日用量
 */
export function queryDailyUsage(params: {
  startDate?: string
  endDate?: string
  groupBy?: string
}) {
  return http.get(api.monitor.dailyUsage, params)
}

/**
 * 查询周期汇总
 */
export function querySummary(params: { period?: string }) {
  return http.get(api.monitor.summary, params)
}

/**
 * 查询活跃设备
 */
export function queryActiveDevices() {
  return http.get(api.monitor.activeDevices)
}

/**
 * 查询延迟趋势
 */
export function queryLatency(params: {
  serviceType?: string
  days?: number
}) {
  return http.get(api.monitor.latency, params)
}

<template>
  <div class="monitor-container">
    <!-- 时间范围选择 -->
    <a-card :bordered="false" class="time-range-card">
      <a-space>
        <a-radio-group v-model:value="timeRange" @change="handleTimeRangeChange">
          <a-radio-button value="7">{{ t('monitor.last7Days') }}</a-radio-button>
          <a-radio-button value="14">{{ t('monitor.last14Days') }}</a-radio-button>
          <a-radio-button value="30">{{ t('monitor.last30Days') }}</a-radio-button>
        </a-radio-group>
      </a-space>
    </a-card>

    <!-- 概览统计卡片 -->
    <a-row :gutter="16" class="summary-row">
      <a-col :xs="24" :sm="12" :md="6">
        <a-card :bordered="false" class="stat-card">
          <a-statistic
            :title="t('monitor.todayTokens')"
            :value="summaryData.totalTokens || 0"
            :value-style="{ color: '#1890ff' }"
          />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="6">
        <a-card :bordered="false" class="stat-card">
          <a-statistic
            :title="t('monitor.todayCalls')"
            :value="summaryData.totalCalls || 0"
            :value-style="{ color: '#52c41a' }"
          />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="6">
        <a-card :bordered="false" class="stat-card">
          <a-statistic
            :title="t('monitor.onlineDevices')"
            :value="summaryData.onlineDevices || 0"
            :value-style="{ color: '#722ed1' }"
          />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :md="6">
        <a-card :bordered="false" class="stat-card">
          <a-statistic
            :title="t('monitor.avgLlmLatency')"
            :value="summaryData.avgLlmLatencyMs || 0"
            suffix="ms"
            :value-style="{ color: '#fa8c16' }"
          />
        </a-card>
      </a-col>
    </a-row>

    <!-- 图表区域 -->
    <a-row :gutter="16">
      <!-- Token 用量趋势折线图 -->
      <a-col :xs="24" :lg="12">
        <a-card :title="t('monitor.tokenUsageTrend')" :bordered="false" class="chart-card">
          <div ref="tokenChartRef" class="chart-container"></div>
        </a-card>
      </a-col>

      <!-- 调用量柱状图 -->
      <a-col :xs="24" :lg="12">
        <a-card :title="t('monitor.callVolume')" :bordered="false" class="chart-card">
          <div ref="callChartRef" class="chart-container"></div>
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="16">
      <!-- Provider 分布饼图 -->
      <a-col :xs="24" :lg="12">
        <a-card :title="t('monitor.providerDistribution')" :bordered="false" class="chart-card">
          <div ref="providerChartRef" class="chart-container"></div>
        </a-card>
      </a-col>

      <!-- 平均延迟折线图 -->
      <a-col :xs="24" :lg="12">
        <a-card :title="t('monitor.latencyTrend')" :bordered="false" class="chart-card">
          <div ref="latencyChartRef" class="chart-container"></div>
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="16">
      <!-- 设备活跃度排行 -->
      <a-col :xs="24">
        <a-card :title="t('monitor.deviceActivity')" :bordered="false" class="chart-card">
          <div ref="deviceChartRef" class="chart-container"></div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import * as echarts from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { queryDailyUsage, querySummary, queryActiveDevices, queryLatency } from '@/services/monitor'
import dayjs from 'dayjs'

// 注册 ECharts 组件
echarts.use([
  LineChart,
  BarChart,
  PieChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  CanvasRenderer,
])

const { t } = useI18n()

// 时间范围
const timeRange = ref('7')

// 概览数据
const summaryData = ref<Record<string, number>>({})

// 图表引用
const tokenChartRef = ref<HTMLDivElement>()
const callChartRef = ref<HTMLDivElement>()
const providerChartRef = ref<HTMLDivElement>()
const latencyChartRef = ref<HTMLDivElement>()
const deviceChartRef = ref<HTMLDivElement>()

// ECharts 实例
let tokenChart: echarts.ECharts | null = null
let callChart: echarts.ECharts | null = null
let providerChart: echarts.ECharts | null = null
let latencyChart: echarts.ECharts | null = null
let deviceChart: echarts.ECharts | null = null

/**
 * 获取日期范围
 */
function getDateRange() {
  const days = parseInt(timeRange.value)
  const endDate = dayjs().format('YYYY-MM-DD')
  const startDate = dayjs().subtract(days - 1, 'day').format('YYYY-MM-DD')
  return { startDate, endDate, days }
}

/**
 * 加载概览数据
 */
async function loadSummary() {
  try {
    const res = await querySummary({ period: timeRange.value === '30' ? 'month' : 'week' }) as any
    if (res.code === 200 && res.data) {
      summaryData.value = res.data
    }
  } catch (e) {
    console.error('加载汇总数据失败', e)
  }
}

/**
 * 加载 Token 用量趋势图
 */
async function loadTokenChart() {
  const { startDate, endDate } = getDateRange()
  try {
    const res = await queryDailyUsage({ startDate, endDate, groupBy: 'provider' }) as any
    if (res.code !== 200) return
    const data: any[] = res.data || []

    // 按日期聚合 promptTokens 和 completionTokens
    const dateMap: Record<string, { prompt: number; completion: number }> = {}
    for (const item of data) {
      const date = item.statDate
      if (!dateMap[date]) {
        dateMap[date] = { prompt: 0, completion: 0 }
      }
      dateMap[date].prompt += Number(item.promptTokens || 0)
      dateMap[date].completion += Number(item.completionTokens || 0)
    }

    const dates = Object.keys(dateMap).sort()
    const promptData = dates.map((d) => dateMap[d].prompt)
    const completionData = dates.map((d) => dateMap[d].completion)

    if (tokenChart) {
      tokenChart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { data: [t('monitor.promptTokens'), t('monitor.completionTokens')] },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'category', data: dates },
        yAxis: { type: 'value' },
        series: [
          {
            name: t('monitor.promptTokens'),
            type: 'line',
            data: promptData,
            smooth: true,
            itemStyle: { color: '#1890ff' },
            areaStyle: { color: 'rgba(24,144,255,0.1)' },
          },
          {
            name: t('monitor.completionTokens'),
            type: 'line',
            data: completionData,
            smooth: true,
            itemStyle: { color: '#52c41a' },
            areaStyle: { color: 'rgba(82,196,26,0.1)' },
          },
        ],
      })
    }
  } catch (e) {
    console.error('加载Token用量失败', e)
  }
}

/**
 * 加载调用量柱状图
 */
async function loadCallChart() {
  const { startDate, endDate } = getDateRange()
  try {
    const res = await queryDailyUsage({ startDate, endDate, groupBy: 'provider' }) as any
    if (res.code !== 200) return
    const data: any[] = res.data || []

    // 按日期和 serviceType 聚合
    const dateMap: Record<string, { llm: number; stt: number; tts: number }> = {}
    for (const item of data) {
      const date = item.statDate
      if (!dateMap[date]) {
        dateMap[date] = { llm: 0, stt: 0, tts: 0 }
      }
      const st = item.serviceType as 'llm' | 'stt' | 'tts'
      if (st in dateMap[date]) {
        dateMap[date][st] += Number(item.requestCount || 0)
      }
    }

    const dates = Object.keys(dateMap).sort()
    const llmData = dates.map((d) => dateMap[d].llm)
    const sttData = dates.map((d) => dateMap[d].stt)
    const ttsData = dates.map((d) => dateMap[d].tts)

    if (callChart) {
      callChart.setOption({
        tooltip: { trigger: 'axis' },
        legend: {
          data: [t('monitor.llmCalls'), t('monitor.sttCalls'), t('monitor.ttsCalls')],
        },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'category', data: dates },
        yAxis: { type: 'value' },
        series: [
          {
            name: t('monitor.llmCalls'),
            type: 'bar',
            stack: 'calls',
            data: llmData,
            itemStyle: { color: '#1890ff' },
          },
          {
            name: t('monitor.sttCalls'),
            type: 'bar',
            stack: 'calls',
            data: sttData,
            itemStyle: { color: '#52c41a' },
          },
          {
            name: t('monitor.ttsCalls'),
            type: 'bar',
            stack: 'calls',
            data: ttsData,
            itemStyle: { color: '#fa8c16' },
          },
        ],
      })
    }
  } catch (e) {
    console.error('加载调用量失败', e)
  }
}

/**
 * 加载 Provider 分布饼图
 */
async function loadProviderChart() {
  const { startDate, endDate } = getDateRange()
  try {
    const res = await queryDailyUsage({ startDate, endDate, groupBy: 'provider' }) as any
    if (res.code !== 200) return
    const data: any[] = res.data || []

    // 按 provider 聚合调用次数
    const providerMap: Record<string, number> = {}
    for (const item of data) {
      const key = item.groupKey || 'unknown'
      providerMap[key] = (providerMap[key] || 0) + Number(item.requestCount || 0)
    }

    const pieData = Object.entries(providerMap).map(([name, value]) => ({ name, value }))

    if (providerChart) {
      providerChart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        legend: { orient: 'vertical', left: 'left' },
        series: [
          {
            type: 'pie',
            radius: ['40%', '70%'],
            avoidLabelOverlap: false,
            itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
            label: { show: true, formatter: '{b}\n{d}%' },
            data: pieData,
          },
        ],
      })
    }
  } catch (e) {
    console.error('加载Provider分布失败', e)
  }
}

/**
 * 加载延迟趋势图
 */
async function loadLatencyChart() {
  const { days } = getDateRange()
  try {
    const res = await queryLatency({ days }) as any
    if (res.code !== 200) return
    const data: any[] = res.data || []

    // 按日期和 serviceType 聚合
    const dateMap: Record<string, { llm: number; stt: number; tts: number }> = {}
    for (const item of data) {
      const date = item.statDate
      if (!dateMap[date]) {
        dateMap[date] = { llm: 0, stt: 0, tts: 0 }
      }
      const st = item.serviceType as 'llm' | 'stt' | 'tts'
      if (st in dateMap[date]) {
        dateMap[date][st] = Number(item.avgLatencyMs || 0)
      }
    }

    const dates = Object.keys(dateMap).sort()
    const llmLatency = dates.map((d) => dateMap[d].llm)
    const sttLatency = dates.map((d) => dateMap[d].stt)
    const ttsLatency = dates.map((d) => dateMap[d].tts)

    if (latencyChart) {
      latencyChart.setOption({
        tooltip: { trigger: 'axis', formatter: (params: any) => {
          let str = params[0]?.axisValue + '<br/>'
          for (const p of params) {
            str += `${p.marker} ${p.seriesName}: ${p.value} ms<br/>`
          }
          return str
        }},
        legend: { data: ['LLM', 'STT', 'TTS'] },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'category', data: dates },
        yAxis: { type: 'value', axisLabel: { formatter: '{value} ms' } },
        series: [
          {
            name: 'LLM',
            type: 'line',
            data: llmLatency,
            smooth: true,
            itemStyle: { color: '#1890ff' },
          },
          {
            name: 'STT',
            type: 'line',
            data: sttLatency,
            smooth: true,
            itemStyle: { color: '#52c41a' },
          },
          {
            name: 'TTS',
            type: 'line',
            data: ttsLatency,
            smooth: true,
            itemStyle: { color: '#fa8c16' },
          },
        ],
      })
    }
  } catch (e) {
    console.error('加载延迟趋势失败', e)
  }
}

/**
 * 加载设备活跃度排行
 */
async function loadDeviceChart() {
  try {
    const res = await queryActiveDevices() as any
    if (res.code !== 200) return
    const data = res.data || {}
    const devices: any[] = data.devices || []

    // 取 Top 10
    const top10 = devices.slice(0, 10).reverse()
    const deviceIds = top10.map((d: any) => d.deviceId || 'unknown')
    const requests = top10.map((d: any) => Number(d.totalRequests || 0))

    if (deviceChart) {
      deviceChart.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: { type: 'value' },
        yAxis: { type: 'category', data: deviceIds },
        series: [
          {
            type: 'bar',
            data: requests,
            itemStyle: {
              color: (params: any) => {
                const colors = [
                  '#1890ff', '#52c41a', '#fa8c16', '#722ed1', '#eb2f96',
                  '#13c2c2', '#faad14', '#2f54eb', '#a0d911', '#f5222d',
                ]
                return colors[params.dataIndex % colors.length]
              },
            },
            label: { show: true, position: 'right' },
          },
        ],
      })
    }
  } catch (e) {
    console.error('加载设备活跃度失败', e)
  }
}

/**
 * 初始化所有图表
 */
function initCharts() {
  if (tokenChartRef.value) {
    tokenChart = echarts.init(tokenChartRef.value)
  }
  if (callChartRef.value) {
    callChart = echarts.init(callChartRef.value)
  }
  if (providerChartRef.value) {
    providerChart = echarts.init(providerChartRef.value)
  }
  if (latencyChartRef.value) {
    latencyChart = echarts.init(latencyChartRef.value)
  }
  if (deviceChartRef.value) {
    deviceChart = echarts.init(deviceChartRef.value)
  }
}

/**
 * 加载所有数据
 */
async function loadAllData() {
  await Promise.all([
    loadSummary(),
    loadTokenChart(),
    loadCallChart(),
    loadProviderChart(),
    loadLatencyChart(),
    loadDeviceChart(),
  ])
}

/**
 * 处理时间范围变化
 */
function handleTimeRangeChange() {
  loadAllData()
}

/**
 * 处理窗口大小变化
 */
function handleResize() {
  tokenChart?.resize()
  callChart?.resize()
  providerChart?.resize()
  latencyChart?.resize()
  deviceChart?.resize()
}

onMounted(async () => {
  await nextTick()
  initCharts()
  await loadAllData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  tokenChart?.dispose()
  callChart?.dispose()
  providerChart?.dispose()
  latencyChart?.dispose()
  deviceChart?.dispose()
})
</script>

<style scoped>
.monitor-container {
  padding: 0;
}

.time-range-card {
  margin-bottom: 16px;
}

.summary-row {
  margin-bottom: 16px;
}

.stat-card {
  text-align: center;
}

.chart-card {
  margin-bottom: 16px;
}

.chart-container {
  width: 100%;
  height: 350px;
}
</style>

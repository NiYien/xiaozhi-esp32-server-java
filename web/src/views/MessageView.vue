<script setup lang="ts">
import { ref, reactive, onBeforeUnmount, computed, watch, nextTick } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import { RobotOutlined, UserOutlined, ToolOutlined, ExportOutlined, MessageOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { useI18n } from 'vue-i18n'
import { useExport } from '@/composables/useExport'
import { useLoadingStore } from '@/store/loading'
import { queryMessages, querySessions, deleteMessage } from '@/services/message'
import { queryDevices, clearDeviceMemory } from '@/services/device'
import AudioPlayer from '@/components/AudioPlayer.vue'
import type { Message, MessageQueryParams, ChatSession, SessionQueryParams } from '@/types/message'
import type { Device } from '@/types/device'
import dayjs, { Dayjs } from 'dayjs'
import { useEventBus } from '@vueuse/core'
import { onBeforeRouteLeave } from 'vue-router'

const { t } = useI18n()
const loadingStore = useLoadingStore()
const { exporting, exportToCSV } = useExport()

// 事件总线
const stopAllAudioBus = useEventBus<void>('stop-all-audio')

// ==================== 设备列表 ====================
const deviceList = ref<Device[]>([])

async function fetchDevices() {
  try {
    const res = await queryDevices({ start: 1, limit: 1000 })
    if (res.code === 200 && res.data?.list) {
      deviceList.value = res.data.list
    }
  } catch (e) {
    console.error('获取设备列表失败:', e)
  }
}

// ==================== 筛选条件 ====================
const filterForm = reactive({
  deviceId: '' as string,
})
const timeRange = ref<[Dayjs, Dayjs]>([dayjs().subtract(30, 'day').startOf('day'), dayjs().endOf('day')])

const rangePresets = computed(() => [
  { label: t('message.today'), value: [dayjs().startOf('day'), dayjs().endOf('day')] },
  { label: t('message.thisMonth'), value: [dayjs().startOf('month'), dayjs().endOf('month')] },
])

// ==================== 左侧对话列表 ====================
const sessionList = ref<ChatSession[]>([])
const sessionLoading = ref(false)
const sessionPage = ref(1)
const sessionPageSize = 20
const hasMoreSessions = ref(true)
const selectedSessionId = ref<string | null>(null)

async function fetchSessions(append = false) {
  if (sessionLoading.value) return
  sessionLoading.value = true
  try {
    const params: SessionQueryParams = {
      start: append ? sessionPage.value : 1,
      limit: sessionPageSize,
      startTime: timeRange.value[0].format('YYYY-MM-DD HH:mm:ss'),
      endTime: timeRange.value[1].format('YYYY-MM-DD HH:mm:ss'),
    }
    if (filterForm.deviceId) params.deviceId = filterForm.deviceId

    const res = await querySessions(params)
    if (res.code === 200 && res.data) {
      const list = res.data.list || []
      if (append) {
        sessionList.value = [...sessionList.value, ...list]
      } else {
        sessionList.value = list
        sessionPage.value = 1
      }
      hasMoreSessions.value = list.length >= sessionPageSize
    }
  } catch (e) {
    console.error('获取对话列表失败:', e)
  } finally {
    sessionLoading.value = false
  }
}

function loadMoreSessions() {
  if (!hasMoreSessions.value || sessionLoading.value) return
  sessionPage.value++
  fetchSessions(true)
}

function selectSession(session: ChatSession) {
  selectedSessionId.value = session.sessionId
  fetchMessages()
}

// 筛选条件变化时重新加载
function onFilterChange() {
  selectedSessionId.value = null
  messages.value = []
  fetchSessions(false)
}

// ==================== 右侧聊天消息 ====================
const messages = ref<Message[]>([])
const messagesLoading = ref(false)
const chatContainerRef = ref<HTMLDivElement>()

const selectedSession = computed(() => {
  return sessionList.value.find(s => s.sessionId === selectedSessionId.value) || null
})

async function fetchMessages() {
  if (!selectedSessionId.value) return
  messagesLoading.value = true
  try {
    const params: MessageQueryParams = {
      start: 1,
      limit: 10000,
      startTime: timeRange.value[0].format('YYYY-MM-DD HH:mm:ss'),
      endTime: timeRange.value[1].format('YYYY-MM-DD HH:mm:ss'),
    }
    // 如果是 legacy_ 开头的虚拟 sessionId，不传 sessionId 筛选
    if (selectedSessionId.value && !selectedSessionId.value.startsWith('legacy_')) {
      params.sessionId = selectedSessionId.value
    }
    if (filterForm.deviceId) params.deviceId = filterForm.deviceId

    const res = await queryMessages(params)
    if (res.code === 200 && res.data?.list) {
      // 按 sessionId 筛选（前端补充过滤）并按时间正序排列
      let list = res.data.list
      if (selectedSessionId.value) {
        if (selectedSessionId.value.startsWith('legacy_')) {
          // legacy 消息：找 messageId 匹配
          const legacyMsgId = selectedSessionId.value.replace('legacy_', '')
          list = list.filter(m => String(m.messageId) === legacyMsgId)
        } else {
          list = list.filter(m => m.sessionId === selectedSessionId.value)
        }
      }
      messages.value = list.sort((a, b) => {
        const ta = a.createTime ? new Date(a.createTime).getTime() : 0
        const tb = b.createTime ? new Date(b.createTime).getTime() : 0
        return ta - tb
      })
      // 滚动到底部
      nextTick(() => {
        if (chatContainerRef.value) {
          chatContainerRef.value.scrollTop = chatContainerRef.value.scrollHeight
        }
      })
    }
  } catch (e) {
    console.error('获取消息失败:', e)
  } finally {
    messagesLoading.value = false
  }
}

// ==================== 消息操作 ====================
async function handleDeleteMessage(msg: Message) {
  try {
    const res = await deleteMessage(msg.messageId)
    if (res.code === 200) {
      antMessage.success(t('common.deleteSuccess'))
      messages.value = messages.value.filter(m => m.messageId !== msg.messageId)
    }
  } catch (e) {
    console.error('删除消息失败:', e)
    antMessage.error(t('common.deleteFailed'))
  }
}

async function handleClearDeviceMemory() {
  if (!selectedSession.value) return
  const deviceId = selectedSession.value.deviceId
  try {
    const res = await clearDeviceMemory(deviceId)
    if (res.code === 200) {
      antMessage.success(t('common.deleteSuccess'))
      // 重新加载
      selectedSessionId.value = null
      messages.value = []
      fetchSessions(false)
    }
  } catch (e) {
    console.error('清除设备记忆失败:', e)
    antMessage.error(t('common.deleteFailed'))
  }
}

// ==================== 导出功能 ====================
async function handleExport() {
  loadingStore.showLoading(t('common.exporting'))
  try {
    const params: MessageQueryParams = {
      start: 1,
      limit: 100000,
      startTime: timeRange.value[0].format('YYYY-MM-DD HH:mm:ss'),
      endTime: timeRange.value[1].format('YYYY-MM-DD HH:mm:ss'),
    }
    if (filterForm.deviceId) params.deviceId = filterForm.deviceId

    const res = await queryMessages(params)
    if (res.code !== 200 || !res.data?.list || res.data.list.length === 0) {
      antMessage.warning(t('export.noData'))
      return
    }

    await exportToCSV(res.data.list, {
      filename: `messages_${dayjs().format('YYYY-MM-DD_HH-mm-ss')}`,
      showLoading: false,
      columns: [
        { key: 'deviceId', title: t('device.deviceId') },
        { key: 'deviceName', title: t('device.deviceName') },
        { key: 'roleName', title: t('role.roleName') },
        {
          key: 'sender',
          title: t('message.messageSender'),
          format: (val) => val === 'user' ? t('message.user') : t('message.assistant')
        },
        { key: 'message', title: t('message.messageContent') },
        { key: 'createTime', title: t('message.conversationTime') }
      ]
    })
    antMessage.success(t('common.exportSuccess'))
  } catch (e) {
    console.error('导出失败:', e)
    antMessage.error(t('common.exportFailed'))
  } finally {
    loadingStore.hideLoading()
  }
}

// ==================== 工具方法 ====================
function hasValidAudio(audioPath: string | undefined | null): boolean {
  return !!(audioPath && audioPath.trim())
}

function formatTime(time: string | undefined): string {
  if (!time) return ''
  return dayjs(time).format('MM-DD HH:mm')
}

function formatFullTime(time: string | undefined): string {
  if (!time) return ''
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

function truncateTitle(title: string | null | undefined): string {
  if (!title) return t('message.noAudio')
  return title.length > 30 ? title.substring(0, 30) + '...' : title
}

function parseToolCalls(toolCallsStr: string | undefined | null): any[] {
  if (!toolCallsStr) return []
  try {
    const parsed = JSON.parse(toolCallsStr)
    return Array.isArray(parsed) ? parsed : [parsed]
  } catch {
    return []
  }
}

// 左侧列表滚动加载
function onSessionListScroll(e: Event) {
  const target = e.target as HTMLDivElement
  if (target.scrollHeight - target.scrollTop - target.clientHeight < 50) {
    loadMoreSessions()
  }
}

// ==================== 生命周期 ====================
onBeforeRouteLeave(() => {
  stopAllAudioBus.emit()
})

onBeforeUnmount(() => {
  stopAllAudioBus.emit()
})

// 初始化
fetchDevices()
fetchSessions(false)
</script>

<template>
  <div class="message-view">
    <div class="chat-layout">
      <!-- 左侧面板：对话列表 -->
      <div class="left-panel">
        <!-- 筛选条件 -->
        <div class="filter-bar">
          <a-select
            v-model:value="filterForm.deviceId"
            :placeholder="t('message.selectDevice')"
            allow-clear
            style="width: 100%; margin-bottom: 8px"
            @change="onFilterChange"
          >
            <a-select-option value="">{{ t('message.allDevices') }}</a-select-option>
            <a-select-option v-for="device in deviceList" :key="device.deviceId" :value="device.deviceId">
              {{ device.deviceName || device.deviceId }}
            </a-select-option>
          </a-select>
          <a-range-picker
            v-model:value="timeRange"
            :presets="rangePresets"
            :allow-clear="false"
            format="MM-DD"
            style="width: 100%"
            @change="onFilterChange"
          />
        </div>

        <!-- 对话列表 -->
        <div class="session-list" @scroll="onSessionListScroll">
          <a-spin :spinning="sessionLoading && sessionList.length === 0">
            <div v-if="sessionList.length === 0 && !sessionLoading" class="empty-sessions">
              <a-empty :description="t('message.noSessions')" />
            </div>
            <div
              v-for="session in sessionList"
              :key="session.sessionId"
              class="session-item"
              :class="{ 'session-item--active': selectedSessionId === session.sessionId }"
              @click="selectSession(session)"
            >
              <div class="session-item__title">
                <MessageOutlined style="margin-right: 6px; color: var(--ant-color-primary)" />
                {{ truncateTitle(session.title) }}
              </div>
              <div class="session-item__meta">
                <span class="session-item__device">{{ session.deviceName || session.deviceId }}</span>
                <span class="session-item__time">{{ formatTime(session.lastMessageTime) }}</span>
              </div>
              <div class="session-item__count">
                {{ t('message.messageCountLabel', { count: session.messageCount }) }}
              </div>
            </div>
            <div v-if="sessionLoading && sessionList.length > 0" class="loading-more">
              <a-spin size="small" />
            </div>
            <div v-if="!hasMoreSessions && sessionList.length > 0" class="no-more">
              {{ t('message.noMoreSessions') }}
            </div>
          </a-spin>
        </div>
      </div>

      <!-- 右侧面板：聊天视图 -->
      <div class="right-panel">
        <!-- 未选中对话时的空状态 -->
        <div v-if="!selectedSessionId" class="empty-chat">
          <a-empty :description="t('message.selectSession')">
            <template #image>
              <MessageOutlined style="font-size: 64px; color: var(--ant-color-text-quaternary)" />
            </template>
          </a-empty>
        </div>

        <!-- 选中对话后 -->
        <template v-else>
          <!-- 顶部信息栏 -->
          <div class="chat-header">
            <div class="chat-header__info">
              <span class="chat-header__device">{{ selectedSession?.deviceName || selectedSession?.deviceId }}</span>
              <a-divider type="vertical" />
              <span v-if="selectedSession?.roleName" class="chat-header__role">{{ selectedSession.roleName }}</span>
              <a-divider v-if="selectedSession?.roleName" type="vertical" />
              <span class="chat-header__time">
                {{ formatFullTime(selectedSession?.firstMessageTime) }} ~ {{ formatFullTime(selectedSession?.lastMessageTime) }}
              </span>
            </div>
            <div class="chat-header__actions">
              <a-popconfirm
                :title="t('device.confirmClearMemory')"
                @confirm="handleClearDeviceMemory"
              >
                <a-button size="small" danger>
                  <template #icon><DeleteOutlined /></template>
                  {{ t('device.clearMemory') }}
                </a-button>
              </a-popconfirm>
              <a-button size="small" type="primary" @click="handleExport" :loading="exporting">
                <template #icon><ExportOutlined /></template>
                {{ t('common.export') }}
              </a-button>
            </div>
          </div>

          <!-- 聊天消息区 -->
          <div ref="chatContainerRef" class="chat-container">
            <a-spin :spinning="messagesLoading">
              <div class="chat-messages">
                <div
                  v-for="msg in messages"
                  :key="msg.messageId"
                  class="chat-message"
                  :class="{
                    'chat-message--user': msg.sender === 'user',
                    'chat-message--assistant': msg.sender === 'assistant',
                    'chat-message--function': msg.messageType === 'FUNCTION_CALL',
                  }"
                >
                  <!-- 助手消息：头像在左 -->
                  <div v-if="msg.sender === 'assistant'" class="chat-message__avatar">
                    <a-avatar :size="36" style="background-color: var(--ant-color-primary)">
                      <template #icon><RobotOutlined /></template>
                    </a-avatar>
                  </div>

                  <!-- 消息内容 -->
                  <div class="chat-message__content">
                    <!-- Function Call 消息：折叠展示 -->
                    <template v-if="msg.messageType === 'FUNCTION_CALL'">
                      <a-collapse class="tool-call-collapse" :bordered="false">
                        <a-collapse-panel>
                          <template #header>
                            <ToolOutlined style="margin-right: 6px" />
                            {{ t('message.toolCalls') }}
                          </template>
                          <div v-if="msg.toolCalls">
                            <div v-for="(call, idx) in parseToolCalls(msg.toolCalls)" :key="idx" class="tool-call-item">
                              <div class="tool-call-item__name">
                                <strong>{{ t('message.toolName') }}:</strong> {{ call.name || '-' }}
                              </div>
                              <div v-if="call.arguments" class="tool-call-item__args">
                                <strong>{{ t('message.toolArguments') }}:</strong>
                                <pre>{{ typeof call.arguments === 'string' ? call.arguments : JSON.stringify(call.arguments, null, 2) }}</pre>
                              </div>
                              <div v-if="call.result" class="tool-call-item__result">
                                <strong>{{ t('message.toolResult') }}:</strong>
                                <pre>{{ typeof call.result === 'string' ? call.result : JSON.stringify(call.result, null, 2) }}</pre>
                              </div>
                            </div>
                          </div>
                          <div v-if="msg.message" style="margin-top: 8px; color: var(--ant-color-text-secondary)">
                            {{ msg.message }}
                          </div>
                        </a-collapse-panel>
                      </a-collapse>
                    </template>

                    <!-- 普通消息 -->
                    <template v-else>
                      <div class="chat-bubble" :class="msg.sender === 'user' ? 'chat-bubble--user' : 'chat-bubble--assistant'">
                        <div v-if="msg.message" class="chat-bubble__text">{{ msg.message }}</div>
                        <div v-if="hasValidAudio(msg.audioPath)" class="chat-bubble__audio">
                          <AudioPlayer :audio-url="msg.audioPath!" />
                        </div>
                      </div>
                    </template>

                    <!-- 时间和操作 -->
                    <div class="chat-message__footer" :class="msg.sender === 'user' ? 'chat-message__footer--right' : ''">
                      <span class="chat-message__time">{{ formatFullTime(msg.createTime) }}</span>
                      <a-popconfirm
                        :title="t('message.confirmDeleteMessage')"
                        @confirm="() => handleDeleteMessage(msg)"
                      >
                        <a-button type="link" size="small" danger class="chat-message__delete">
                          <DeleteOutlined />
                        </a-button>
                      </a-popconfirm>
                    </div>
                  </div>

                  <!-- 用户消息：头像在右 -->
                  <div v-if="msg.sender === 'user'" class="chat-message__avatar">
                    <a-avatar :size="36" style="background-color: #87d068">
                      <template #icon><UserOutlined /></template>
                    </a-avatar>
                  </div>
                </div>
              </div>
            </a-spin>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.message-view {
  height: calc(100vh - 120px);
  padding: 16px;
}

.chat-layout {
  display: flex;
  height: 100%;
  border: 1px solid var(--ant-color-border);
  border-radius: 8px;
  overflow: hidden;
  background: var(--ant-color-bg-container);
}

// ==================== 左侧面板 ====================
.left-panel {
  width: 30%;
  min-width: 280px;
  max-width: 400px;
  border-right: 1px solid var(--ant-color-border);
  display: flex;
  flex-direction: column;
  background: var(--ant-color-bg-layout);
}

.filter-bar {
  padding: 12px;
  border-bottom: 1px solid var(--ant-color-border);
  background: var(--ant-color-bg-container);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

.empty-sessions {
  padding: 40px 16px;
  text-align: center;
}

.session-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--ant-color-border-secondary);
  transition: background-color 0.2s;

  &:hover {
    background-color: var(--ant-color-bg-text-hover);
  }

  &--active {
    background-color: var(--ant-color-primary-bg);
    border-left: 3px solid var(--ant-color-primary);
    padding-left: 13px;

    &:hover {
      background-color: var(--ant-color-primary-bg);
    }
  }

  &__title {
    font-size: 14px;
    font-weight: 500;
    color: var(--ant-color-text);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    margin-bottom: 4px;
    display: flex;
    align-items: center;
  }

  &__meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 2px;
  }

  &__device {
    font-size: 12px;
    color: var(--ant-color-text-secondary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 60%;
  }

  &__time {
    font-size: 12px;
    color: var(--ant-color-text-tertiary);
    white-space: nowrap;
  }

  &__count {
    font-size: 11px;
    color: var(--ant-color-text-quaternary);
  }
}

.loading-more,
.no-more {
  text-align: center;
  padding: 12px;
  font-size: 12px;
  color: var(--ant-color-text-tertiary);
}

// ==================== 右侧面板 ====================
.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.empty-chat {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--ant-color-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
  background: var(--ant-color-bg-container);

  &__info {
    display: flex;
    align-items: center;
    gap: 4px;
    overflow: hidden;
    flex: 1;
  }

  &__device {
    font-weight: 600;
    color: var(--ant-color-text);
    white-space: nowrap;
  }

  &__role {
    color: var(--ant-color-primary);
    white-space: nowrap;
  }

  &__time {
    font-size: 12px;
    color: var(--ant-color-text-tertiary);
    white-space: nowrap;
  }

  &__actions {
    display: flex;
    gap: 8px;
    flex-shrink: 0;
    margin-left: 12px;
  }
}

// ==================== 聊天区域 ====================
.chat-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: var(--ant-color-bg-layout);
}

.chat-messages {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-message {
  display: flex;
  align-items: flex-start;
  gap: 10px;

  &--user {
    justify-content: flex-end;
  }

  &--assistant {
    justify-content: flex-start;
  }

  &--function {
    justify-content: center;

    .chat-message__content {
      max-width: 80%;
    }
  }

  &__avatar {
    flex-shrink: 0;
  }

  &__content {
    max-width: 70%;
    min-width: 60px;
  }

  &__footer {
    display: flex;
    align-items: center;
    gap: 4px;
    margin-top: 4px;

    &--right {
      justify-content: flex-end;
    }
  }

  &__time {
    font-size: 11px;
    color: var(--ant-color-text-quaternary);
  }

  &__delete {
    font-size: 12px;
    padding: 0 4px;
    opacity: 0;
    transition: opacity 0.2s;
  }

  &:hover .chat-message__delete {
    opacity: 1;
  }
}

// ==================== 聊天气泡 ====================
.chat-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  word-break: break-word;
  line-height: 1.6;

  &--user {
    background-color: #d9f7be;
    border-top-right-radius: 4px;
    color: var(--ant-color-text);
  }

  &--assistant {
    background-color: var(--ant-color-bg-container);
    border-top-left-radius: 4px;
    color: var(--ant-color-text);
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  }

  &__text {
    white-space: pre-wrap;
  }

  &__audio {
    margin-top: 8px;
    min-width: 200px;
  }
}

// ==================== Function Call 折叠 ====================
.tool-call-collapse {
  background: var(--ant-color-bg-container);
  border-radius: 8px;

  :deep(.ant-collapse-header) {
    font-size: 13px;
    color: var(--ant-color-text-secondary);
  }
}

.tool-call-item {
  margin-bottom: 8px;
  padding: 8px;
  background: var(--ant-color-fill-quaternary);
  border-radius: 6px;
  font-size: 12px;

  &__name {
    margin-bottom: 4px;
  }

  &__args,
  &__result {
    margin-top: 4px;

    pre {
      margin: 4px 0 0;
      padding: 6px 8px;
      background: var(--ant-color-fill-tertiary);
      border-radius: 4px;
      font-size: 11px;
      overflow-x: auto;
      white-space: pre-wrap;
      word-break: break-all;
    }
  }
}

// 暗色模式下用户气泡颜色
:global(.dark) .chat-bubble--user {
  background-color: #274916;
}
</style>

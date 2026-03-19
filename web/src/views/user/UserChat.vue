<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { queryMessages } from '@/services/message'
import { queryDevices } from '@/services/device'
import {
  ArrowLeftOutlined,
  SoundOutlined,
  LoadingOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import type { Message } from '@/types/message'
import type { Device } from '@/types/device'

const route = useRoute()
const router = useRouter()

// 设备 ID
const deviceId = computed(() => route.params.deviceId as string)

// 数据状态
const loading = ref(false)
const loadingMore = ref(false)
const messages = ref<Message[]>([])
const deviceInfo = ref<Device | null>(null)
const currentPage = ref(1)
const hasMore = ref(true)
const pageSize = 20

// 聊天容器引用
const chatContainerRef = ref<HTMLElement>()

// 音频播放
const playingAudioId = ref<number | null>(null)
const audioRef = ref<HTMLAudioElement | null>(null)

/**
 * 加载设备信息
 */
async function loadDeviceInfo() {
  try {
    const res = await queryDevices({ deviceId: deviceId.value, start: 1, limit: 1 })
    if (res.code === 200 && res.data && res.data.list.length > 0) {
      deviceInfo.value = res.data.list[0] ?? null
    }
  } catch (error) {
    console.error('加载设备信息失败:', error)
  }
}

/**
 * 加载对话消息
 */
async function loadMessages(page: number = 1, append: boolean = false) {
  if (loading.value || loadingMore.value) return

  if (page === 1) {
    loading.value = true
  } else {
    loadingMore.value = true
  }

  try {
    const res = await queryMessages({
      deviceId: deviceId.value,
      start: page,
      limit: pageSize,
    })

    if (res.code === 200 && res.data) {
      const newMessages = (res.data.list || []).reverse()

      if (append) {
        // 向上加载更多 - 插入到消息列表前面
        const container = chatContainerRef.value
        const prevScrollHeight = container?.scrollHeight || 0

        messages.value = [...newMessages, ...messages.value]

        // 保持滚动位置
        nextTick(() => {
          if (container) {
            const newScrollHeight = container.scrollHeight
            container.scrollTop = newScrollHeight - prevScrollHeight
          }
        })
      } else {
        messages.value = newMessages

        // 首次加载滚动到底部
        nextTick(() => {
          scrollToBottom()
        })
      }

      currentPage.value = page
      hasMore.value = !res.data.isLastPage
    }
  } catch (error) {
    console.error('加载消息失败:', error)
    message.error('加载对话记录失败')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

/**
 * 滚动到底部
 */
function scrollToBottom() {
  const container = chatContainerRef.value
  if (container) {
    container.scrollTop = container.scrollHeight
  }
}

/**
 * 处理滚动事件 - 上拉加载更多
 */
function handleScroll() {
  const container = chatContainerRef.value
  if (!container || !hasMore.value || loadingMore.value) return

  // 滚动到顶部附近时加载更多
  if (container.scrollTop < 60) {
    loadMessages(currentPage.value + 1, true)
  }
}

/**
 * 获取音频 URL
 */
function getAudioUrl(audioPath?: string): string {
  if (!audioPath) return ''
  if (audioPath.startsWith('http://') || audioPath.startsWith('https://')) {
    return audioPath
  }
  const baseURL = import.meta.env.VITE_API_BASE_URL || ''
  // 去掉 baseURL 末尾的 /api 等路径
  const origin = baseURL ? new URL(baseURL).origin : ''
  return `${origin}/${audioPath}`
}

/**
 * 播放音频
 */
function playAudio(msg: Message) {
  if (!msg.audioPath) return

  // 如果正在播放同一条消息，则停止
  if (playingAudioId.value === msg.messageId) {
    stopAudio()
    return
  }

  stopAudio()

  const audio = new Audio(getAudioUrl(msg.audioPath))
  audioRef.value = audio
  playingAudioId.value = msg.messageId

  audio.play().catch(() => {
    message.error('音频播放失败')
    playingAudioId.value = null
  })

  audio.onended = () => {
    playingAudioId.value = null
    audioRef.value = null
  }

  audio.onerror = () => {
    playingAudioId.value = null
    audioRef.value = null
  }
}

/**
 * 停止音频
 */
function stopAudio() {
  if (audioRef.value) {
    audioRef.value.pause()
    audioRef.value.currentTime = 0
    audioRef.value = null
  }
  playingAudioId.value = null
}

/**
 * 返回设备列表
 */
function goBack() {
  router.push('/u/devices')
}

// 监听路由参数变化
watch(deviceId, () => {
  messages.value = []
  currentPage.value = 1
  hasMore.value = true
  loadDeviceInfo()
  loadMessages()
})

onMounted(() => {
  loadDeviceInfo()
  loadMessages()
})
</script>

<template>
  <div class="user-chat">
    <!-- 顶部信息栏 -->
    <div class="chat-header">
      <a-button type="text" class="back-btn" @click="goBack">
        <template #icon><ArrowLeftOutlined /></template>
      </a-button>
      <div class="chat-header-info">
        <h3>{{ deviceInfo?.deviceName || deviceId }}</h3>
        <span v-if="deviceInfo?.roleName" class="chat-role">{{ deviceInfo.roleName }}</span>
      </div>
    </div>

    <!-- 对话区域 -->
    <div
      ref="chatContainerRef"
      class="chat-container"
      @scroll="handleScroll"
    >
      <!-- 加载更多提示 -->
      <div v-if="loadingMore" class="loading-more">
        <a-spin size="small" />
        <span>加载历史消息...</span>
      </div>
      <div v-else-if="!hasMore && messages.length > 0" class="no-more">
        -- 没有更多消息了 --
      </div>

      <!-- 消息列表 -->
      <div v-if="loading" class="chat-loading">
        <a-spin size="large" />
      </div>

      <template v-else-if="messages.length > 0">
        <div
          v-for="msg in messages"
          :key="msg.messageId"
          class="message-row"
          :class="{ 'is-user': msg.sender === 'user' }"
        >
          <!-- 头像 -->
          <div class="message-avatar">
            <a-avatar
              v-if="msg.sender === 'user'"
              :size="36"
              style="background: #1890ff"
            >
              <template #icon><UserOutlined /></template>
            </a-avatar>
            <a-avatar
              v-else
              :size="36"
              style="background: #52c41a"
            >
              <template #icon><RobotOutlined /></template>
            </a-avatar>
          </div>

          <!-- 消息内容 -->
          <div class="message-body">
            <div class="message-bubble" :class="{ 'user-bubble': msg.sender === 'user' }">
              <div class="message-text">{{ msg.message }}</div>

              <!-- 音频播放按钮 -->
              <div
                v-if="msg.audioPath"
                class="message-audio"
                @click="playAudio(msg)"
              >
                <LoadingOutlined v-if="playingAudioId === msg.messageId" spin />
                <SoundOutlined v-else />
                <span>{{ playingAudioId === msg.messageId ? '播放中...' : '播放音频' }}</span>
              </div>
            </div>
            <div class="message-time">{{ msg.createTime }}</div>
          </div>
        </div>
      </template>

      <!-- 空状态 -->
      <div v-else class="chat-empty">
        <a-empty description="暂无对话记录" />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.user-chat {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 56px - 32px);
  background: var(--ant-color-bg-container);
  border-radius: 8px;
  overflow: hidden;
}

// 顶部栏
.chat-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--ant-color-border-secondary);
  flex-shrink: 0;

  .back-btn {
    font-size: 18px;
  }

  .chat-header-info {
    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: var(--ant-color-text);
    }

    .chat-role {
      font-size: 12px;
      color: var(--ant-color-text-secondary);
    }
  }
}

// 对话容器
.chat-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

// 加载提示
.loading-more,
.no-more {
  text-align: center;
  padding: 8px;
  color: var(--ant-color-text-tertiary);
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.chat-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

// 消息行
.message-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  max-width: 80%;

  &.is-user {
    align-self: flex-end;
    flex-direction: row-reverse;

    .message-body {
      align-items: flex-end;
    }
  }
}

// 消息头像
.message-avatar {
  flex-shrink: 0;
}

// 消息体
.message-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

// 消息气泡
.message-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--ant-color-bg-layout);
  color: var(--ant-color-text);
  word-break: break-word;
  line-height: 1.6;

  &.user-bubble {
    background: var(--ant-color-primary);
    color: #fff;

    .message-audio {
      color: rgba(255, 255, 255, 0.85);
      border-top-color: rgba(255, 255, 255, 0.2);

      &:hover {
        color: #fff;
      }
    }
  }
}

.message-text {
  font-size: 14px;
  white-space: pre-wrap;
}

// 音频按钮
.message-audio {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--ant-color-border-secondary);
  font-size: 12px;
  color: var(--ant-color-primary);
  cursor: pointer;
  transition: color 0.2s;

  &:hover {
    color: var(--ant-color-primary-hover);
  }
}

// 消息时间
.message-time {
  font-size: 11px;
  color: var(--ant-color-text-quaternary);
  padding: 0 4px;
}

// 移动端适配
@media (max-width: 768px) {
  .user-chat {
    // 底部 Tab Bar 占用空间
    height: calc(100vh - 56px - 24px - 56px - env(safe-area-inset-bottom));
    border-radius: 0;
  }

  .message-row {
    max-width: 90%;
  }

  .chat-container {
    padding: 12px;
  }
}
</style>

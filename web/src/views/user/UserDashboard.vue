<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useAvatar } from '@/composables/useAvatar'
import { queryDevices } from '@/services/device'
import { queryMessages } from '@/services/message'
import {
  LaptopOutlined,
  WifiOutlined,
  MessageOutlined,
  RightOutlined,
} from '@ant-design/icons-vue'
import type { Device } from '@/types/device'
import type { Message } from '@/types/message'

const router = useRouter()
const userStore = useUserStore()
const { getAvatarUrl } = useAvatar()

const userInfo = computed(() => userStore.userInfo)

// 统计数据
const loading = ref(true)
const totalDevices = ref(0)
const onlineDevices = ref(0)
const todayMessages = ref(0)

// 最近设备
const recentDevices = ref<Device[]>([])

// 最近对话
const recentMessages = ref<Message[]>([])
const messagesLoading = ref(false)

/**
 * 加载统计数据和设备列表
 */
async function loadDashboardData() {
  loading.value = true
  try {
    // 加载设备列表
    const deviceRes = await queryDevices({ start: 1, limit: 50 })
    if (deviceRes.code === 200 && deviceRes.data) {
      const devices = deviceRes.data.list || []
      totalDevices.value = deviceRes.data.total || devices.length
      onlineDevices.value = devices.filter((d: Device) => d.state === '1' || d.state === '2').length
      recentDevices.value = devices.slice(0, 4)
    }

    // 加载最近消息
    messagesLoading.value = true
    const msgRes = await queryMessages({ start: 1, limit: 5 })
    if (msgRes.code === 200 && msgRes.data) {
      recentMessages.value = msgRes.data.list || []
    }

    // 查询今日对话数（使用日期过滤）
    const today = new Date()
    const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
    const todayMsgRes = await queryMessages({ start: 1, limit: 1, startTime: todayStr, endTime: todayStr })
    if (todayMsgRes.code === 200 && todayMsgRes.data) {
      todayMessages.value = todayMsgRes.data.total || 0
    }
  } catch (error) {
    console.error('加载仪表盘数据失败:', error)
  } finally {
    loading.value = false
    messagesLoading.value = false
  }
}

/**
 * 获取设备状态显示信息
 */
function getDeviceStatus(state?: string) {
  if (state === '1') return { text: '在线', color: '#52c41a' }
  if (state === '2') return { text: '待机', color: '#1890ff' }
  return { text: '离线', color: '#d9d9d9' }
}

onMounted(() => {
  loadDashboardData()
})
</script>

<template>
  <div class="user-dashboard">
    <!-- 欢迎区域 -->
    <div class="welcome-section">
      <a-avatar :src="getAvatarUrl(userInfo?.avatar)" :size="48">
        <template #icon>
          <span style="font-size: 24px">{{ (userInfo?.name || userInfo?.username || '用户')[0] }}</span>
        </template>
      </a-avatar>
      <div class="welcome-text">
        <h2>你好，{{ userInfo?.name || userInfo?.username || '用户' }}</h2>
        <p>欢迎回来，查看你的设备和对话动态</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <a-row :gutter="[16, 16]" class="stats-row">
      <a-col :xs="8" :sm="8" :md="8">
        <a-card class="stat-card" :bordered="false" :loading="loading" @click="router.push('/u/devices')">
          <div class="stat-content">
            <div class="stat-icon" style="background: rgba(24, 144, 255, 0.1); color: #1890ff">
              <LaptopOutlined />
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ totalDevices }}</div>
              <div class="stat-label">设备总数</div>
            </div>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="8" :sm="8" :md="8">
        <a-card class="stat-card" :bordered="false" :loading="loading">
          <div class="stat-content">
            <div class="stat-icon" style="background: rgba(82, 196, 26, 0.1); color: #52c41a">
              <WifiOutlined />
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ onlineDevices }}</div>
              <div class="stat-label">在线设备</div>
            </div>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="8" :sm="8" :md="8">
        <a-card class="stat-card" :bordered="false" :loading="loading">
          <div class="stat-content">
            <div class="stat-icon" style="background: rgba(114, 46, 209, 0.1); color: #722ed1">
              <MessageOutlined />
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ todayMessages }}</div>
              <div class="stat-label">今日对话</div>
            </div>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 最近设备 -->
    <a-card
      :bordered="false"
      class="section-card"
    >
      <template #title>
        <span>我的设备</span>
      </template>
      <template #extra>
        <a @click="router.push('/u/devices')">
          查看全部 <RightOutlined />
        </a>
      </template>

      <a-spin :spinning="loading">
        <div v-if="recentDevices.length === 0 && !loading" class="empty-tip">
          <a-empty description="暂无设备，请在设备页面添加" />
        </div>
        <a-row v-else :gutter="[16, 16]">
          <a-col
            v-for="device in recentDevices"
            :key="device.deviceId"
            :xs="24"
            :sm="12"
            :md="12"
            :lg="6"
          >
            <a-card
              hoverable
              class="device-mini-card"
              @click="router.push(`/u/chat/${device.deviceId}`)"
            >
              <div class="device-mini-info">
                <a-avatar :src="getAvatarUrl(device.avatar)" :size="40">
                  <template #icon><LaptopOutlined /></template>
                </a-avatar>
                <div class="device-mini-text">
                  <div class="device-mini-name">{{ device.deviceName || device.deviceId }}</div>
                  <div class="device-mini-role">{{ device.roleName || '未分配角色' }}</div>
                </div>
                <div class="device-mini-status">
                  <a-badge :color="getDeviceStatus(device.state).color" :text="getDeviceStatus(device.state).text" />
                </div>
              </div>
            </a-card>
          </a-col>
        </a-row>
      </a-spin>
    </a-card>

    <!-- 最近对话 -->
    <a-card
      :bordered="false"
      class="section-card"
    >
      <template #title>
        <span>最近对话</span>
      </template>

      <a-spin :spinning="messagesLoading">
        <div v-if="recentMessages.length === 0 && !messagesLoading" class="empty-tip">
          <a-empty description="暂无对话记录" />
        </div>
        <a-list v-else :data-source="recentMessages" item-layout="horizontal">
          <template #renderItem="{ item }">
            <a-list-item class="message-item" @click="router.push(`/u/chat/${item.deviceId}`)">
              <a-list-item-meta>
                <template #avatar>
                  <a-avatar
                    :style="{
                      background: item.sender === 'user' ? '#1890ff' : '#52c41a',
                    }"
                    :size="36"
                  >
                    {{ item.sender === 'user' ? '我' : 'AI' }}
                  </a-avatar>
                </template>
                <template #title>
                  <span class="message-device">{{ item.deviceName || item.deviceId }}</span>
                  <span class="message-time">{{ item.createTime }}</span>
                </template>
                <template #description>
                  <span class="message-content">{{ item.message }}</span>
                </template>
              </a-list-item-meta>
            </a-list-item>
          </template>
        </a-list>
      </a-spin>
    </a-card>
  </div>
</template>

<style scoped lang="scss">
.user-dashboard {
  padding-bottom: 16px;
}

// 欢迎区域
.welcome-section {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  padding: 24px;
  background: var(--ant-color-bg-container);
  border-radius: 8px;

  .welcome-text {
    h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 600;
      color: var(--ant-color-text);
    }

    p {
      margin: 4px 0 0;
      color: var(--ant-color-text-secondary);
      font-size: 14px;
    }
  }
}

// 统计卡片
.stats-row {
  margin-bottom: 16px;
}

.stat-card {
  cursor: pointer;
  transition: all 0.2s;
  border-radius: 8px;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  }

  .stat-content {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .stat-icon {
    width: 44px;
    height: 44px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    flex-shrink: 0;
  }

  .stat-info {
    .stat-value {
      font-size: 24px;
      font-weight: 600;
      line-height: 1.2;
      color: var(--ant-color-text);
    }

    .stat-label {
      font-size: 13px;
      color: var(--ant-color-text-secondary);
      margin-top: 2px;
    }
  }
}

// 区块卡片
.section-card {
  margin-bottom: 16px;
  border-radius: 8px;
}

// 设备迷你卡片
.device-mini-card {
  border-radius: 8px;

  :deep(.ant-card-body) {
    padding: 16px;
  }

  .device-mini-info {
    display: flex;
    align-items: center;
    gap: 12px;

    .device-mini-text {
      flex: 1;
      min-width: 0;

      .device-mini-name {
        font-size: 14px;
        font-weight: 500;
        color: var(--ant-color-text);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .device-mini-role {
        font-size: 12px;
        color: var(--ant-color-text-secondary);
        margin-top: 2px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }
}

// 空提示
.empty-tip {
  padding: 32px 0;
}

// 消息列表
.message-item {
  cursor: pointer;
  transition: background 0.2s;
  padding: 12px 0;

  &:hover {
    background: var(--ant-color-bg-text-hover);
  }

  .message-device {
    font-weight: 500;
    font-size: 14px;
  }

  .message-time {
    font-size: 12px;
    color: var(--ant-color-text-tertiary);
    margin-left: 12px;
  }

  .message-content {
    display: -webkit-box;
    -webkit-line-clamp: 1;
    -webkit-box-orient: vertical;
    overflow: hidden;
    color: var(--ant-color-text-secondary);
  }
}

// 移动端适配
@media (max-width: 768px) {
  .welcome-section {
    padding: 16px;

    .welcome-text {
      h2 {
        font-size: 18px;
      }
    }
  }

  .stat-card {
    .stat-icon {
      width: 36px;
      height: 36px;
      font-size: 16px;
    }

    .stat-info {
      .stat-value {
        font-size: 20px;
      }

      .stat-label {
        font-size: 12px;
      }
    }
  }
}
</style>

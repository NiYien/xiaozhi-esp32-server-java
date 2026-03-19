<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useAvatar } from '@/composables/useAvatar'
import { queryDevices, updateDevice, addDevice } from '@/services/device'
import { queryRoles } from '@/services/role'
import {
  LaptopOutlined,
  MessageOutlined,
  SwapOutlined,
  InfoCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  WifiOutlined,
  EnvironmentOutlined,
  ClockCircleOutlined,
  AppstoreOutlined,
  CodeOutlined,
} from '@ant-design/icons-vue'
import type { Device } from '@/types/device'
import type { Role } from '@/types/role'

const router = useRouter()
const { getAvatarUrl } = useAvatar()

// 数据
const loading = ref(false)
const devices = ref<Device[]>([])
const roles = ref<Role[]>([])

// 添加设备
const addDeviceVisible = ref(false)
const addDeviceCode = ref('')
const addDeviceLoading = ref(false)

// 切换角色弹窗
const roleModalVisible = ref(false)
const currentDevice = ref<Device | null>(null)
const selectedRoleId = ref<number | undefined>(undefined)
const roleChangeLoading = ref(false)

// 设备详情弹窗
const detailVisible = ref(false)
const detailDevice = ref<Device | null>(null)

/**
 * 加载设备列表
 */
async function loadDevices() {
  loading.value = true
  try {
    const res = await queryDevices({ start: 1, limit: 100 })
    if (res.code === 200 && res.data) {
      devices.value = res.data.list || []
    }
  } catch (error) {
    console.error('加载设备列表失败:', error)
    message.error('加载设备列表失败')
  } finally {
    loading.value = false
  }
}

/**
 * 加载角色列表
 */
async function loadRoles() {
  try {
    const res = await queryRoles({})
    if (res.code === 200 && res.data) {
      roles.value = res.data.list || []
    }
  } catch (error) {
    console.error('加载角色列表失败:', error)
  }
}

/**
 * 获取设备状态
 */
function getDeviceStatus(state?: string) {
  if (state === '1') return { text: '在线', color: '#52c41a' }
  if (state === '2') return { text: '待机', color: '#1890ff' }
  return { text: '离线', color: '#d9d9d9' }
}

/**
 * 打开切换角色弹窗
 */
function openRoleModal(device: Device) {
  currentDevice.value = device
  selectedRoleId.value = device.roleId
  roleModalVisible.value = true
}

/**
 * 确认切换角色
 */
async function confirmRoleChange() {
  if (!currentDevice.value || !selectedRoleId.value) return

  roleChangeLoading.value = true
  try {
    const res = await updateDevice({
      deviceId: currentDevice.value.deviceId,
      roleId: selectedRoleId.value,
    })
    if (res.code === 200) {
      message.success('角色切换成功')
      roleModalVisible.value = false
      await loadDevices()
    } else {
      message.error(res.message || '角色切换失败')
    }
  } catch (error) {
    console.error('切换角色失败:', error)
    message.error('切换角色失败')
  } finally {
    roleChangeLoading.value = false
  }
}

/**
 * 查看设备详情
 */
function showDeviceDetail(device: Device) {
  detailDevice.value = device
  detailVisible.value = true
}

/**
 * 查看对话历史
 */
function goToChat(device: Device) {
  router.push(`/u/chat/${device.deviceId}`)
}

/**
 * 添加设备
 */
async function handleAddDevice() {
  if (!addDeviceCode.value) {
    message.warning('请输入设备激活码')
    return
  }

  addDeviceLoading.value = true
  try {
    const res = await addDevice(addDeviceCode.value)
    if (res.code === 200) {
      message.success('设备添加成功')
      addDeviceCode.value = ''
      addDeviceVisible.value = false
      await loadDevices()
    } else {
      message.error(res.message || '添加设备失败')
    }
  } catch (error) {
    console.error('添加设备失败:', error)
    message.error('添加设备失败')
  } finally {
    addDeviceLoading.value = false
  }
}

onMounted(() => {
  loadDevices()
  loadRoles()
})
</script>

<template>
  <div class="user-devices">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>我的设备</h2>
      <a-space>
        <a-button @click="loadDevices" :loading="loading">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button type="primary" @click="addDeviceVisible = true">
          <template #icon><PlusOutlined /></template>
          添加设备
        </a-button>
      </a-space>
    </div>

    <!-- 设备卡片列表 -->
    <a-spin :spinning="loading">
      <!-- 无设备提示 -->
      <div v-if="devices.length === 0 && !loading" class="empty-state">
        <a-empty>
          <template #description>
            <div>
              <p style="font-size: 16px; margin-bottom: 8px">暂无绑定设备</p>
              <p style="color: var(--ant-color-text-tertiary)">
                请使用设备激活码添加你的第一台设备
              </p>
            </div>
          </template>
          <a-button type="primary" @click="addDeviceVisible = true">
            <template #icon><PlusOutlined /></template>
            添加设备
          </a-button>
        </a-empty>
      </div>

      <!-- 设备卡片网格 -->
      <a-row v-else :gutter="[16, 16]">
        <a-col
          v-for="device in devices"
          :key="device.deviceId"
          :xs="24"
          :sm="12"
          :md="12"
          :lg="8"
          :xl="6"
        >
          <a-card hoverable class="device-card" @click="showDeviceDetail(device)">
            <div class="device-card-content">
              <!-- 设备头部 -->
              <div class="device-card-header">
                <a-avatar :src="getAvatarUrl(device.avatar)" :size="48" class="device-avatar">
                  <template #icon><LaptopOutlined /></template>
                </a-avatar>
                <a-badge
                  :color="getDeviceStatus(device.state).color"
                  :text="getDeviceStatus(device.state).text"
                  class="device-status"
                />
              </div>

              <!-- 设备名称 -->
              <div class="device-card-name">
                {{ device.deviceName || '未命名设备' }}
              </div>

              <!-- 角色信息 -->
              <div class="device-card-role">
                <a-tag v-if="device.roleName" color="blue">{{ device.roleName }}</a-tag>
                <a-tag v-else>未分配角色</a-tag>
              </div>

              <!-- 设备信息 -->
              <div class="device-card-meta">
                <span v-if="device.chipModelName" class="meta-item">
                  <AppstoreOutlined /> {{ device.chipModelName }}
                </span>
                <span v-if="device.updateTime" class="meta-item">
                  <ClockCircleOutlined /> {{ device.updateTime }}
                </span>
              </div>

              <!-- 操作按钮 -->
              <div class="device-card-actions" @click.stop>
                <a-button
                  type="text"
                  size="small"
                  @click="goToChat(device)"
                >
                  <template #icon><MessageOutlined /></template>
                  对话
                </a-button>
                <a-button
                  type="text"
                  size="small"
                  @click="openRoleModal(device)"
                >
                  <template #icon><SwapOutlined /></template>
                  切换角色
                </a-button>
                <a-button
                  type="text"
                  size="small"
                  @click="showDeviceDetail(device)"
                >
                  <template #icon><InfoCircleOutlined /></template>
                  详情
                </a-button>
              </div>
            </div>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>

    <!-- 添加设备弹窗 -->
    <a-modal
      v-model:open="addDeviceVisible"
      title="添加设备"
      :confirm-loading="addDeviceLoading"
      @ok="handleAddDevice"
    >
      <div style="padding: 16px 0">
        <p style="margin-bottom: 16px; color: var(--ant-color-text-secondary)">
          请输入设备上显示的激活码来绑定设备
        </p>
        <a-input
          v-model:value="addDeviceCode"
          placeholder="请输入设备激活码"
          size="large"
          allow-clear
          @press-enter="handleAddDevice"
        />
      </div>
    </a-modal>

    <!-- 切换角色弹窗 -->
    <a-modal
      v-model:open="roleModalVisible"
      title="切换角色"
      :confirm-loading="roleChangeLoading"
      @ok="confirmRoleChange"
    >
      <div style="padding: 16px 0">
        <p style="margin-bottom: 16px; color: var(--ant-color-text-secondary)">
          为设备「{{ currentDevice?.deviceName || currentDevice?.deviceId }}」选择新角色
        </p>
        <a-select
          v-model:value="selectedRoleId"
          style="width: 100%"
          size="large"
          placeholder="请选择角色"
        >
          <a-select-option
            v-for="role in roles"
            :key="role.roleId"
            :value="role.roleId"
          >
            <div style="display: flex; align-items: center; gap: 8px">
              <a-avatar :src="getAvatarUrl(role.avatar)" :size="24">
                {{ role.roleName[0] }}
              </a-avatar>
              <span>{{ role.roleName }}</span>
              <span v-if="role.roleDesc" style="color: var(--ant-color-text-tertiary); font-size: 12px">
                - {{ role.roleDesc }}
              </span>
            </div>
          </a-select-option>
        </a-select>
      </div>
    </a-modal>

    <!-- 设备详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      :title="detailDevice?.deviceName || '设备详情'"
      :footer="null"
      width="480px"
    >
      <div v-if="detailDevice" class="device-detail">
        <div class="detail-header">
          <a-avatar :src="getAvatarUrl(detailDevice.avatar)" :size="64">
            <template #icon><LaptopOutlined /></template>
          </a-avatar>
          <div class="detail-header-info">
            <h3>{{ detailDevice.deviceName || '未命名设备' }}</h3>
            <a-badge
              :color="getDeviceStatus(detailDevice.state).color"
              :text="getDeviceStatus(detailDevice.state).text"
            />
          </div>
        </div>

        <a-descriptions :column="1" bordered size="small" class="detail-desc">
          <a-descriptions-item label="设备ID">
            {{ detailDevice.deviceId }}
          </a-descriptions-item>
          <a-descriptions-item label="当前角色">
            {{ detailDevice.roleName || '未分配' }}
          </a-descriptions-item>
          <a-descriptions-item v-if="detailDevice.chipModelName" label="设备类型">
            {{ detailDevice.chipModelName }}
          </a-descriptions-item>
          <a-descriptions-item v-if="detailDevice.type" label="固件类型">
            {{ detailDevice.type }}
          </a-descriptions-item>
          <a-descriptions-item v-if="detailDevice.version" label="固件版本">
            <CodeOutlined /> {{ detailDevice.version }}
          </a-descriptions-item>
          <a-descriptions-item v-if="detailDevice.ip" label="IP 地址">
            {{ detailDevice.ip }}
          </a-descriptions-item>
          <a-descriptions-item v-if="detailDevice.wifiName" label="WiFi 名称">
            <WifiOutlined /> {{ detailDevice.wifiName }}
          </a-descriptions-item>
          <a-descriptions-item v-if="detailDevice.location" label="位置">
            <EnvironmentOutlined /> {{ detailDevice.location }}
          </a-descriptions-item>
          <a-descriptions-item v-if="detailDevice.updateTime" label="最后活跃">
            {{ detailDevice.updateTime }}
          </a-descriptions-item>
          <a-descriptions-item v-if="detailDevice.createTime" label="添加时间">
            {{ detailDevice.createTime }}
          </a-descriptions-item>
        </a-descriptions>

        <div class="detail-actions">
          <a-button type="primary" @click="goToChat(detailDevice); detailVisible = false">
            <template #icon><MessageOutlined /></template>
            查看对话
          </a-button>
          <a-button @click="openRoleModal(detailDevice); detailVisible = false">
            <template #icon><SwapOutlined /></template>
            切换角色
          </a-button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped lang="scss">
.user-devices {
  padding-bottom: 16px;
}

// 页面标题
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;

  h2 {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    color: var(--ant-color-text);
  }
}

// 空状态
.empty-state {
  padding: 60px 0;
  text-align: center;
}

// 设备卡片
.device-card {
  border-radius: 10px;
  overflow: hidden;
  transition: all 0.2s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  }

  :deep(.ant-card-body) {
    padding: 20px;
  }
}

.device-card-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.device-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.device-card-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--ant-color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-card-role {
  display: flex;
  gap: 4px;
}

.device-card-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--ant-color-text-tertiary);

  .meta-item {
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

.device-card-actions {
  display: flex;
  gap: 4px;
  margin-top: 4px;
  padding-top: 12px;
  border-top: 1px solid var(--ant-color-border-secondary);
}

// 设备详情
.device-detail {
  .detail-header {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 24px;

    .detail-header-info {
      h3 {
        margin: 0 0 4px;
        font-size: 18px;
        font-weight: 600;
      }
    }
  }

  .detail-desc {
    margin-bottom: 24px;
  }

  .detail-actions {
    display: flex;
    gap: 12px;
    justify-content: center;
  }
}

// 移动端适配
@media (max-width: 768px) {
  .page-header {
    flex-wrap: wrap;
    gap: 12px;

    h2 {
      font-size: 18px;
    }
  }

  .device-card-actions {
    flex-wrap: wrap;
  }
}
</style>

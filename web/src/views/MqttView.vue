<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { message, Modal } from 'ant-design-vue'
import {
  ReloadOutlined,
  SendOutlined,
  SoundOutlined,
  NotificationOutlined,
  CloudUploadOutlined,
  SettingOutlined,
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  TeamOutlined,
} from '@ant-design/icons-vue'
import {
  getMqttConfig,
  getMqttDevices,
  wakeupDevice,
  notifyDevice,
  broadcast,
  pushOta,
  broadcastOta,
  getSensorHistory,
  getDeviceGroups,
  createDeviceGroup,
  updateDeviceGroup,
  deleteDeviceGroup,
  getGroupDevices,
  addGroupMembers,
  removeGroupMember,
  wakeupGroup,
  notifyGroup,
  pushDeviceConfig,
} from '@/services/mqtt'
import type { MqttConfig, MqttDeviceStatus, SensorData, DeviceGroup, MqttSendLog } from '@/types/mqtt'

const { t } = useI18n()

// ========== 基础状态 ==========
const mqttConfig = ref<MqttConfig | null>(null)
const deviceList = ref<MqttDeviceStatus[]>([])
const configLoading = ref(false)
const devicesLoading = ref(false)
const activeTab = ref('devices')

// ========== AEC 状态 ==========
const aecStatus = ref<{ enabled: boolean; streamDelayMs: number; noiseSuppressionLevel: string } | null>(null)
const aecLoading = ref(false)

// ========== 操作表单 ==========
const wakeupForm = ref({ deviceId: '', message: '' })
const notifyForm = ref({ deviceId: '', text: '' })
const broadcastForm = ref({ text: '' })
const wakeupLoading = ref(false)
const notifyLoading = ref(false)
const broadcastLoading = ref(false)

// ========== OTA 推送 ==========
const otaForm = reactive({ deviceId: '', version: '', url: '', releaseNotes: '', force: false })
const otaLoading = ref(false)

// ========== 传感器数据 ==========
const sensorModalVisible = ref(false)
const sensorDeviceId = ref('')
const sensorData = ref<SensorData[]>([])
const sensorLoading = ref(false)

// ========== 分组管理 ==========
const groupList = ref<DeviceGroup[]>([])
const groupLoading = ref(false)
const groupModalVisible = ref(false)
const groupModalTitle = ref('')
const groupForm = reactive({ groupId: 0, groupName: '', description: '' })
const groupMemberModalVisible = ref(false)
const groupMemberDevices = ref<string[]>([])
const groupMemberGroupId = ref(0)
const groupMemberLoading = ref(false)
const addMemberDeviceId = ref('')
const groupNotifyForm = reactive({ groupId: 0, text: '' })
const groupNotifyModalVisible = ref(false)

// ========== 远程配置 ==========
const configModalVisible = ref(false)
const configDeviceId = ref('')
const configForm = reactive({ volume: 50, wakeWord: '', ledBrightness: 50, timezone: 'Asia/Shanghai', language: '' })
const configLoading2 = ref(false)

// ========== 发送记录 ==========
const sendLogs = ref<MqttSendLog[]>([])

// ========== 自动轮询 ==========
let pollingTimer: ReturnType<typeof setInterval> | null = null
const mqttEnabled = computed(() => mqttConfig.value?.enabled === true)

// 添加发送记录
function addSendLog(type: string, target: string, msg: string, success: boolean) {
  sendLogs.value.unshift({
    time: new Date().toLocaleTimeString(),
    type,
    target,
    message: msg,
    success,
  })
  if (sendLogs.value.length > 50) {
    sendLogs.value = sendLogs.value.slice(0, 50)
  }
}

// ========== 设备列表表格列 ==========
const columns = computed(() => [
  { title: t('mqtt.deviceId'), dataIndex: 'deviceId', width: 160, align: 'center' as const },
  { title: t('mqtt.deviceName'), dataIndex: 'deviceName', width: 100, align: 'center' as const },
  { title: t('mqtt.state'), dataIndex: 'state', width: 80, align: 'center' as const },
  { title: t('mqtt.channels'), dataIndex: 'channels', width: 120, align: 'center' as const },
  { title: t('mqtt.temperature'), dataIndex: 'temperature', width: 80, align: 'center' as const },
  { title: t('mqtt.battery'), dataIndex: 'battery', width: 80, align: 'center' as const },
  { title: t('mqtt.lastHeartbeat'), dataIndex: 'lastHeartbeat', width: 160, align: 'center' as const },
  { title: t('mqtt.actions'), dataIndex: 'actions', width: 280, align: 'center' as const },
])

// ========== 加载数据 ==========
async function loadConfig() {
  configLoading.value = true
  try {
    const res = await getMqttConfig()
    if (res.code === 200) mqttConfig.value = res.data
  } catch (error) {
    console.error('加载MQTT配置失败:', error)
  } finally {
    configLoading.value = false
  }
}

async function loadDevices() {
  devicesLoading.value = true
  try {
    const res = await getMqttDevices()
    if (res.code === 200) deviceList.value = res.data as unknown as MqttDeviceStatus[]
  } catch (error) {
    console.error('加载设备状态失败:', error)
  } finally {
    devicesLoading.value = false
  }
}

async function loadGroups() {
  groupLoading.value = true
  try {
    const res = await getDeviceGroups()
    if (res.code === 200) groupList.value = (res.data || []) as unknown as DeviceGroup[]
  } catch (error) {
    console.error('加载分组列表失败:', error)
  } finally {
    groupLoading.value = false
  }
}

async function loadAecStatus() {
  aecLoading.value = true
  try {
    const res = await fetch('/api/config/aec-status')
    const data = await res.json()
    if (data.code === 200) aecStatus.value = data.data
  } catch (error) {
    console.error('加载AEC状态失败:', error)
  } finally {
    aecLoading.value = false
  }
}

async function handleRefresh() {
  await Promise.all([loadConfig(), loadDevices()])
  message.success(t('mqtt.refreshSuccess'))
}

// ========== 唤醒/通知/广播 ==========
async function handleWakeup(deviceId?: string) {
  const targetId = deviceId || wakeupForm.value.deviceId
  if (!targetId) { message.warning(t('mqtt.pleaseEnterDeviceId')); return }
  wakeupLoading.value = true
  try {
    const res = await wakeupDevice(targetId, wakeupForm.value.message || undefined)
    if (res.code === 200) {
      message.success(res.message || t('mqtt.wakeupSuccess'))
      addSendLog('wakeup', targetId, wakeupForm.value.message || '', true)
      wakeupForm.value = { deviceId: '', message: '' }
      await loadDevices()
    } else {
      message.error(res.message || t('mqtt.wakeupFailed'))
      addSendLog('wakeup', targetId, res.message || '', false)
    }
  } catch { message.error(t('mqtt.wakeupFailed')); addSendLog('wakeup', targetId, '', false) }
  finally { wakeupLoading.value = false }
}

async function handleNotify(deviceId?: string) {
  const targetId = deviceId || notifyForm.value.deviceId
  const text = notifyForm.value.text
  if (!targetId) { message.warning(t('mqtt.pleaseEnterDeviceId')); return }
  if (!text) { message.warning(t('mqtt.pleaseEnterText')); return }
  notifyLoading.value = true
  try {
    const res = await notifyDevice(targetId, text)
    if (res.code === 200) {
      message.success(res.message || t('mqtt.notifySuccess'))
      addSendLog('notify', targetId, text, true)
      notifyForm.value = { deviceId: '', text: '' }
    } else {
      message.error(res.message || t('mqtt.notifyFailed'))
      addSendLog('notify', targetId, text, false)
    }
  } catch { message.error(t('mqtt.notifyFailed')) }
  finally { notifyLoading.value = false }
}

function handleBroadcast() {
  if (!broadcastForm.value.text) { message.warning(t('mqtt.pleaseEnterText')); return }
  Modal.confirm({
    title: t('mqtt.broadcastConfirmTitle'),
    content: t('mqtt.broadcastConfirmContent'),
    okText: t('common.confirm'),
    cancelText: t('common.cancel'),
    onOk: async () => {
      broadcastLoading.value = true
      try {
        const res = await broadcast(broadcastForm.value.text)
        if (res.code === 200) {
          message.success(res.message || t('mqtt.broadcastSuccess'))
          addSendLog('broadcast', 'all', broadcastForm.value.text, true)
          broadcastForm.value = { text: '' }
        } else {
          message.error(res.message || t('mqtt.broadcastFailed'))
          addSendLog('broadcast', 'all', broadcastForm.value.text, false)
        }
      } catch { message.error(t('mqtt.broadcastFailed')) }
      finally { broadcastLoading.value = false }
    },
  })
}

// ========== OTA 推送 ==========
async function handlePushOta() {
  if (!otaForm.version || !otaForm.url) { message.warning(t('mqtt.otaFormRequired')); return }
  otaLoading.value = true
  try {
    let res
    if (otaForm.deviceId) {
      res = await pushOta(otaForm.deviceId, otaForm.version, otaForm.url, otaForm.releaseNotes, otaForm.force)
    } else {
      res = await broadcastOta(otaForm.version, otaForm.url, otaForm.releaseNotes, otaForm.force)
    }
    if (res.code === 200) {
      message.success(res.message || t('mqtt.otaPushSuccess'))
      addSendLog('ota', otaForm.deviceId || 'broadcast', `v${otaForm.version}`, true)
      otaForm.deviceId = ''; otaForm.version = ''; otaForm.url = ''; otaForm.releaseNotes = ''; otaForm.force = false
    } else {
      message.error(res.message || t('mqtt.otaPushFailed'))
      addSendLog('ota', otaForm.deviceId || 'broadcast', `v${otaForm.version}`, false)
    }
  } catch { message.error(t('mqtt.otaPushFailed')) }
  finally { otaLoading.value = false }
}

// ========== 传感器数据 ==========
async function showSensorHistory(deviceId: string) {
  sensorDeviceId.value = deviceId
  sensorModalVisible.value = true
  sensorLoading.value = true
  try {
    const res = await getSensorHistory(deviceId)
    if (res.code === 200) sensorData.value = (res.data || []) as unknown as SensorData[]
  } catch { message.error(t('mqtt.loadSensorFailed')) }
  finally { sensorLoading.value = false }
}

// ========== 分组管理 ==========
function showCreateGroup() {
  groupModalTitle.value = t('mqtt.createGroup')
  groupForm.groupId = 0
  groupForm.groupName = ''
  groupForm.description = ''
  groupModalVisible.value = true
}

function showEditGroup(group: DeviceGroup) {
  groupModalTitle.value = t('mqtt.editGroup')
  groupForm.groupId = group.groupId
  groupForm.groupName = group.groupName
  groupForm.description = group.description || ''
  groupModalVisible.value = true
}

async function handleSaveGroup() {
  if (!groupForm.groupName) { message.warning(t('mqtt.groupNameRequired')); return }
  try {
    if (groupForm.groupId) {
      await updateDeviceGroup(groupForm.groupId, { groupName: groupForm.groupName, description: groupForm.description })
    } else {
      await createDeviceGroup(groupForm.groupName, groupForm.description)
    }
    message.success(t('common.success'))
    groupModalVisible.value = false
    await loadGroups()
  } catch { message.error(t('common.fail')) }
}

function handleDeleteGroup(group: DeviceGroup) {
  Modal.confirm({
    title: t('mqtt.deleteGroupConfirm'),
    content: group.groupName,
    okText: t('common.confirm'),
    cancelText: t('common.cancel'),
    onOk: async () => {
      try {
        await deleteDeviceGroup(group.groupId)
        message.success(t('common.success'))
        await loadGroups()
      } catch { message.error(t('common.fail')) }
    },
  })
}

async function showGroupMembers(groupId: number) {
  groupMemberGroupId.value = groupId
  groupMemberModalVisible.value = true
  groupMemberLoading.value = true
  try {
    const res = await getGroupDevices(groupId)
    if (res.code === 200) groupMemberDevices.value = (res.data || []) as unknown as string[]
  } catch { message.error(t('common.fail')) }
  finally { groupMemberLoading.value = false }
}

async function handleAddMember() {
  if (!addMemberDeviceId.value) return
  try {
    await addGroupMembers(groupMemberGroupId.value, [addMemberDeviceId.value])
    message.success(t('common.success'))
    addMemberDeviceId.value = ''
    await showGroupMembers(groupMemberGroupId.value)
    await loadGroups()
  } catch { message.error(t('common.fail')) }
}

async function handleRemoveMember(deviceId: string) {
  try {
    await removeGroupMember(groupMemberGroupId.value, deviceId)
    message.success(t('common.success'))
    await showGroupMembers(groupMemberGroupId.value)
    await loadGroups()
  } catch { message.error(t('common.fail')) }
}

async function handleGroupWakeup(groupId: number) {
  try {
    const res = await wakeupGroup(groupId)
    if (res.code === 200) {
      message.success(res.message || t('mqtt.wakeupSuccess'))
      addSendLog('group_wakeup', `group:${groupId}`, '', true)
    } else {
      message.error(res.message || t('mqtt.wakeupFailed'))
    }
  } catch { message.error(t('mqtt.wakeupFailed')) }
}

function showGroupNotify(groupId: number) {
  groupNotifyForm.groupId = groupId
  groupNotifyForm.text = ''
  groupNotifyModalVisible.value = true
}

async function handleGroupNotify() {
  if (!groupNotifyForm.text) { message.warning(t('mqtt.pleaseEnterText')); return }
  try {
    const res = await notifyGroup(groupNotifyForm.groupId, groupNotifyForm.text)
    if (res.code === 200) {
      message.success(res.message || t('mqtt.notifySuccess'))
      addSendLog('group_notify', `group:${groupNotifyForm.groupId}`, groupNotifyForm.text, true)
      groupNotifyModalVisible.value = false
    } else {
      message.error(res.message || t('mqtt.notifyFailed'))
    }
  } catch { message.error(t('mqtt.notifyFailed')) }
}

// ========== 远程配置 ==========
function showConfigModal(deviceId: string) {
  configDeviceId.value = deviceId
  configForm.volume = 50
  configForm.wakeWord = ''
  configForm.ledBrightness = 50
  configForm.timezone = 'Asia/Shanghai'
  configForm.language = ''
  configModalVisible.value = true
}

async function handlePushConfig() {
  configLoading2.value = true
  try {
    // 只发送有值的配置项
    const config: Record<string, unknown> = {}
    if (configForm.volume !== null && configForm.volume !== undefined) config.volume = configForm.volume
    if (configForm.wakeWord) config.wakeWord = configForm.wakeWord
    if (configForm.ledBrightness !== null && configForm.ledBrightness !== undefined) config.ledBrightness = configForm.ledBrightness
    if (configForm.timezone) config.timezone = configForm.timezone
    if (configForm.language) config.language = configForm.language

    if (Object.keys(config).length === 0) { message.warning(t('mqtt.configEmpty')); return }

    const res = await pushDeviceConfig(configDeviceId.value, config)
    if (res.code === 200) {
      message.success(res.message || t('mqtt.configPushSuccess'))
      addSendLog('config', configDeviceId.value, JSON.stringify(config), true)
      configModalVisible.value = false
    } else {
      message.error(res.message || t('mqtt.configPushFailed'))
      addSendLog('config', configDeviceId.value, JSON.stringify(config), false)
    }
  } catch { message.error(t('mqtt.configPushFailed')) }
  finally { configLoading2.value = false }
}

// ========== 行操作 ==========
function handleRowWakeup(record: MqttDeviceStatus) {
  wakeupForm.value.deviceId = record.deviceId
  handleWakeup(record.deviceId)
}

function handleRowNotify(record: MqttDeviceStatus) {
  notifyForm.value.deviceId = record.deviceId
}

function getStateColor(state: string): string {
  switch (state) {
    case 'online': return 'green'
    case 'standby': return 'orange'
    default: return 'default'
  }
}

function getStateText(state: string): string {
  switch (state) {
    case 'online': return t('mqtt.stateOnline')
    case 'standby': return t('mqtt.stateStandby')
    default: return t('mqtt.stateOffline')
  }
}

function formatUptime(seconds: number | null): string {
  if (!seconds) return '-'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return `${h}h ${m}m`
}

function startPolling() {
  stopPolling()
  pollingTimer = setInterval(() => { loadDevices() }, 10000)
}
function stopPolling() {
  if (pollingTimer) { clearInterval(pollingTimer); pollingTimer = null }
}

onMounted(async () => {
  await loadConfig()
  loadAecStatus()
  if (mqttEnabled.value) {
    await loadDevices()
    await loadGroups()
    startPolling()
  }
})
onUnmounted(() => { stopPolling() })
</script>

<template>
  <div class="mqtt-container">
    <!-- 顶部状态卡片 -->
    <a-row :gutter="16" class="status-row">
      <a-col :xs="24" :sm="12">
        <a-card :bordered="false" :loading="configLoading">
          <template #title>{{ t('mqtt.connectionStatus') }}</template>
          <div v-if="mqttConfig">
            <a-badge
              :status="mqttConfig.connected ? 'success' : mqttConfig.enabled ? 'error' : 'default'"
              :text="mqttConfig.connected ? t('mqtt.connected') : mqttConfig.enabled ? t('mqtt.disconnected') : t('mqtt.notEnabled')"
              style="font-size: 16px"
            />
            <div v-if="!mqttConfig.enabled" style="margin-top: 12px">
              <a-alert type="info" show-icon>
                <template #message>{{ t('mqtt.setupGuideTitle') }}</template>
                <template #description>
                  <ol style="margin: 8px 0 0 0; padding-left: 20px; line-height: 2">
                    <li>{{ t('mqtt.setupStep1') }}<br/><code style="background: #f5f5f5; padding: 2px 6px; border-radius: 3px; font-size: 12px">docker run -d -p 1883:1883 eclipse-mosquitto:2 mosquitto -c /mosquitto-no-auth.conf</code></li>
                    <li>{{ t('mqtt.setupStep2') }}</li>
                    <li>{{ t('mqtt.setupStep3') }}</li>
                  </ol>
                </template>
              </a-alert>
            </div>
            <div v-else-if="!mqttConfig.connected" style="margin-top: 12px">
              <a-alert type="warning" show-icon>
                <template #message>{{ t('mqtt.connectFailTitle') }}</template>
                <template #description>
                  <ul style="margin: 8px 0 0 0; padding-left: 20px; line-height: 2">
                    <li>{{ t('mqtt.connectFailCheck1') }}</li>
                    <li>{{ t('mqtt.connectFailCheck2') }}</li>
                    <li>{{ t('mqtt.connectFailCheck3') }}</li>
                  </ul>
                </template>
              </a-alert>
            </div>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12">
        <a-card :bordered="false" :loading="configLoading">
          <template #title>{{ t('mqtt.configInfo') }}</template>
          <div v-if="mqttConfig && mqttConfig.enabled">
            <a-descriptions :column="1" size="small">
              <a-descriptions-item :label="t('mqtt.brokerUrl')">{{ mqttConfig.brokerUrl }}</a-descriptions-item>
              <a-descriptions-item :label="t('mqtt.topicPrefix')">{{ mqttConfig.topicPrefix }}</a-descriptions-item>
              <a-descriptions-item :label="t('mqtt.clientId')">{{ mqttConfig.clientId || '-' }}</a-descriptions-item>
              <a-descriptions-item :label="t('mqtt.keepAliveInterval')">{{ mqttConfig.keepAliveInterval }}s</a-descriptions-item>
            </a-descriptions>
          </div>
          <div v-else><a-empty :description="t('mqtt.notEnabled')" /></div>
        </a-card>
      </a-col>
    </a-row>

    <!-- AEC 回声消除状态 -->
    <a-row :gutter="16" class="status-row" style="margin-top: 16px">
      <a-col :xs="24" :sm="12">
        <a-card :bordered="false" :loading="aecLoading">
          <template #title>{{ t('mqtt.aecTitle') }}</template>
          <div v-if="aecStatus">
            <a-descriptions :column="1" size="small">
              <a-descriptions-item :label="t('mqtt.aecEnabled')">
                <a-badge :status="aecStatus.enabled ? 'success' : 'default'" :text="aecStatus.enabled ? t('common.enable') : t('common.disable')" />
              </a-descriptions-item>
              <a-descriptions-item :label="t('mqtt.aecStreamDelay')">{{ aecStatus.streamDelayMs }}ms</a-descriptions-item>
              <a-descriptions-item :label="t('mqtt.aecNoiseLevel')">{{ aecStatus.noiseSuppressionLevel }}</a-descriptions-item>
            </a-descriptions>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <template v-if="mqttEnabled">
      <!-- Tab 切换 -->
      <a-tabs v-model:activeKey="activeTab" style="margin-bottom: 16px">
        <!-- 设备管理 Tab -->
        <a-tab-pane key="devices" :tab="t('mqtt.deviceTab')">
          <!-- 操作区 -->
          <a-card :bordered="false" class="action-card">
            <template #title>{{ t('mqtt.operations') }}</template>
            <a-row :gutter="[16, 16]">
              <!-- 唤醒设备 -->
              <a-col :xs="24" :md="6">
                <a-card size="small" :title="t('mqtt.wakeupDevice')">
                  <a-space direction="vertical" style="width: 100%">
                    <a-input v-model:value="wakeupForm.deviceId" :placeholder="t('mqtt.pleaseEnterDeviceId')" allow-clear />
                    <a-input v-model:value="wakeupForm.message" :placeholder="t('mqtt.wakeupMessage')" allow-clear />
                    <a-button type="primary" :loading="wakeupLoading" @click="handleWakeup()" block>
                      <template #icon><SoundOutlined /></template>{{ t('mqtt.wakeup') }}
                    </a-button>
                  </a-space>
                </a-card>
              </a-col>
              <!-- 发送通知 -->
              <a-col :xs="24" :md="6">
                <a-card size="small" :title="t('mqtt.sendNotification')">
                  <a-space direction="vertical" style="width: 100%">
                    <a-input v-model:value="notifyForm.deviceId" :placeholder="t('mqtt.pleaseEnterDeviceId')" allow-clear />
                    <a-input v-model:value="notifyForm.text" :placeholder="t('mqtt.pleaseEnterText')" allow-clear />
                    <a-button type="primary" :loading="notifyLoading" @click="handleNotify()" block>
                      <template #icon><SendOutlined /></template>{{ t('mqtt.notify') }}
                    </a-button>
                  </a-space>
                </a-card>
              </a-col>
              <!-- 广播消息 -->
              <a-col :xs="24" :md="6">
                <a-card size="small" :title="t('mqtt.broadcastMessage')">
                  <a-space direction="vertical" style="width: 100%">
                    <a-textarea v-model:value="broadcastForm.text" :placeholder="t('mqtt.pleaseEnterBroadcastText')" :rows="2" allow-clear />
                    <a-button type="primary" danger :loading="broadcastLoading" @click="handleBroadcast" block>
                      <template #icon><NotificationOutlined /></template>{{ t('mqtt.broadcast') }}
                    </a-button>
                  </a-space>
                </a-card>
              </a-col>
              <!-- OTA 推送 -->
              <a-col :xs="24" :md="6">
                <a-card size="small" :title="t('mqtt.otaPush')">
                  <a-space direction="vertical" style="width: 100%">
                    <a-input v-model:value="otaForm.deviceId" :placeholder="t('mqtt.otaDeviceHint')" allow-clear />
                    <a-input v-model:value="otaForm.version" :placeholder="t('mqtt.otaVersion')" allow-clear />
                    <a-input v-model:value="otaForm.url" :placeholder="t('mqtt.otaUrl')" allow-clear />
                    <a-button type="primary" :loading="otaLoading" @click="handlePushOta" block>
                      <template #icon><CloudUploadOutlined /></template>{{ t('mqtt.otaPushBtn') }}
                    </a-button>
                  </a-space>
                </a-card>
              </a-col>
            </a-row>
          </a-card>

          <!-- 设备状态列表 -->
          <a-card :bordered="false" class="device-list-card">
            <template #title>{{ t('mqtt.deviceStatusList') }}</template>
            <template #extra>
              <a-button type="link" @click="handleRefresh">
                <template #icon><ReloadOutlined /></template>{{ t('common.refresh') }}
              </a-button>
            </template>
            <a-table :columns="columns" :data-source="deviceList" :loading="devicesLoading"
              :pagination="false" row-key="deviceId" :scroll="{ x: 'max-content' }" size="middle">
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'state'">
                  <a-tag :color="getStateColor(record.state)">{{ getStateText(record.state) }}</a-tag>
                </template>
                <template v-else-if="column.dataIndex === 'temperature'">
                  {{ record.temperature != null ? record.temperature + '\u00B0C' : '-' }}
                </template>
                <template v-else-if="column.dataIndex === 'battery'">
                  {{ record.battery != null ? record.battery + '%' : '-' }}
                </template>
                <template v-else-if="column.dataIndex === 'lastHeartbeat'">
                  {{ record.lastHeartbeat || '-' }}
                </template>
                <template v-else-if="column.dataIndex === 'actions'">
                  <a-space>
                    <a-button v-if="record.state === 'standby'" type="link" size="small" @click="handleRowWakeup(record)">
                      {{ t('mqtt.wakeup') }}
                    </a-button>
                    <a-button v-if="record.state === 'online' || record.state === 'standby'" type="link" size="small" @click="handleRowNotify(record)">
                      {{ t('mqtt.notify') }}
                    </a-button>
                    <a-button type="link" size="small" @click="showSensorHistory(record.deviceId)">
                      {{ t('mqtt.sensorHistory') }}
                    </a-button>
                    <a-button type="link" size="small" @click="showConfigModal(record.deviceId)">
                      <template #icon><SettingOutlined /></template>{{ t('mqtt.remoteConfig') }}
                    </a-button>
                  </a-space>
                </template>
              </template>
            </a-table>
          </a-card>
        </a-tab-pane>

        <!-- 分组管理 Tab -->
        <a-tab-pane key="groups" :tab="t('mqtt.groupTab')">
          <a-card :bordered="false">
            <template #title>{{ t('mqtt.groupManagement') }}</template>
            <template #extra>
              <a-space>
                <a-button type="primary" @click="showCreateGroup">
                  <template #icon><PlusOutlined /></template>{{ t('mqtt.createGroup') }}
                </a-button>
                <a-button @click="loadGroups">
                  <template #icon><ReloadOutlined /></template>{{ t('common.refresh') }}
                </a-button>
              </a-space>
            </template>
            <a-table :data-source="groupList" :loading="groupLoading" :pagination="false" row-key="groupId" size="middle">
              <a-table-column :title="t('mqtt.groupName')" data-index="groupName" />
              <a-table-column :title="t('mqtt.groupDesc')" data-index="description" />
              <a-table-column :title="t('mqtt.deviceCount')" data-index="deviceCount" align="center" :width="100" />
              <a-table-column :title="t('mqtt.actions')" align="center" :width="320">
                <template #default="{ record }">
                  <a-space>
                    <a-button type="link" size="small" @click="showGroupMembers(record.groupId)">
                      <template #icon><TeamOutlined /></template>{{ t('mqtt.members') }}
                    </a-button>
                    <a-button type="link" size="small" @click="handleGroupWakeup(record.groupId)">
                      {{ t('mqtt.wakeup') }}
                    </a-button>
                    <a-button type="link" size="small" @click="showGroupNotify(record.groupId)">
                      {{ t('mqtt.notify') }}
                    </a-button>
                    <a-button type="link" size="small" @click="showEditGroup(record)">
                      <template #icon><EditOutlined /></template>
                    </a-button>
                    <a-button type="link" size="small" danger @click="handleDeleteGroup(record)">
                      <template #icon><DeleteOutlined /></template>
                    </a-button>
                  </a-space>
                </template>
              </a-table-column>
            </a-table>
          </a-card>
        </a-tab-pane>

        <!-- 发送记录 Tab -->
        <a-tab-pane key="logs" :tab="t('mqtt.sendLogTab')">
          <a-card :bordered="false">
            <template #title>{{ t('mqtt.sendLog') }}</template>
            <a-table :data-source="sendLogs" :pagination="false" row-key="time" size="small">
              <a-table-column :title="t('mqtt.logTime')" data-index="time" :width="100" />
              <a-table-column :title="t('mqtt.logType')" data-index="type" :width="120" />
              <a-table-column :title="t('mqtt.logTarget')" data-index="target" :width="160" />
              <a-table-column :title="t('mqtt.logMessage')" data-index="message" />
              <a-table-column :title="t('mqtt.logResult')" data-index="success" :width="80" align="center">
                <template #default="{ record }">
                  <a-tag :color="record.success ? 'green' : 'red'">{{ record.success ? 'OK' : 'FAIL' }}</a-tag>
                </template>
              </a-table-column>
            </a-table>
            <a-empty v-if="sendLogs.length === 0" :description="t('mqtt.noSendLog')" />
          </a-card>
        </a-tab-pane>
      </a-tabs>
    </template>

    <!-- 传感器历史数据弹窗 -->
    <a-modal v-model:open="sensorModalVisible" :title="t('mqtt.sensorHistoryTitle') + ' - ' + sensorDeviceId"
      :footer="null" width="800px">
      <a-table :data-source="sensorData" :loading="sensorLoading" :pagination="{ pageSize: 20 }" row-key="id" size="small">
        <a-table-column title="Temperature" data-index="temperature" :width="100">
          <template #default="{ record }">{{ record.temperature != null ? record.temperature + '\u00B0C' : '-' }}</template>
        </a-table-column>
        <a-table-column title="Battery" data-index="battery" :width="80">
          <template #default="{ record }">{{ record.battery != null ? record.battery + '%' : '-' }}</template>
        </a-table-column>
        <a-table-column title="Free Heap" data-index="freeHeap" :width="100">
          <template #default="{ record }">{{ record.freeHeap != null ? (record.freeHeap / 1024).toFixed(1) + 'KB' : '-' }}</template>
        </a-table-column>
        <a-table-column title="WiFi RSSI" data-index="wifiRssi" :width="100">
          <template #default="{ record }">{{ record.wifiRssi != null ? record.wifiRssi + 'dBm' : '-' }}</template>
        </a-table-column>
        <a-table-column title="Uptime" data-index="uptime" :width="100">
          <template #default="{ record }">{{ formatUptime(record.uptime) }}</template>
        </a-table-column>
        <a-table-column title="Time" data-index="createTime" :width="160" />
      </a-table>
    </a-modal>

    <!-- 分组编辑弹窗 -->
    <a-modal v-model:open="groupModalVisible" :title="groupModalTitle" @ok="handleSaveGroup">
      <a-form layout="vertical">
        <a-form-item :label="t('mqtt.groupName')">
          <a-input v-model:value="groupForm.groupName" :placeholder="t('mqtt.groupNameRequired')" />
        </a-form-item>
        <a-form-item :label="t('mqtt.groupDesc')">
          <a-input v-model:value="groupForm.description" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 分组成员管理弹窗 -->
    <a-modal v-model:open="groupMemberModalVisible" :title="t('mqtt.memberManagement')" :footer="null" width="500px">
      <a-space style="margin-bottom: 12px; width: 100%">
        <a-input v-model:value="addMemberDeviceId" :placeholder="t('mqtt.pleaseEnterDeviceId')" style="width: 260px" />
        <a-button type="primary" @click="handleAddMember">{{ t('mqtt.addMember') }}</a-button>
      </a-space>
      <a-list :data-source="groupMemberDevices" :loading="groupMemberLoading" size="small" bordered>
        <template #renderItem="{ item }">
          <a-list-item>
            {{ item }}
            <template #actions>
              <a-button type="link" size="small" danger @click="handleRemoveMember(item)">
                <template #icon><DeleteOutlined /></template>
              </a-button>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </a-modal>

    <!-- 分组通知弹窗 -->
    <a-modal v-model:open="groupNotifyModalVisible" :title="t('mqtt.groupNotify')" @ok="handleGroupNotify">
      <a-textarea v-model:value="groupNotifyForm.text" :placeholder="t('mqtt.pleaseEnterText')" :rows="3" />
    </a-modal>

    <!-- 远程配置弹窗 -->
    <a-modal v-model:open="configModalVisible" :title="t('mqtt.remoteConfigTitle') + ' - ' + configDeviceId"
      @ok="handlePushConfig" :confirmLoading="configLoading2">
      <a-form layout="vertical">
        <a-form-item :label="t('mqtt.configVolume')">
          <a-slider v-model:value="configForm.volume" :min="0" :max="100" />
        </a-form-item>
        <a-form-item :label="t('mqtt.configWakeWord')">
          <a-input v-model:value="configForm.wakeWord" :placeholder="t('mqtt.configWakeWordHint')" />
        </a-form-item>
        <a-form-item :label="t('mqtt.configLedBrightness')">
          <a-slider v-model:value="configForm.ledBrightness" :min="0" :max="100" />
        </a-form-item>
        <a-form-item :label="t('mqtt.configTimezone')">
          <a-input v-model:value="configForm.timezone" placeholder="Asia/Shanghai" />
        </a-form-item>
        <a-form-item :label="t('mqtt.configLanguage')">
          <a-input v-model:value="configForm.language" placeholder="zh-CN" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.mqtt-container { padding: 0; }
.status-row { margin-bottom: 16px; }
.action-card { margin-bottom: 16px; }
.device-list-card { margin-bottom: 16px; }
</style>

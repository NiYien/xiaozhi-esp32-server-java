<template>
  <div class="voiceprint-container">
    <!-- 功能状态提示 -->
    <a-alert
      v-if="statusLoaded"
      :message="statusEnabled ? t('voiceprint.statusEnabled', { count: voiceprintCount }) : t('voiceprint.statusDisabled')"
      :type="statusEnabled ? 'success' : 'warning'"
      show-icon
      style="margin-bottom: 16px"
    />

    <a-card :title="t('voiceprint.title')" :bordered="false">
      <template #extra>
        <a-space>
          <a-button
            type="primary"
            :disabled="!statusEnabled"
            @click="showRegisterModal"
          >
            {{ t('voiceprint.register') }}
          </a-button>
        </a-space>
      </template>

      <!-- 声纹列表 -->
      <a-table
        :columns="columns"
        :data-source="voiceprintList"
        :loading="loading"
        :pagination="false"
        row-key="voiceprintId"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-popconfirm
              :title="t('voiceprint.confirmDelete')"
              @confirm="handleDelete(record)"
            >
              <a-button type="link" danger size="small">
                {{ t('common.delete') }}
              </a-button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 注册声纹对话框 -->
    <a-modal
      v-model:open="registerModalVisible"
      :title="t('voiceprint.register')"
      :confirm-loading="registerLoading"
      @ok="handleRegister"
      @cancel="handleModalCancel"
    >
      <a-form layout="vertical">
        <a-form-item :label="t('voiceprint.voiceprintName')" required>
          <a-input
            v-model:value="registerForm.name"
            :placeholder="t('voiceprint.enterName')"
          />
        </a-form-item>
        <a-form-item :label="t('voiceprint.deviceId')" required>
          <a-select
            v-model:value="registerForm.deviceId"
            :placeholder="t('voiceprint.selectDevice')"
            :loading="deviceLoading"
            show-search
            option-filter-prop="label"
          >
            <a-select-option
              v-for="device in deviceList"
              :key="device.deviceId"
              :value="device.deviceId"
              :label="device.deviceName || device.deviceId"
            >
              {{ device.deviceName || device.deviceId }}
              <span v-if="device.deviceName" style="color: #999; margin-left: 8px;">
                ({{ device.deviceId }})
              </span>
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item :label="t('voiceprint.audioSource')" required>
          <a-tabs v-model:activeKey="audioTab" @change="handleTabChange">
            <a-tab-pane key="upload" :tab="t('voiceprint.tabUpload')">
              <a-upload
                :before-upload="beforeUpload"
                :file-list="fileList"
                :max-count="1"
                accept=".wav,.pcm"
                @remove="handleRemoveFile"
              >
                <a-button>
                  <upload-outlined />
                  {{ t('voiceprint.selectFile') }}
                </a-button>
              </a-upload>
              <div style="color: #999; font-size: 12px; margin-top: 4px;">
                {{ t('voiceprint.audioTip') }}
              </div>
            </a-tab-pane>
            <a-tab-pane key="record" :tab="t('voiceprint.tabRecord')">
              <div v-if="!recorder.isSupported.value" style="color: #ff4d4f; padding: 16px 0;">
                {{ t('voiceprint.micNotSupported') }}
              </div>
              <div v-else>
                <canvas
                  ref="waveformCanvas"
                  :width="400"
                  :height="80"
                  style="width: 100%; height: 80px; border: 1px solid #f0f0f0; border-radius: 4px;"
                />
                <div style="display: flex; align-items: center; margin-top: 8px; gap: 8px;">
                  <span style="font-size: 14px; font-variant-numeric: tabular-nums; min-width: 50px;">
                    {{ formatDuration(recorder.duration.value) }}
                  </span>
                  <span
                    v-if="recorder.state.value === 'recording' && recorder.duration.value < 2"
                    style="color: #faad14; font-size: 12px;"
                  >
                    {{ t('voiceprint.minDurationTip') }}
                  </span>
                </div>
                <div style="margin-top: 8px; display: flex; gap: 8px;">
                  <a-button
                    v-if="recorder.state.value === 'idle'"
                    type="primary"
                    danger
                    @click="handleStartRecord"
                  >
                    {{ t('voiceprint.startRecord') }}
                  </a-button>
                  <a-button
                    v-if="recorder.state.value === 'recording'"
                    :disabled="recorder.duration.value < 1.5"
                    @click="recorder.stop()"
                  >
                    {{ t('voiceprint.stopRecord') }}
                  </a-button>
                  <a-button
                    v-if="recorder.state.value === 'stopped'"
                    @click="recorder.reset()"
                  >
                    {{ t('voiceprint.reRecord') }}
                  </a-button>
                  <a-button
                    v-if="recorder.state.value === 'stopped' && recorder.wavBlobUrl.value"
                    @click="playPreview"
                  >
                    {{ t('voiceprint.playPreview') }}
                  </a-button>
                </div>
                <div
                  v-if="recorder.errorMsg.value === 'permissionDenied'"
                  style="color: #ff4d4f; font-size: 12px; margin-top: 4px;"
                >
                  {{ t('voiceprint.micPermissionDenied') }}
                </div>
                <div style="color: #999; font-size: 12px; margin-top: 4px;">
                  {{ t('voiceprint.recordTip') }}
                </div>
              </div>
            </a-tab-pane>
            <a-tab-pane key="conversation" tab="从对话记录选择">
              <a-button
                type="primary"
                :disabled="!registerForm.deviceId"
                @click="openAudioSelectionFromRegister"
              >
                选择对话音频
              </a-button>
              <div v-if="selectedMessageIds.length > 0" style="margin-top: 8px; color: #52c41a;">
                已选择 {{ selectedMessageIds.length }} 条音频
              </div>
              <div style="color: #999; font-size: 12px; margin-top: 4px;">
                从已选设备的对话记录中选择音频用于声纹注册
              </div>
            </a-tab-pane>
          </a-tabs>
        </a-form-item>
      </a-form>
    </a-modal>

    <AudioSelectionDialog
      v-model:open="audioSelectionVisible"
      :device-id="registerForm.deviceId || ''"
      :min-duration="1.5"
      @confirm="handleAudioSelectionConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { UploadOutlined } from '@ant-design/icons-vue'
import { useI18n } from 'vue-i18n'
import type { UploadFile } from 'ant-design-vue'
import {
  queryVoiceprints,
  registerVoiceprint,
  deleteVoiceprint,
  getVoiceprintStatus,
} from '@/services/voiceprint'
import { queryDevices } from '@/services/device'
import { useAudioRecorder } from '@/composables/useAudioRecorder'
import AudioSelectionDialog from '@/components/AudioSelectionDialog.vue'
import { http } from '@/services/request'
import api from '@/services/api'
import type { SysVoiceprint } from '@/types/voiceprint'
import type { Device } from '@/types/device'

const { t } = useI18n()

// 功能状态
const statusLoaded = ref(false)
const statusEnabled = ref(false)
const voiceprintCount = ref(0)

// 声纹列表
const loading = ref(false)
const voiceprintList = ref<SysVoiceprint[]>([])

// 注册对话框
const registerModalVisible = ref(false)
const registerLoading = ref(false)
const registerForm = ref({
  name: '',
  deviceId: undefined as string | undefined,
})
const fileList = ref<UploadFile[]>([])
const audioTab = ref<string>('upload')

// 录音
const recorder = useAudioRecorder()
const waveformCanvas = ref<HTMLCanvasElement | null>(null)

// 设备列表
const deviceLoading = ref(false)
const deviceList = ref<Device[]>([])

const columns = computed(() => [
  { title: t('voiceprint.voiceprintName'), dataIndex: 'voiceprintName', key: 'voiceprintName', ellipsis: true },
  { title: t('voiceprint.deviceId'), dataIndex: 'deviceId', key: 'deviceId', width: 200 },
  { title: t('voiceprint.sampleCount'), dataIndex: 'sampleCount', key: 'sampleCount', width: 100 },
  { title: t('voiceprint.createTime'), dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: t('voiceprint.action'), key: 'action', width: 100, fixed: 'right' as const },
])

// ==================== 功能状态 ====================

async function fetchStatus() {
  try {
    const res = await getVoiceprintStatus()
    const data = (res as any).data
    if (data) {
      statusEnabled.value = data.enabled
      voiceprintCount.value = data.voiceprintCount || 0
    }
    statusLoaded.value = true
  } catch (e) {
    console.error(e)
    statusLoaded.value = true
  }
}

// ==================== 声纹列表 ====================

async function fetchVoiceprints() {
  loading.value = true
  try {
    const res = await queryVoiceprints()
    voiceprintList.value = (res as any).data || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

// ==================== 注册声纹 ====================

async function fetchDevices() {
  deviceLoading.value = true
  try {
    const res = await queryDevices({ start: 1, limit: 1000 })
    deviceList.value = (res as any).data?.list || []
  } catch (e) {
    console.error(e)
  } finally {
    deviceLoading.value = false
  }
}

function showRegisterModal() {
  registerForm.value = { name: '', deviceId: undefined }
  fileList.value = []
  audioTab.value = 'upload'
  selectedMessageIds.value = []
  recorder.reset()
  registerModalVisible.value = true
  fetchDevices()
}

function handleModalCancel() {
  recorder.reset()
  registerModalVisible.value = false
}

function handleTabChange(key: string | number) {
  if (key === 'upload') {
    recorder.reset()
    selectedMessageIds.value = []
  } else if (key === 'record') {
    fileList.value = []
    selectedMessageIds.value = []
    nextTick(() => {
      if (waveformCanvas.value) {
        recorder.initCanvas(waveformCanvas.value)
      }
    })
  } else if (key === 'conversation') {
    fileList.value = []
    recorder.reset()
  }
}

function handleStartRecord() {
  if (waveformCanvas.value) {
    recorder.initCanvas(waveformCanvas.value)
  }
  recorder.start()
}

function beforeUpload(file: File) {
  fileList.value = [{ uid: '-1', name: file.name, status: 'done', originFileObj: file } as UploadFile]
  return false
}

function handleRemoveFile() {
  fileList.value = []
}

function playPreview() {
  if (recorder.wavBlobUrl.value) {
    new Audio(recorder.wavBlobUrl.value).play()
  }
}

function formatDuration(seconds: number): string {
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

async function handleRegister() {
  if (!registerForm.value.name.trim()) {
    message.warning(t('voiceprint.nameRequired'))
    return
  }
  if (!registerForm.value.deviceId) {
    message.warning(t('voiceprint.deviceRequired'))
    return
  }

  // 从对话记录选择模式
  if (audioTab.value === 'conversation') {
    if (selectedMessageIds.value.length === 0) {
      message.warning('请先选择对话音频')
      return
    }
    registerLoading.value = true
    try {
      const res = await http.postJSON(api.voiceprint.registerFromMessages, {
        deviceId: registerForm.value.deviceId,
        name: registerForm.value.name,
        messageIds: selectedMessageIds.value,
      }) as any
      if (res.code === 200) {
        message.success(t('voiceprint.registerSuccess'))
        registerModalVisible.value = false
        selectedMessageIds.value = []
        fetchVoiceprints()
        fetchStatus()
      } else {
        message.error(res.message || t('voiceprint.registerFailed'))
      }
    } catch (e) {
      message.error(t('voiceprint.registerFailed'))
    } finally {
      registerLoading.value = false
    }
    return
  }

  let audioFile: File | null = null

  if (audioTab.value === 'upload') {
    const firstFile = fileList.value[0]
    if (!firstFile?.originFileObj) {
      message.warning(t('voiceprint.audioRequired'))
      return
    }
    audioFile = firstFile.originFileObj as File
  } else {
    if (recorder.state.value !== 'stopped' || !recorder.wavFile.value) {
      message.warning(t('voiceprint.recordNotDone'))
      return
    }
    audioFile = recorder.wavFile.value
  }

  registerLoading.value = true
  try {
    const res = await registerVoiceprint(
      registerForm.value.deviceId,
      registerForm.value.name,
      audioFile
    )
    const data = res as any
    if (data.code === 200) {
      message.success(t('voiceprint.registerSuccess'))
      registerModalVisible.value = false
      recorder.reset()
      fetchVoiceprints()
      fetchStatus()
    } else {
      message.error(data.message || t('voiceprint.registerFailed'))
    }
  } catch (e) {
    message.error(t('voiceprint.registerFailed'))
  } finally {
    registerLoading.value = false
  }
}

// ==================== 删除声纹 ====================

async function handleDelete(record: SysVoiceprint) {
  try {
    const res = await deleteVoiceprint(record.voiceprintId)
    const data = res as any
    if (data.code === 200) {
      message.success(t('common.deleteSuccess'))
      fetchVoiceprints()
      fetchStatus()
    } else {
      message.error(data.message || t('common.deleteFailed'))
    }
  } catch (e) {
    message.error(t('common.deleteFailed'))
  }
}

// ==================== 从对话记录选择音频 ====================

const audioSelectionVisible = ref(false)
const selectedMessageIds = ref<number[]>([])

// 从注册弹窗内打开音频选择对话框
function openAudioSelectionFromRegister() {
  if (!registerForm.value.deviceId) {
    message.warning(t('voiceprint.deviceRequired'))
    return
  }
  audioSelectionVisible.value = true
}

async function handleAudioSelectionConfirm(messageIds: number[]) {
  selectedMessageIds.value = messageIds
  audioSelectionVisible.value = false
}

onMounted(() => {
  fetchStatus()
  fetchVoiceprints()
})
</script>

<style scoped>
.voiceprint-container {
  padding: 0;
}
</style>

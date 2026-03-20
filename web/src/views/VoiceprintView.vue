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
        <a-button
          type="primary"
          :disabled="!statusEnabled"
          @click="showRegisterModal"
        >
          {{ t('voiceprint.register') }}
        </a-button>
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
      @cancel="registerModalVisible = false"
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
        <a-form-item :label="t('voiceprint.audioFile')" required>
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
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
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
    const res = await queryDevices({ start: 0, limit: 1000 })
    deviceList.value = (res as any).data || []
  } catch (e) {
    console.error(e)
  } finally {
    deviceLoading.value = false
  }
}

function showRegisterModal() {
  registerForm.value = { name: '', deviceId: undefined }
  fileList.value = []
  registerModalVisible.value = true
  fetchDevices()
}

function beforeUpload(file: File) {
  fileList.value = [{ uid: '-1', name: file.name, status: 'done', originFileObj: file } as UploadFile]
  return false
}

function handleRemoveFile() {
  fileList.value = []
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
  if (fileList.value.length === 0 || !fileList.value[0].originFileObj) {
    message.warning(t('voiceprint.audioRequired'))
    return
  }

  registerLoading.value = true
  try {
    const res = await registerVoiceprint(
      registerForm.value.deviceId,
      registerForm.value.name,
      fileList.value[0].originFileObj as File
    )
    const data = res as any
    if (data.code === 0) {
      message.success(t('voiceprint.registerSuccess'))
      registerModalVisible.value = false
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
    if (data.code === 0) {
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

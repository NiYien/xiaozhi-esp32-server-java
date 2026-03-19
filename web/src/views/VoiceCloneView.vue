<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import {
  PlusOutlined,
  DeleteOutlined,
  SoundOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import {
  queryVoiceClones,
  uploadVoiceClone,
  deleteVoiceClone,
  previewVoiceClone,
} from '@/services/voiceClone'
import type { VoiceClone } from '@/services/voiceClone'
import { queryConfigs } from '@/services/config'

const { t } = useI18n()

// 列表数据
const loading = ref(false)
const cloneList = ref<VoiceClone[]>([])

// 上传弹窗
const uploadVisible = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadForm = reactive({
  cloneName: '',
  provider: 'volcengine',
  configId: undefined as number | undefined,
  file: null as File | null,
})

// TTS 配置列表（用于选择 provider 对应的配置）
const ttsConfigs = ref<{ configId: number; configName: string; provider: string }[]>([])

// 试听相关
const previewLoading = ref<number | null>(null)
const audioPlayer = ref<HTMLAudioElement | null>(null)

// 轮询定时器
let pollTimer: ReturnType<typeof setInterval> | null = null

// 表格列定义
const columns = [
  { title: t('voiceClone.cloneName'), dataIndex: 'cloneName', key: 'cloneName' },
  { title: t('voiceClone.provider'), dataIndex: 'provider', key: 'provider' },
  { title: t('voiceClone.status'), dataIndex: 'status', key: 'status' },
  { title: t('voiceClone.createTime'), dataIndex: 'createTime', key: 'createTime' },
  { title: t('common.action'), key: 'action', width: 200 },
]

// 支持音色克隆的 Provider 列表
const cloneProviders = [
  { label: t('voiceClone.volcengine'), value: 'volcengine' },
  { label: t('voiceClone.aliyunNls'), value: 'aliyun-nls' },
]

// 状态标签颜色
function getStatusColor(status: string) {
  switch (status) {
    case 'ready': return 'green'
    case 'training': return 'blue'
    case 'uploading': return 'orange'
    case 'failed': return 'red'
    default: return 'default'
  }
}

// 状态标签文本
function getStatusText(status: string) {
  switch (status) {
    case 'ready': return t('voiceClone.statusReady')
    case 'training': return t('voiceClone.statusTraining')
    case 'uploading': return t('voiceClone.statusUploading')
    case 'failed': return t('voiceClone.statusFailed')
    default: return status
  }
}

// 加载列表
async function fetchData() {
  loading.value = true
  try {
    const res = await queryVoiceClones()
    if (res.code === 200) {
      cloneList.value = res.data || []
      // 如果有 training 状态的记录，启动轮询
      updatePolling()
    }
  } catch (error) {
    console.error('查询失败:', error)
  } finally {
    loading.value = false
  }
}

// 加载 TTS 配置
async function fetchTtsConfigs() {
  try {
    const res = await queryConfigs({ configType: 'tts', start: 0, limit: 100 })
    if (res.code === 200 && res.data) {
      ttsConfigs.value = (res.data.list || [])
        .filter((c: any) => c.provider === 'volcengine' || c.provider === 'aliyun-nls')
        .map((c: any) => ({
          configId: c.configId,
          configName: c.configName || `${c.provider}-${c.configId}`,
          provider: c.provider,
        }))
    }
  } catch (error) {
    console.error('获取TTS配置失败:', error)
  }
}

// 过滤当前 provider 对应的配置
function filteredConfigs() {
  return ttsConfigs.value.filter(c => c.provider === uploadForm.provider)
}

// 打开上传弹窗
function showUploadDialog() {
  uploadForm.cloneName = ''
  uploadForm.provider = 'volcengine'
  uploadForm.configId = undefined
  uploadForm.file = null
  uploadProgress.value = 0
  uploadVisible.value = true
}

// 处理文件选择
function handleFileChange(info: any) {
  if (info.file) {
    uploadForm.file = info.file
  }
}

// 自定义上传（阻止默认上传行为）
function beforeUpload(file: File) {
  uploadForm.file = file
  return false
}

// 提交上传
async function handleUpload() {
  if (!uploadForm.cloneName) {
    message.warning(t('voiceClone.pleaseEnterName'))
    return
  }
  if (!uploadForm.configId) {
    message.warning(t('voiceClone.pleaseSelectConfig'))
    return
  }
  if (!uploadForm.file) {
    message.warning(t('voiceClone.pleaseSelectFile'))
    return
  }

  uploading.value = true
  try {
    await uploadVoiceClone(
      uploadForm.file,
      uploadForm.cloneName,
      uploadForm.provider,
      uploadForm.configId,
      (percent) => {
        uploadProgress.value = percent
      }
    )
    message.success(t('voiceClone.uploadSuccess'))
    uploadVisible.value = false
    await fetchData()
  } catch (error: any) {
    message.error(error.message || t('voiceClone.uploadFailed'))
  } finally {
    uploading.value = false
  }
}

// 删除克隆音色
async function handleDelete(cloneId: number) {
  try {
    const res = await deleteVoiceClone(cloneId)
    if (res.code === 200) {
      message.success(t('common.success'))
      await fetchData()
    } else {
      message.error(res.message || t('common.error'))
    }
  } catch (error) {
    message.error(t('common.error'))
  }
}

// 试听
async function handlePreview(cloneId: number) {
  previewLoading.value = cloneId
  try {
    const res = await previewVoiceClone(cloneId)
    if (res.code === 200 && res.data) {
      // 播放音频
      if (audioPlayer.value) {
        audioPlayer.value.pause()
      }
      const audio = new Audio(res.data)
      audioPlayer.value = audio
      audio.play()
    } else {
      message.error(res.message || t('voiceClone.previewFailed'))
    }
  } catch (error) {
    message.error(t('voiceClone.previewFailed'))
  } finally {
    previewLoading.value = null
  }
}

// 轮询管理
function updatePolling() {
  const hasTraining = cloneList.value.some(c => c.status === 'training' || c.status === 'uploading')
  if (hasTraining && !pollTimer) {
    pollTimer = setInterval(fetchData, 10000) // 每10秒刷新
  } else if (!hasTraining && pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(() => {
  fetchData()
  fetchTtsConfigs()
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  if (audioPlayer.value) {
    audioPlayer.value.pause()
  }
})
</script>

<template>
  <div class="voice-clone-container">
    <!-- 操作栏 -->
    <div class="action-bar">
      <a-button type="primary" @click="showUploadDialog">
        <template #icon><PlusOutlined /></template>
        {{ t('voiceClone.addClone') }}
      </a-button>
      <a-button @click="fetchData" :loading="loading">
        <template #icon><ReloadOutlined /></template>
        {{ t('common.refresh') }}
      </a-button>
    </div>

    <!-- 列表 -->
    <a-table
      :columns="columns"
      :data-source="cloneList"
      :loading="loading"
      :pagination="false"
      row-key="cloneId"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="getStatusColor(record.status)">
            {{ getStatusText(record.status) }}
          </a-tag>
          <a-tooltip v-if="record.status === 'failed' && record.errorMessage" :title="record.errorMessage">
            <span style="color: #ff4d4f; cursor: pointer; margin-left: 4px;">?</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button
              v-if="record.status === 'ready'"
              type="link"
              size="small"
              :loading="previewLoading === record.cloneId"
              @click="handlePreview(record.cloneId)"
            >
              <template #icon><SoundOutlined /></template>
              {{ t('voiceClone.preview') }}
            </a-button>
            <a-popconfirm
              :title="t('voiceClone.confirmDelete')"
              @confirm="handleDelete(record.cloneId)"
            >
              <a-button type="link" size="small" danger>
                <template #icon><DeleteOutlined /></template>
                {{ t('common.delete') }}
              </a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 上传弹窗 -->
    <a-modal
      v-model:open="uploadVisible"
      :title="t('voiceClone.addClone')"
      :confirm-loading="uploading"
      @ok="handleUpload"
      @cancel="uploadVisible = false"
    >
      <a-form layout="vertical">
        <a-form-item :label="t('voiceClone.cloneName')" required>
          <a-input
            v-model:value="uploadForm.cloneName"
            :placeholder="t('voiceClone.pleaseEnterName')"
            :maxlength="100"
          />
        </a-form-item>
        <a-form-item :label="t('voiceClone.provider')" required>
          <a-select v-model:value="uploadForm.provider">
            <a-select-option v-for="p in cloneProviders" :key="p.value" :value="p.value">
              {{ p.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item :label="t('voiceClone.ttsConfig')" required>
          <a-select
            v-model:value="uploadForm.configId"
            :placeholder="t('voiceClone.pleaseSelectConfig')"
          >
            <a-select-option v-for="c in filteredConfigs()" :key="c.configId" :value="c.configId">
              {{ c.configName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item :label="t('voiceClone.audioFile')" required>
          <a-upload
            :before-upload="beforeUpload"
            :max-count="1"
            accept=".wav,.mp3"
          >
            <a-button>
              {{ t('voiceClone.selectFile') }}
            </a-button>
          </a-upload>
          <div class="upload-tip">{{ t('voiceClone.audioTip') }}</div>
        </a-form-item>
        <a-progress v-if="uploading" :percent="uploadProgress" size="small" />
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.voice-clone-container {
  padding: 16px;
}
.action-bar {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
}
.upload-tip {
  color: #999;
  font-size: 12px;
  margin-top: 4px;
}
</style>

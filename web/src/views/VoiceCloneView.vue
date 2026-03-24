<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
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
import { useAudioRecorder } from '@/composables/useAudioRecorder'

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
  speakerId: '',
  provider: 'volcengine',
  configId: undefined as number | undefined,
  file: null as File | null,
})

// TTS 配置列表（用于选择 provider 对应的配置）
const ttsConfigs = ref<{ configId: number; configName: string; provider: string }[]>([])

// 试听相关
const previewLoading = ref<number | null>(null)
const audioPlayer = ref<HTMLAudioElement | null>(null)

// 录音相关
const audioTab = ref<string>('upload')
const recorder = useAudioRecorder()
const waveformCanvas = ref<HTMLCanvasElement | null>(null)

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
    const res = await queryConfigs({ configType: 'tts', start: 1, limit: 100 })
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
  uploadForm.speakerId = ''
  uploadForm.provider = 'volcengine'
  uploadForm.configId = undefined
  uploadForm.file = null
  uploadProgress.value = 0
  audioTab.value = 'upload'
  recorder.reset()
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
  if (uploadForm.provider === 'volcengine' && !uploadForm.speakerId) {
    message.warning(t('voiceClone.pleaseEnterSpeakerId'))
    return
  }
  if (!uploadForm.configId) {
    message.warning(t('voiceClone.pleaseSelectConfig'))
    return
  }

  let file: File | null = null
  if (audioTab.value === 'upload') {
    if (!uploadForm.file) {
      message.warning(t('voiceClone.pleaseSelectFile'))
      return
    }
    file = uploadForm.file
  } else {
    if (recorder.state.value !== 'stopped' || !recorder.wavFile.value) {
      message.warning(t('voiceClone.recordNotDone'))
      return
    }
    file = recorder.wavFile.value
  }

  uploading.value = true
  try {
    await uploadVoiceClone(
      file,
      uploadForm.cloneName,
      uploadForm.provider,
      uploadForm.configId,
      uploadForm.speakerId || undefined,
      (percent) => {
        uploadProgress.value = percent
      }
    )
    message.success(t('voiceClone.uploadSuccess'))
    uploadVisible.value = false
    recorder.reset()
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

// 录音辅助函数
function handleStartRecord() {
  if (waveformCanvas.value) {
    recorder.initCanvas(waveformCanvas.value)
  }
  recorder.start()
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

function handleTabChange(key: string | number) {
  if (key === 'upload') {
    recorder.reset()
  } else {
    uploadForm.file = null
    nextTick(() => {
      if (waveformCanvas.value) {
        recorder.initCanvas(waveformCanvas.value)
      }
    })
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
      @cancel="() => { recorder.reset(); uploadVisible = false }"
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
        <a-form-item v-if="uploadForm.provider === 'volcengine'" :label="t('voiceClone.speakerId')" required>
          <a-input
            v-model:value="uploadForm.speakerId"
            :placeholder="t('voiceClone.pleaseEnterSpeakerId')"
            :maxlength="50"
          />
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
        <a-form-item :label="t('voiceClone.audioSource')" required>
          <a-tabs v-model:activeKey="audioTab" @change="handleTabChange">
            <a-tab-pane key="upload" :tab="t('voiceClone.tabUpload')">
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
            </a-tab-pane>
            <a-tab-pane key="record" :tab="t('voiceClone.tabRecord')">
              <div v-if="!recorder.isSupported.value" style="color: #ff4d4f; padding: 16px 0;">
                {{ t('voiceClone.micNotSupported') }}
              </div>
              <div v-else>
                <canvas ref="waveformCanvas" :width="400" :height="80"
                  style="width: 100%; height: 80px; border: 1px solid #f0f0f0; border-radius: 4px;" />
                <div style="display: flex; align-items: center; margin-top: 8px; gap: 8px;">
                  <span style="font-size: 14px; font-variant-numeric: tabular-nums; min-width: 50px;">
                    {{ formatDuration(recorder.duration.value) }}
                  </span>
                  <span v-if="recorder.state.value === 'recording' && recorder.duration.value < 10"
                    style="color: #faad14; font-size: 12px;">
                    {{ t('voiceClone.minDurationTip') }}
                  </span>
                </div>
                <div style="margin-top: 8px; display: flex; gap: 8px;">
                  <a-button v-if="recorder.state.value === 'idle'" type="primary" danger @click="handleStartRecord">
                    {{ t('voiceClone.startRecord') }}
                  </a-button>
                  <a-button v-if="recorder.state.value === 'recording'" :disabled="recorder.duration.value < 10" @click="recorder.stop()">
                    {{ t('voiceClone.stopRecord') }}
                  </a-button>
                  <a-button v-if="recorder.state.value === 'stopped'" @click="recorder.reset()">
                    {{ t('voiceClone.reRecord') }}
                  </a-button>
                  <a-button v-if="recorder.state.value === 'stopped' && recorder.wavBlobUrl.value"
                    @click="playPreview">
                    {{ t('voiceClone.playRecording') }}
                  </a-button>
                </div>
                <div v-if="recorder.errorMsg.value === 'permissionDenied'"
                  style="color: #ff4d4f; font-size: 12px; margin-top: 4px;">
                  {{ t('voiceClone.micPermissionDenied') }}
                </div>
                <div style="color: #999; font-size: 12px; margin-top: 4px;">
                  {{ t('voiceClone.recordTip') }}
                </div>
              </div>
            </a-tab-pane>
          </a-tabs>
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

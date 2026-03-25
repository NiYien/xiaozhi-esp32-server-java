<template>
  <div class="knowledge-container">
    <!-- RAG 未启用时显示引导卡片 -->
    <a-card v-if="ragDisabledDetected" :bordered="false" style="margin-bottom: 16px;">
      <a-result
        status="info"
        :title="t('knowledge.ragDisabled')"
      >
        <template #subTitle>
          <div style="text-align: left; max-width: 500px; margin: 0 auto;">
            <p style="margin-bottom: 8px; font-weight: 500;">{{ t('knowledge.ragGuideTitle') }}</p>
            <ol style="padding-left: 20px; margin: 0;">
              <li>{{ t('knowledge.ragGuideStep1') }}</li>
              <li>{{ t('knowledge.ragGuideStep2') }}</li>
              <li>{{ t('knowledge.ragGuideStep3') }}</li>
            </ol>
          </div>
        </template>
      </a-result>
    </a-card>

    <a-card :title="t('knowledge.title')" :bordered="false">
      <template #extra>
        <a-space>
          <a-button type="primary" @click="showCreateBaseModal">
            {{ t('knowledge.createBase') }}
          </a-button>
        </a-space>
      </template>

      <!-- 知识库列表 -->
      <a-table
        :columns="baseColumns"
        :data-source="baseList"
        :loading="baseLoading"
        :pagination="false"
        row-key="knowledgeBaseId"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'embeddingModelName'">
            {{ record.embeddingModelName || t('knowledge.defaultModel') }}
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="viewDocs(record)">
                {{ t('knowledge.viewDocs') }}
              </a-button>
              <a-button type="link" size="small" @click="showEditBaseModal(record)">
                {{ t('common.edit') }}
              </a-button>
              <a-popconfirm
                :title="t('common.confirmDelete')"
                @confirm="handleDeleteBase(record)"
              >
                <a-button type="link" danger size="small">
                  {{ t('common.delete') }}
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 创建知识库弹窗 -->
    <a-modal
      v-model:open="createBaseModalVisible"
      :title="t('knowledge.createBase')"
      :ok-button-props="{ disabled: embeddingModels.length === 0 }"
      @ok="handleCreateBase"
      @cancel="createBaseModalVisible = false"
    >
      <a-form layout="vertical">
        <a-form-item :label="t('knowledge.baseName')" required>
          <a-input v-model:value="newBase.knowledgeBaseName" :placeholder="t('knowledge.baseNamePlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('knowledge.baseDesc')">
          <a-textarea v-model:value="newBase.description" :placeholder="t('knowledge.baseDescPlaceholder')" :rows="3" />
        </a-form-item>
        <a-form-item :label="t('knowledge.embeddingModel')" required>
          <a-select
            v-model:value="newBase.embeddingConfigId"
            :placeholder="t('knowledge.embeddingModelPlaceholder')"
            :loading="embeddingModelsLoading"
            :not-found-content="t('knowledge.noEmbeddingModel')"
          >
            <a-select-option v-for="model in embeddingModels" :key="model.configId" :value="model.configId">
              {{ model.configName }}
            </a-select-option>
          </a-select>
          <div v-if="embeddingModels.length === 0 && !embeddingModelsLoading" style="color: #ff4d4f; margin-top: 4px; font-size: 12px;">
            {{ t('knowledge.noEmbeddingModelTip') }}
          </div>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 编辑知识库弹窗 -->
    <a-modal
      v-model:open="editBaseModalVisible"
      :title="t('knowledge.editBase')"
      @ok="handleEditBase"
      @cancel="editBaseModalVisible = false"
    >
      <a-form layout="vertical">
        <a-form-item :label="t('knowledge.baseName')" required>
          <a-input v-model:value="editBase.knowledgeBaseName" :placeholder="t('knowledge.baseNamePlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('knowledge.baseDesc')">
          <a-textarea v-model:value="editBase.description" :placeholder="t('knowledge.baseDescPlaceholder')" :rows="3" />
        </a-form-item>
        <a-form-item :label="t('knowledge.embeddingModel')">
          <a-select
            v-model:value="editBase.embeddingConfigId"
            :placeholder="t('knowledge.embeddingModelPlaceholder')"
            :loading="embeddingModelsLoading"
            @change="onEditEmbeddingModelChange"
          >
            <a-select-option v-for="model in embeddingModels" :key="model.configId" :value="model.configId">
              {{ model.configName }}
            </a-select-option>
          </a-select>
          <a-alert
            v-if="embeddingModelChanged"
            type="warning"
            :message="t('knowledge.embeddingModelChangeWarning')"
            show-icon
            style="margin-top: 8px;"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 文档管理弹窗 -->
    <a-modal
      v-model:open="docModalVisible"
      :title="currentBase ? currentBase.knowledgeBaseName + ' - ' + t('knowledge.docManage') : t('knowledge.docManage')"
      width="900px"
      :footer="null"
    >
      <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center;">
        <a-select
          v-model:value="docQueryParams.status"
          :placeholder="t('knowledge.filterStatus')"
          allow-clear
          style="width: 150px"
          @change="fetchDocs"
        >
          <a-select-option value="uploading">{{ t('knowledge.statusUploading') }}</a-select-option>
          <a-select-option value="processing">{{ t('knowledge.statusProcessing') }}</a-select-option>
          <a-select-option value="ready">{{ t('knowledge.statusReady') }}</a-select-option>
          <a-select-option value="failed">{{ t('knowledge.statusFailed') }}</a-select-option>
        </a-select>
        <a-space>
          <a-button @click="fetchDocs">
            <reload-outlined />
            {{ t('common.refresh') }}
          </a-button>
          <a-upload
            :before-upload="beforeUpload"
            :show-upload-list="false"
            accept=".txt,.pdf,.md,.docx"
          >
            <a-button type="primary">
              <upload-outlined />
              {{ t('knowledge.upload') }}
            </a-button>
          </a-upload>
        </a-space>
      </div>

      <!-- 轮询超时提示 -->
      <a-alert
        v-if="pollingTimedOut"
        type="warning"
        :message="t('knowledge.pollingTimeout')"
        show-icon
        closable
        style="margin-bottom: 16px;"
      />

      <a-table
        :columns="docColumns"
        :data-source="docList"
        :loading="docLoading"
        :pagination="false"
        row-key="docId"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">
              {{ statusText(record.status) }}
            </a-tag>
          </template>
          <template v-if="column.key === 'fileSize'">
            {{ formatFileSize(record.fileSize) }}
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button
                v-if="record.status === 'failed'"
                type="link"
                size="small"
                @click="handleReprocess(record)"
              >
                {{ t('knowledge.reprocess') }}
              </a-button>
              <a-popconfirm
                :title="t('common.confirmDelete')"
                @confirm="handleDeleteDoc(record)"
              >
                <a-button type="link" danger size="small">
                  {{ t('common.delete') }}
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
          <template v-if="column.key === 'errorMsg'">
            <a-tooltip v-if="record.errorMsg" :title="record.errorMsg">
              <span style="color: #ff4d4f; cursor: pointer;">
                {{ record.errorMsg.substring(0, 30) }}{{ record.errorMsg.length > 30 ? '...' : '' }}
              </span>
            </a-tooltip>
          </template>
        </template>
      </a-table>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { UploadOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { useI18n } from 'vue-i18n'
import {
  queryKnowledgeBases,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
  queryKnowledgeDocs,
  uploadKnowledgeDoc,
  deleteKnowledgeDoc,
  reprocessKnowledgeDoc,
  getDocStatus,
  type KnowledgeBase,
  type KnowledgeDoc,
} from '@/services/knowledge'
import { queryConfigs } from '@/services/config'
import type { Config } from '@/types/config'

const { t } = useI18n()

// 知识库相关
const baseLoading = ref(false)
const baseList = ref<KnowledgeBase[]>([])
const createBaseModalVisible = ref(false)
const newBase = reactive({
  knowledgeBaseName: '',
  description: '',
  embeddingConfigId: undefined as number | undefined,
})

// 编辑知识库相关
const editBaseModalVisible = ref(false)
const editBase = reactive({
  knowledgeBaseId: 0,
  knowledgeBaseName: '',
  description: '',
  embeddingConfigId: undefined as number | undefined,
  originalEmbeddingConfigId: undefined as number | undefined,
})
const embeddingModelChanged = ref(false)

// Embedding 模型选项
const embeddingModels = ref<Config[]>([])
const embeddingModelsLoading = ref(false)

// 文档相关
const docLoading = ref(false)
const docList = ref<KnowledgeDoc[]>([])
const docModalVisible = ref(false)
const currentBase = ref<KnowledgeBase | null>(null)
const docQueryParams = reactive({
  status: undefined as string | undefined,
})

// RAG 未启用检测
const ragDisabledDetected = ref(false)

// 轮询相关
let pollingTimer: ReturnType<typeof setInterval> | null = null
// 轮询计数器，最大 60 次（约 5 分钟）
const MAX_POLLING_COUNT = 60
let pollingCount = 0
const pollingTimedOut = ref(false)

const baseColumns = computed(() => [
  { title: t('knowledge.baseName'), dataIndex: 'knowledgeBaseName', key: 'knowledgeBaseName', ellipsis: true },
  { title: t('knowledge.baseDesc'), dataIndex: 'description', key: 'description', ellipsis: true },
  { title: t('knowledge.embeddingModel'), dataIndex: 'embeddingModelName', key: 'embeddingModelName', width: 180 },
  { title: t('knowledge.createTime'), dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: t('knowledge.action'), key: 'action', width: 250, fixed: 'right' as const },
])

const docColumns = computed(() => [
  { title: t('knowledge.docName'), dataIndex: 'docName', key: 'docName', ellipsis: true },
  { title: t('knowledge.docType'), dataIndex: 'docType', key: 'docType', width: 80 },
  { title: t('knowledge.fileSize'), dataIndex: 'fileSize', key: 'fileSize', width: 100 },
  { title: t('knowledge.chunkCount'), dataIndex: 'chunkCount', key: 'chunkCount', width: 80 },
  { title: t('knowledge.status'), dataIndex: 'status', key: 'status', width: 100 },
  { title: t('knowledge.errorMsg'), dataIndex: 'errorMsg', key: 'errorMsg', width: 150 },
  { title: t('knowledge.createTime'), dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: t('knowledge.action'), key: 'action', width: 150, fixed: 'right' as const },
])

function statusColor(status: string) {
  const map: Record<string, string> = {
    uploading: 'default',
    processing: 'processing',
    ready: 'success',
    failed: 'error',
  }
  return map[status] || 'default'
}

function statusText(status: string) {
  const map: Record<string, string> = {
    uploading: t('knowledge.statusUploading'),
    processing: t('knowledge.statusProcessing'),
    ready: t('knowledge.statusReady'),
    failed: t('knowledge.statusFailed'),
  }
  return map[status] || status
}

function formatFileSize(bytes: number) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

// ==================== 知识库操作 ====================

async function fetchBases() {
  baseLoading.value = true
  try {
    const res = await queryKnowledgeBases({})
    baseList.value = (res as any).data || []
  } catch (e) {
    console.error(e)
  } finally {
    baseLoading.value = false
  }
}

async function fetchEmbeddingModels() {
  embeddingModelsLoading.value = true
  try {
    const res = await queryConfigs({ configType: 'llm', modelType: 'embedding', start: 1, limit: 100 })
    const data = (res as any).data
    embeddingModels.value = Array.isArray(data) ? data : (data?.list || [])
  } catch (e) {
    console.error(e)
    embeddingModels.value = []
  } finally {
    embeddingModelsLoading.value = false
  }
}

function getDefaultEmbeddingConfigId(): number | undefined {
  const defaultModel = embeddingModels.value.find((m) => m.isDefault === '1')
  return defaultModel?.configId
}

async function showCreateBaseModal() {
  newBase.knowledgeBaseName = ''
  newBase.description = ''
  newBase.embeddingConfigId = undefined
  await fetchEmbeddingModels()
  newBase.embeddingConfigId = getDefaultEmbeddingConfigId()
  createBaseModalVisible.value = true
}

async function showEditBaseModal(record: KnowledgeBase) {
  editBase.knowledgeBaseId = record.knowledgeBaseId
  editBase.knowledgeBaseName = record.knowledgeBaseName
  editBase.description = record.description || ''
  editBase.embeddingConfigId = record.embeddingConfigId || undefined
  editBase.originalEmbeddingConfigId = record.embeddingConfigId || undefined
  embeddingModelChanged.value = false
  await fetchEmbeddingModels()
  editBaseModalVisible.value = true
}

async function handleCreateBase() {
  if (!newBase.knowledgeBaseName.trim()) {
    message.warning(t('knowledge.baseNameRequired'))
    return
  }
  try {
    await createKnowledgeBase({
      knowledgeBaseName: newBase.knowledgeBaseName,
      description: newBase.description || undefined,
      embeddingConfigId: newBase.embeddingConfigId,
    })
    message.success(t('common.success'))
    createBaseModalVisible.value = false
    fetchBases()
  } catch (e) {
    message.error(t('common.error'))
  }
}

async function handleEditBase() {
  if (!editBase.knowledgeBaseName.trim()) {
    message.warning(t('knowledge.baseNameRequired'))
    return
  }
  try {
    await updateKnowledgeBase({
      knowledgeBaseId: editBase.knowledgeBaseId,
      knowledgeBaseName: editBase.knowledgeBaseName,
      description: editBase.description || undefined,
      embeddingConfigId: editBase.embeddingConfigId,
    })
    message.success(t('common.success'))
    editBaseModalVisible.value = false
    fetchBases()
  } catch (e) {
    message.error(t('common.error'))
  }
}

function onEditEmbeddingModelChange(value: number) {
  embeddingModelChanged.value = value !== editBase.originalEmbeddingConfigId
}

async function handleDeleteBase(record: KnowledgeBase) {
  try {
    await deleteKnowledgeBase(record.knowledgeBaseId)
    message.success(t('common.success'))
    fetchBases()
  } catch (e) {
    message.error(t('common.error'))
  }
}

// ==================== 文档操作 ====================

function viewDocs(record: KnowledgeBase) {
  currentBase.value = record
  docQueryParams.status = undefined
  docModalVisible.value = true
  pollingTimedOut.value = false
  fetchDocs()
  startPolling()
}

async function fetchDocs() {
  if (!currentBase.value) return
  docLoading.value = true
  try {
    const res = await queryKnowledgeDocs({
      knowledgeBaseId: currentBase.value.knowledgeBaseId,
      status: docQueryParams.status,
    })
    docList.value = (res as any).data || []
  } catch (e) {
    console.error(e)
  } finally {
    docLoading.value = false
  }
}

function beforeUpload(file: File) {
  if (!currentBase.value) {
    message.error(t('knowledge.selectBaseFirst'))
    return false
  }
  handleUpload(file)
  return false
}

async function handleUpload(file: File) {
  if (!currentBase.value) return
  try {
    const res = await uploadKnowledgeDoc(file, currentBase.value.knowledgeBaseId)
    const resAny = res as any
    // 检查后端是否返回了错误
    if (resAny.code !== undefined && resAny.code !== 0 && resAny.code !== 200) {
      // 后端返回了业务错误，显示具体错误信息
      const errorMsg = resAny.message || resAny.msg || t('knowledge.uploadFailed')
      message.error(errorMsg)
      // 检测 RAG 未启用
      if (errorMsg.includes('RAG') || errorMsg.includes('未启用')) {
        ragDisabledDetected.value = true
      }
      return
    }
    message.success(t('knowledge.uploadSuccess'))
    fetchDocs()
    // 上传后确保轮询运行中
    startPolling()
  } catch (e: any) {
    // 尝试从错误响应中提取具体信息
    const errorMsg = e?.response?.data?.message || e?.response?.data?.msg || e?.message || t('knowledge.uploadFailed')
    message.error(errorMsg)
    // 检测 RAG 未启用
    if (typeof errorMsg === 'string' && (errorMsg.includes('RAG') || errorMsg.includes('未启用'))) {
      ragDisabledDetected.value = true
    }
  }
}

async function handleDeleteDoc(record: KnowledgeDoc) {
  try {
    await deleteKnowledgeDoc(record.docId)
    message.success(t('common.success'))
    fetchDocs()
  } catch (e) {
    message.error(t('common.error'))
  }
}

async function handleReprocess(record: KnowledgeDoc) {
  try {
    await reprocessKnowledgeDoc(record.docId)
    message.success(t('knowledge.reprocessStarted'))
    fetchDocs()
    pollingTimedOut.value = false
    startPolling()
  } catch (e) {
    message.error(t('common.error'))
  }
}

// 为 processing/uploading 状态的文档添加轮询（每5秒），最大 60 次
function startPolling() {
  stopPolling()
  pollingCount = 0
  pollingTimedOut.value = false
  pollingTimer = setInterval(async () => {
    pollingCount++

    // 检查是否超过最大轮询次数
    if (pollingCount >= MAX_POLLING_COUNT) {
      pollingTimedOut.value = true
      stopPolling()
      return
    }

    // 检查是否有正在处理中的文档
    const processingDocs = docList.value.filter(
      (d) => d.status === 'processing' || d.status === 'uploading'
    )
    if (processingDocs.length === 0) {
      stopPolling()
      return
    }
    // 逐个查询状态更新
    let hasUpdate = false
    for (const doc of processingDocs) {
      try {
        const res = await getDocStatus(doc.docId)
        const updated = (res as any).data
        if (updated && updated.status !== doc.status) {
          hasUpdate = true
        }
      } catch (e) {
        // 忽略单个查询失败
      }
    }
    // 有状态变化时刷新整个列表
    if (hasUpdate) {
      fetchDocs()
    }
  }, 5000)
}

function stopPolling() {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

onMounted(() => {
  fetchBases()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.knowledge-container {
  padding: 0;
}
</style>

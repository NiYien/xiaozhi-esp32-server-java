<template>
  <div class="knowledge-container">
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
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="viewDocs(record)">
                {{ t('knowledge.viewDocs') }}
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
  deleteKnowledgeBase,
  queryKnowledgeDocs,
  uploadKnowledgeDoc,
  deleteKnowledgeDoc,
  reprocessKnowledgeDoc,
  getDocStatus,
  type KnowledgeBase,
  type KnowledgeDoc,
} from '@/services/knowledge'

const { t } = useI18n()

// 知识库相关
const baseLoading = ref(false)
const baseList = ref<KnowledgeBase[]>([])
const createBaseModalVisible = ref(false)
const newBase = reactive({
  knowledgeBaseName: '',
  description: '',
})

// 文档相关
const docLoading = ref(false)
const docList = ref<KnowledgeDoc[]>([])
const docModalVisible = ref(false)
const currentBase = ref<KnowledgeBase | null>(null)
const docQueryParams = reactive({
  status: undefined as string | undefined,
})

// W6：状态轮询定时器
let pollingTimer: ReturnType<typeof setInterval> | null = null

const baseColumns = computed(() => [
  { title: t('knowledge.baseName'), dataIndex: 'knowledgeBaseName', key: 'knowledgeBaseName', ellipsis: true },
  { title: t('knowledge.baseDesc'), dataIndex: 'description', key: 'description', ellipsis: true },
  { title: t('knowledge.createTime'), dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: t('knowledge.action'), key: 'action', width: 200, fixed: 'right' as const },
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

function showCreateBaseModal() {
  newBase.knowledgeBaseName = ''
  newBase.description = ''
  createBaseModalVisible.value = true
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
    })
    message.success(t('common.success'))
    createBaseModalVisible.value = false
    fetchBases()
  } catch (e) {
    message.error(t('common.error'))
  }
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
    await uploadKnowledgeDoc(file, currentBase.value.knowledgeBaseId)
    message.success(t('knowledge.uploadSuccess'))
    fetchDocs()
    // 上传后确保轮询运行中
    startPolling()
  } catch (e) {
    message.error(t('knowledge.uploadFailed'))
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
    startPolling()
  } catch (e) {
    message.error(t('common.error'))
  }
}

// W6：为 processing/uploading 状态的文档添加轮询（每5秒）
function startPolling() {
  stopPolling()
  pollingTimer = setInterval(async () => {
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

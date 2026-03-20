<template>
  <div class="mcp-server-container">
    <a-card :title="t('mcpServer.title')" :bordered="false">
      <template #extra>
        <a-space>
          <a-button @click="handleRefreshAll" :loading="refreshLoading">
            <reload-outlined />
            {{ t('mcpServer.refreshAll') }}
          </a-button>
          <a-button type="primary" @click="showAddModal">
            {{ t('common.add') }}
          </a-button>
        </a-space>
      </template>

      <a-table
        :columns="columns"
        :data-source="dataList"
        :loading="loading"
        :pagination="false"
        row-key="serverId"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'transportType'">
            <a-tag :color="record.transportType === 'sse' ? 'blue' : 'green'">
              {{ record.transportType === 'sse' ? 'SSE' : 'Streamable HTTP' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'serverUrl'">
            <a-tooltip :title="record.serverUrl">
              <span>{{ record.serverUrl.length > 50 ? record.serverUrl.substring(0, 50) + '...' : record.serverUrl }}</span>
            </a-tooltip>
          </template>
          <template v-if="column.key === 'enabled'">
            <a-switch
              :checked="record.enabled === 1"
              @change="(checked: boolean) => handleToggleEnabled(record, checked)"
            />
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="showEditModal(record)">
                {{ t('common.edit') }}
              </a-button>
              <a-popconfirm
                :title="t('mcpServer.confirmDelete')"
                @confirm="handleDelete(record)"
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

    <!-- 添加/编辑弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="isEdit ? t('mcpServer.editServer') : t('mcpServer.addServer')"
      @ok="handleSubmit"
      @cancel="modalVisible = false"
      :confirm-loading="submitLoading"
      width="600px"
    >
      <a-form layout="vertical" :model="formData">
        <a-form-item :label="t('mcpServer.serverName')" required>
          <a-input
            v-model:value="formData.serverName"
            :placeholder="t('mcpServer.enterServerName')"
          />
        </a-form-item>
        <a-form-item :label="t('mcpServer.serverCode')">
          <a-input
            v-model:value="formData.serverCode"
            :placeholder="t('mcpServer.enterServerCode')"
          />
          <div style="color: #999; font-size: 12px; margin-top: 4px;">
            {{ t('mcpServer.serverCodeTip') }}
          </div>
        </a-form-item>
        <a-form-item :label="t('mcpServer.transportType')" required>
          <a-select v-model:value="formData.transportType">
            <a-select-option value="sse">SSE</a-select-option>
            <a-select-option value="streamable_http">Streamable HTTP</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item :label="t('mcpServer.serverUrl')" required>
          <a-input
            v-model:value="formData.serverUrl"
            :placeholder="t('mcpServer.enterServerUrl')"
          />
        </a-form-item>
        <a-form-item :label="t('mcpServer.authType')">
          <a-select v-model:value="formData.authType">
            <a-select-option value="none">{{ t('mcpServer.authNone') }}</a-select-option>
            <a-select-option value="api_key">API Key</a-select-option>
            <a-select-option value="bearer">Bearer Token</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item
          v-if="formData.authType !== 'none'"
          :label="t('mcpServer.authToken')"
        >
          <a-input-password
            v-model:value="formData.authToken"
            :placeholder="t('mcpServer.enterAuthToken')"
          />
        </a-form-item>

        <!-- 测试连接 -->
        <a-form-item>
          <a-button @click="handleTestConnection" :loading="testLoading">
            {{ t('mcpServer.testConnection') }}
          </a-button>
          <span v-if="testResult" style="margin-left: 12px;">
            <a-tag v-if="testResult.success" color="success">
              {{ t('mcpServer.connectionSuccess') }} - {{ testResult.toolCount }} {{ t('mcpServer.toolsAvailable') }}
            </a-tag>
            <a-tag v-else color="error">
              {{ testResult.message }}
            </a-tag>
          </span>
        </a-form-item>

        <!-- 工具列表预览 -->
        <a-form-item v-if="testResult && testResult.success && testResult.tools && testResult.tools.length > 0">
          <div style="font-size: 13px; color: #666; margin-bottom: 8px;">{{ t('mcpServer.availableTools') }}:</div>
          <div style="max-height: 150px; overflow-y: auto;">
            <a-tag v-for="tool in testResult.tools" :key="tool" style="margin-bottom: 4px;">
              {{ tool }}
            </a-tag>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { useI18n } from 'vue-i18n'
import {
  queryMcpServers,
  addMcpServer,
  updateMcpServer,
  deleteMcpServer,
  testMcpServerConnection,
  refreshMcpServers,
  type McpServer,
  type TestConnectionResult,
} from '@/services/mcpServer'

const { t } = useI18n()

const loading = ref(false)
const dataList = ref<McpServer[]>([])
const modalVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const testLoading = ref(false)
const refreshLoading = ref(false)
const testResult = ref<TestConnectionResult | null>(null)

const formData = reactive({
  serverId: undefined as number | undefined,
  serverName: '',
  serverCode: '',
  transportType: 'sse',
  serverUrl: '',
  authType: 'none',
  authToken: '',
})

const columns = computed(() => [
  { title: t('mcpServer.serverName'), dataIndex: 'serverName', key: 'serverName', ellipsis: true },
  { title: t('mcpServer.serverCode'), dataIndex: 'serverCode', key: 'serverCode', width: 150 },
  { title: t('mcpServer.transportType'), dataIndex: 'transportType', key: 'transportType', width: 140 },
  { title: t('mcpServer.serverUrl'), dataIndex: 'serverUrl', key: 'serverUrl', ellipsis: true },
  { title: t('common.status'), key: 'enabled', width: 100 },
  { title: t('common.createTime'), dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: t('table.action'), key: 'action', width: 150, fixed: 'right' as const },
])

async function fetchList() {
  loading.value = true
  try {
    const res = await queryMcpServers({})
    dataList.value = (res as any).data || []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function showAddModal() {
  isEdit.value = false
  formData.serverId = undefined
  formData.serverName = ''
  formData.serverCode = ''
  formData.transportType = 'sse'
  formData.serverUrl = ''
  formData.authType = 'none'
  formData.authToken = ''
  testResult.value = null
  modalVisible.value = true
}

function showEditModal(record: McpServer) {
  isEdit.value = true
  formData.serverId = record.serverId
  formData.serverName = record.serverName
  formData.serverCode = record.serverCode
  formData.transportType = record.transportType
  formData.serverUrl = record.serverUrl
  formData.authType = record.authType
  formData.authToken = ''
  testResult.value = null
  modalVisible.value = true
}

async function handleSubmit() {
  if (!formData.serverName.trim()) {
    message.warning(t('mcpServer.enterServerName'))
    return
  }
  if (!formData.serverUrl.trim()) {
    message.warning(t('mcpServer.enterServerUrl'))
    return
  }

  submitLoading.value = true
  try {
    const data: Record<string, unknown> = {
      serverName: formData.serverName,
      serverCode: formData.serverCode || undefined,
      transportType: formData.transportType,
      serverUrl: formData.serverUrl,
      authType: formData.authType,
    }

    // 仅在用户输入了新的 authToken 时才提交
    if (formData.authToken) {
      data.authToken = formData.authToken
    }

    if (isEdit.value) {
      data.serverId = formData.serverId
      await updateMcpServer(data as Partial<McpServer>)
      message.success(t('common.updateSuccess'))
    } else {
      await addMcpServer(data as Partial<McpServer>)
      message.success(t('common.addSuccess'))
    }
    modalVisible.value = false
    fetchList()
  } catch (e: any) {
    message.error(e?.response?.data?.message || t('common.error'))
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(record: McpServer) {
  try {
    await deleteMcpServer(record.serverId)
    message.success(t('common.deleteSuccess'))
    fetchList()
  } catch (e) {
    message.error(t('common.deleteFailed'))
  }
}

async function handleToggleEnabled(record: McpServer, checked: boolean) {
  try {
    await updateMcpServer({
      serverId: record.serverId,
      enabled: checked ? 1 : 0,
    })
    message.success(t('common.updateSuccess'))
    fetchList()
  } catch (e) {
    message.error(t('common.updateFailed'))
  }
}

async function handleTestConnection() {
  if (!formData.serverUrl.trim()) {
    message.warning(t('mcpServer.enterServerUrl'))
    return
  }

  testLoading.value = true
  testResult.value = null
  try {
    const res = await testMcpServerConnection({
      serverUrl: formData.serverUrl,
      transportType: formData.transportType,
      authType: formData.authType,
      authToken: formData.authToken || undefined,
    })
    const data = (res as any).data
    if (data) {
      testResult.value = data as TestConnectionResult
    } else {
      // 如果 code 为 200 但没有 data，表示成功但没有详细信息
      if ((res as any).code === 200) {
        testResult.value = { success: true, message: t('mcpServer.connectionSuccess'), toolCount: 0, tools: [] }
      } else {
        testResult.value = { success: false, message: (res as any).message || t('mcpServer.connectionFailed') }
      }
    }
  } catch (e: any) {
    testResult.value = { success: false, message: e?.message || t('mcpServer.connectionFailed') }
  } finally {
    testLoading.value = false
  }
}

async function handleRefreshAll() {
  refreshLoading.value = true
  try {
    await refreshMcpServers()
    message.success(t('mcpServer.refreshSuccess'))
  } catch (e) {
    message.error(t('mcpServer.refreshFailed'))
  } finally {
    refreshLoading.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.mcp-server-container {
  padding: 0;
}
</style>

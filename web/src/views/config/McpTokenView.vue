<template>
  <div class="mcp-token-container">
    <a-card title="MCP 接入点管理" :bordered="false">
      <!-- 接入点地址 -->
      <a-alert
        v-if="endpointUrl"
        type="info"
        show-icon
        style="margin-bottom: 16px"
      >
        <template #message>
          <span>接入点地址：</span>
          <a-typography-text code>{{ endpointUrl }}&lt;your-token&gt;</a-typography-text>
          <a-tooltip title="复制完整地址（选择一个Token后自动填入）">
            <copy-outlined style="margin-left: 8px; cursor: pointer" @click="copyEndpointWithToken" />
          </a-tooltip>
        </template>
        <template #description>
          <span>在 mcp_pipe.py 配置中使用此地址。选择下方某个 Token 后可一键复制完整连接地址。</span>
        </template>
      </a-alert>

      <template #extra>
        <a-button type="primary" @click="showCreateModal">
          新建 Token
        </a-button>
      </template>

      <a-table
        :columns="columns"
        :data-source="dataList"
        :loading="loading"
        :pagination="false"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'token'">
            <a-typography-text code>{{ record.token }}</a-typography-text>
          </template>
          <template v-if="column.key === 'enabled'">
            <a-switch
              :checked="record.enabled === 1"
              @change="(checked: boolean) => handleToggleEnabled(record, checked)"
            />
          </template>
          <template v-if="column.key === 'lastUsedAt'">
            {{ record.lastUsedAt || '-' }}
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-tooltip title="Token 已脱敏，无法复制完整地址。请在创建时保存。">
                <a-button
                  type="link"
                  size="small"
                  disabled
                >
                  复制地址
                </a-button>
              </a-tooltip>
              <a-popconfirm
                title="确定要删除此 Token 吗？使用该 Token 的连接将被断开。"
                @confirm="handleDelete(record)"
              >
                <a-button type="link" danger size="small">
                  删除
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 创建Token弹窗 -->
    <a-modal
      v-model:open="createModalVisible"
      title="新建 MCP Token"
      @ok="handleCreate"
      @cancel="createModalVisible = false"
      :confirm-loading="createLoading"
    >
      <a-form layout="vertical">
        <a-form-item label="Token 名称" required>
          <a-input
            v-model:value="newTokenName"
            placeholder="为此 Token 命名，如：联网查询、文件助手"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 创建成功展示Token弹窗 -->
    <a-modal
      v-model:open="tokenShowVisible"
      title="Token 创建成功"
      :footer="null"
      :closable="true"
      @cancel="tokenShowVisible = false"
    >
      <a-alert type="warning" show-icon style="margin-bottom: 16px">
        <template #message>此 Token 仅显示一次，请妥善保存！</template>
      </a-alert>
      <a-input-group compact>
        <a-input :value="createdToken" readonly style="width: calc(100% - 80px)" />
        <a-button type="primary" @click="copyToken">
          <copy-outlined /> 复制
        </a-button>
      </a-input-group>
      <div style="margin-top: 12px">
        <span style="color: #666">完整连接地址：</span>
        <a-typography-text code style="word-break: break-all; font-size: 12px">
          {{ endpointUrl }}{{ createdToken }}
        </a-typography-text>
        <a-button type="link" size="small" @click="copyFullUrl(createdToken)">复制完整地址</a-button>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { CopyOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  queryMcpTokens,
  createMcpToken,
  enableMcpToken,
  disableMcpToken,
  deleteMcpToken,
  getEndpointUrl,
} from '../../services/mcpToken'
import type { McpToken } from '../../services/mcpToken'

const loading = ref(false)
const dataList = ref<McpToken[]>([])
const endpointUrl = ref('')

const createModalVisible = ref(false)
const createLoading = ref(false)
const newTokenName = ref('')

const tokenShowVisible = ref(false)
const createdToken = ref('')

const columns = [
  { title: '名称', dataIndex: 'tokenName', key: 'tokenName' },
  { title: 'Token', dataIndex: 'token', key: 'token', width: 150 },
  { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
  { title: '最后使用', dataIndex: 'lastUsedAt', key: 'lastUsedAt', width: 170 },
  { title: '操作', key: 'action', width: 160 },
]

async function loadData() {
  loading.value = true
  try {
    const res = await queryMcpTokens()
    dataList.value = (res as any).data || []
  } catch {
    message.error('加载Token列表失败')
  } finally {
    loading.value = false
  }
}

async function loadEndpoint() {
  try {
    const res = await getEndpointUrl()
    endpointUrl.value = (res as any).data?.endpointUrl || ''
  } catch {
    // ignore
  }
}

function showCreateModal() {
  newTokenName.value = ''
  createModalVisible.value = true
}

async function handleCreate() {
  if (!newTokenName.value.trim()) {
    message.warning('请输入Token名称')
    return
  }
  createLoading.value = true
  try {
    const res = await createMcpToken(newTokenName.value.trim())
    const data = (res as any).data
    if (data?.token) {
      createdToken.value = data.token
      createModalVisible.value = false
      tokenShowVisible.value = true
      loadData()
    }
  } catch {
    message.error('创建Token失败')
  } finally {
    createLoading.value = false
  }
}

async function handleToggleEnabled(record: McpToken, checked: boolean) {
  try {
    if (checked) {
      await enableMcpToken(record.id)
      message.success('已启用')
    } else {
      await disableMcpToken(record.id)
      message.success('已禁用')
    }
    loadData()
  } catch {
    message.error('操作失败')
  }
}

async function handleDelete(record: McpToken) {
  try {
    await deleteMcpToken(record.id)
    message.success('已删除')
    loadData()
  } catch {
    message.error('删除失败')
  }
}

function copyToken() {
  navigator.clipboard.writeText(createdToken.value)
  message.success('Token 已复制')
}

function copyFullUrl(token: string) {
  const url = endpointUrl.value + token
  navigator.clipboard.writeText(url)
  message.success('完整连接地址已复制')
}

function copyEndpointWithToken() {
  if (dataList.value.length > 0) {
    message.info('请点击下方某个 Token 行的"复制地址"按钮')
  } else {
    navigator.clipboard.writeText(endpointUrl.value)
    message.success('接入点地址已复制（需补充Token）')
  }
}

onMounted(() => {
  loadData()
  loadEndpoint()
})
</script>

<style scoped>
.mcp-token-container {
  padding: 0;
}
</style>

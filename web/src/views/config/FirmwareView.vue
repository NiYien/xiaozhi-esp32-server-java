<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import type { TableColumnsType, TablePaginationConfig } from 'ant-design-vue'
import { UploadOutlined, InboxOutlined } from '@ant-design/icons-vue'
import { useTable } from '@/composables/useTable'
import { useModal } from '@/composables/useModal'
import TableActionButtons from '@/components/TableActionButtons.vue'
import { queryFirmwares, deleteFirmware, setDefaultFirmware } from '@/services/firmware'
import { useUserStore } from '@/store/user'
import api from '@/services/api'
import type { Firmware } from '@/types/firmware'

const { t } = useI18n()
const userStore = useUserStore()

// ==================== 查询表单 ====================
const searchForm = ref({
  firmwareName: '',
  chipModelName: '',
})

// ==================== 表格 ====================
const { loading, data: firmwareList, pagination, handleTableChange, loadData, createDebouncedSearch } = useTable<Firmware>()

const columns = computed<TableColumnsType>(() => [
  {
    title: t('firmware.firmwareName'),
    dataIndex: 'firmwareName',
    width: 200,
    align: 'center',
  },
  {
    title: t('firmware.version'),
    dataIndex: 'version',
    width: 100,
    align: 'center',
  },
  {
    title: t('firmware.chipModelName'),
    dataIndex: 'chipModelName',
    width: 120,
    align: 'center',
  },
  {
    title: t('firmware.deviceType'),
    dataIndex: 'deviceType',
    width: 120,
    align: 'center',
  },
  {
    title: t('firmware.fileSize'),
    dataIndex: 'fileSize',
    width: 100,
    align: 'center',
  },
  {
    title: t('common.isDefault'),
    dataIndex: 'isDefault',
    width: 100,
    align: 'center',
  },
  {
    title: t('firmware.description'),
    dataIndex: 'description',
    align: 'center',
    ellipsis: true,
  },
  {
    title: t('firmware.createTime'),
    dataIndex: 'createTime',
    width: 180,
    align: 'center',
  },
  {
    title: t('table.action'),
    key: 'operation',
    width: 180,
    align: 'center',
    fixed: 'right',
  },
])

// ==================== 数据加载 ====================
const fetchData = () => {
  loadData((params) =>
    queryFirmwares({
      ...searchForm.value,
      ...params,
    })
  )
}

onMounted(fetchData)

const debouncedSearch = createDebouncedSearch(fetchData)

const handleSearch = () => {
  debouncedSearch()
}

const handleReset = () => {
  searchForm.value = { firmwareName: '', chipModelName: '' }
  fetchData()
}

// ==================== 上传弹窗 ====================
const uploadVisible = ref(false)
const uploadForm = reactive({
  firmwareName: '',
  version: '',
  chipModelName: '',
  deviceType: '',
  description: '',
  isDefault: '0',
})
const fileList = ref<any[]>([])
const uploading = ref(false)

const openUploadModal = () => {
  uploadForm.firmwareName = ''
  uploadForm.version = ''
  uploadForm.chipModelName = ''
  uploadForm.deviceType = ''
  uploadForm.description = ''
  uploadForm.isDefault = '0'
  fileList.value = []
  uploadVisible.value = true
}

const beforeUpload = (file: File) => {
  fileList.value = [file]
  return false // prevent auto upload
}

const handleRemoveFile = () => {
  fileList.value = []
}

const handleUpload = async () => {
  if (fileList.value.length === 0) {
    message.error(t('firmware.pleaseSelectFile'))
    return
  }
  if (!uploadForm.firmwareName) {
    message.error(t('firmware.pleaseEnterName'))
    return
  }
  if (!uploadForm.version) {
    message.error(t('firmware.pleaseEnterVersion'))
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', fileList.value[0])
    formData.append('firmwareName', uploadForm.firmwareName)
    formData.append('version', uploadForm.version)
    if (uploadForm.chipModelName) formData.append('chipModelName', uploadForm.chipModelName)
    if (uploadForm.deviceType) formData.append('deviceType', uploadForm.deviceType)
    if (uploadForm.description) formData.append('description', uploadForm.description)
    formData.append('isDefault', uploadForm.isDefault)

    const baseURL = import.meta.env.VITE_API_BASE_URL || ''
    const uploadUrl = `${baseURL}/firmware/upload`

    const response = await fetch(uploadUrl, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${userStore.token}`,
      },
      body: formData,
    })

    const result = await response.json()
    if (result.code === 200) {
      message.success(t('firmware.uploadSuccess'))
      uploadVisible.value = false
      fetchData()
    } else {
      message.error(result.message || t('firmware.uploadFailed'))
    }
  } catch (error) {
    message.error(t('firmware.uploadFailed'))
  } finally {
    uploading.value = false
  }
}

// ==================== 操作 ====================
const handleDelete = async (record: Firmware) => {
  try {
    const res = await deleteFirmware(record.firmwareId)
    if (res.code === 200) {
      message.success(t('common.success'))
      fetchData()
    } else {
      message.error(res.message || t('common.error'))
    }
  } catch (error) {
    message.error(t('common.error'))
  }
}

const handleSetDefault = async (record: Firmware) => {
  try {
    const res = await setDefaultFirmware(record.firmwareId)
    if (res.code === 200) {
      message.success(t('common.success'))
      fetchData()
    } else {
      message.error(res.message || t('common.error'))
    }
  } catch (error) {
    message.error(t('common.error'))
  }
}

/**
 * 格式化文件大小
 */
const formatFileSize = (size: number | null): string => {
  if (!size) return '-'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / (1024 * 1024)).toFixed(2) + ' MB'
}
</script>

<template>
  <div class="firmware-view">
    <!-- 搜索表单 -->
    <a-card :bordered="false" style="margin-bottom: 16px">
      <a-form layout="inline">
        <a-form-item :label="t('firmware.firmwareName')">
          <a-input
            v-model:value="searchForm.firmwareName"
            :placeholder="t('common.pleaseEnter')"
            allow-clear
            @change="handleSearch"
          />
        </a-form-item>
        <a-form-item :label="t('firmware.chipModelName')">
          <a-input
            v-model:value="searchForm.chipModelName"
            :placeholder="t('common.pleaseEnter')"
            allow-clear
            @change="handleSearch"
          />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="handleSearch">{{ t('common.search') }}</a-button>
            <a-button @click="handleReset">{{ t('common.reset') }}</a-button>
            <a-button type="primary" @click="openUploadModal">
              <template #icon><UploadOutlined /></template>
              {{ t('firmware.upload') }}
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 数据表格 -->
    <a-card :bordered="false">
      <a-table
        :columns="columns"
        :data-source="firmwareList"
        :loading="loading"
        :pagination="pagination"
        row-key="firmwareId"
        :scroll="{ x: 1200 }"
        @change="handleTableChange as any"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'chipModelName'">
            {{ record.chipModelName || t('firmware.universal') }}
          </template>
          <template v-else-if="column.dataIndex === 'deviceType'">
            {{ record.deviceType || t('firmware.universal') }}
          </template>
          <template v-else-if="column.dataIndex === 'fileSize'">
            {{ formatFileSize(record.fileSize) }}
          </template>
          <template v-else-if="column.dataIndex === 'isDefault'">
            <a-tag :color="record.isDefault === '1' ? 'green' : 'default'">
              {{ record.isDefault === '1' ? t('common.yes') : t('common.no') }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'operation'">
            <TableActionButtons
              :record="record"
              show-delete
              show-set-default
              :is-default="record.isDefault === '1'"
              @delete="handleDelete"
              @set-default="handleSetDefault"
            />
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 上传弹窗 -->
    <a-modal
      v-model:open="uploadVisible"
      :title="t('firmware.upload')"
      :confirm-loading="uploading"
      @ok="handleUpload"
      :width="600"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item :label="t('firmware.firmwareName')" required>
          <a-input v-model:value="uploadForm.firmwareName" :placeholder="t('firmware.pleaseEnterName')" />
        </a-form-item>
        <a-form-item :label="t('firmware.version')" required>
          <a-input v-model:value="uploadForm.version" placeholder="1.0.0" />
        </a-form-item>
        <a-form-item :label="t('firmware.chipModelName')">
          <a-input v-model:value="uploadForm.chipModelName" :placeholder="t('firmware.chipModelNamePlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('firmware.deviceType')">
          <a-input v-model:value="uploadForm.deviceType" :placeholder="t('firmware.deviceTypePlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('firmware.description')">
          <a-textarea v-model:value="uploadForm.description" :rows="3" :placeholder="t('firmware.descriptionPlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('firmware.setAsDefault')">
          <a-switch
            :checked="uploadForm.isDefault === '1'"
            @change="(checked: boolean) => uploadForm.isDefault = checked ? '1' : '0'"
          />
        </a-form-item>
        <a-form-item :label="t('firmware.file')" required>
          <a-upload
            :file-list="fileList"
            :before-upload="beforeUpload"
            :max-count="1"
            @remove="handleRemoveFile"
            accept=".bin,.uf2,.hex"
          >
            <a-button>
              <template #icon><UploadOutlined /></template>
              {{ t('firmware.selectFile') }}
            </a-button>
          </a-upload>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped lang="scss">
.firmware-view {
  padding: 0;
}
</style>

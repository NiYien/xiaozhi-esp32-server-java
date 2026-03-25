<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import { SoundOutlined, CaretRightOutlined, PauseOutlined } from '@ant-design/icons-vue'
import { queryUserAudios } from '@/services/message'
import AudioPlayer from '@/components/AudioPlayer.vue'
import type { Message } from '@/types/message'

interface Props {
  open: boolean
  deviceId: string
  minDuration: number
  mode?: 'single-group' | 'multi-group'
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'multi-group',
})

const emit = defineEmits<{
  (e: 'update:open', val: boolean): void
  (e: 'confirm', messageIds: number[]): void
}>()

// 音频消息列表
const audioMessages = ref<Message[]>([])
const loading = ref(false)

// 按 audioGroup 分组
interface AudioGroup {
  groupId: string
  messages: Message[]
  totalDuration: number
}

const audioGroups = computed<AudioGroup[]>(() => {
  const groupMap = new Map<string, Message[]>()
  for (const msg of audioMessages.value) {
    const key = msg.audioGroup || `single_${msg.messageId}`
    if (!groupMap.has(key)) {
      groupMap.set(key, [])
    }
    groupMap.get(key)!.push(msg)
  }
  return Array.from(groupMap.entries()).map(([groupId, messages]) => ({
    groupId,
    messages,
    totalDuration: messages.reduce((sum, m) => sum + (m.audioDuration || 0), 0),
  }))
})

// 选中状态
const selectedMessageIds = ref<Set<number>>(new Set())

// 展开的组
const expandedGroups = ref<Set<string>>(new Set())

// 已选总时长
const selectedTotalDuration = computed(() => {
  let total = 0
  for (const msg of audioMessages.value) {
    if (selectedMessageIds.value.has(msg.messageId)) {
      total += msg.audioDuration || 0
    }
  }
  return total
})

// 进度百分比
const progressPercent = computed(() => {
  if (props.minDuration <= 0) return 100
  return Math.min(100, Math.round((selectedTotalDuration.value / props.minDuration) * 100))
})

// 确认按钮是否可用
const canConfirm = computed(() => selectedTotalDuration.value >= props.minDuration)

// 加载数据
async function loadData() {
  loading.value = true
  try {
    const res = await queryUserAudios({ deviceId: props.deviceId })
    if (res.code === 200 && res.data) {
      audioMessages.value = Array.isArray(res.data) ? res.data : []
    }
  } catch (e) {
    console.error('加载用户音频失败:', e)
  } finally {
    loading.value = false
  }
}

// 组级选择
function toggleGroup(group: AudioGroup) {
  const allSelected = group.messages.every((m) => selectedMessageIds.value.has(m.messageId))
  const newSet = new Set(selectedMessageIds.value)
  if (allSelected) {
    group.messages.forEach((m) => newSet.delete(m.messageId))
  } else {
    group.messages.forEach((m) => newSet.add(m.messageId))
  }
  selectedMessageIds.value = newSet
}

// 单条选择
function toggleMessage(messageId: number) {
  const newSet = new Set(selectedMessageIds.value)
  if (newSet.has(messageId)) {
    newSet.delete(messageId)
  } else {
    newSet.add(messageId)
  }
  selectedMessageIds.value = newSet
}

// 组是否全选
function isGroupSelected(group: AudioGroup): boolean {
  return group.messages.length > 0 && group.messages.every((m) => selectedMessageIds.value.has(m.messageId))
}

// 组是否部分选中
function isGroupIndeterminate(group: AudioGroup): boolean {
  const selected = group.messages.filter((m) => selectedMessageIds.value.has(m.messageId)).length
  return selected > 0 && selected < group.messages.length
}

// 展开/收起组
function toggleExpand(groupId: string) {
  const newSet = new Set(expandedGroups.value)
  if (newSet.has(groupId)) {
    newSet.delete(groupId)
  } else {
    newSet.add(groupId)
  }
  expandedGroups.value = newSet
}

// 确认选择
function handleConfirm() {
  const ids = Array.from(selectedMessageIds.value)
  emit('confirm', ids)
  emit('update:open', false)
}

// 格式化时长
function formatDuration(seconds: number): string {
  if (seconds <= 0) return '0s'
  if (seconds < 60) return `${seconds.toFixed(1)}s`
  const min = Math.floor(seconds / 60)
  const sec = Math.round(seconds % 60)
  return `${min}m${sec}s`
}

// 监听 open，打开时加载数据
watch(
  () => props.open,
  (val) => {
    if (val) {
      selectedMessageIds.value = new Set()
      expandedGroups.value = new Set()
      loadData()
    }
  }
)
</script>

<template>
  <a-modal
    :open="open"
    title="从对话记录选择音频"
    width="700px"
    :ok-button-props="{ disabled: !canConfirm }"
    ok-text="确认选择"
    cancel-text="取消"
    @ok="handleConfirm"
    @cancel="emit('update:open', false)"
  >
    <a-spin :spinning="loading">
      <div v-if="audioGroups.length === 0 && !loading" style="text-align: center; padding: 40px">
        <a-empty description="暂无用户音频记录" />
      </div>

      <div v-else class="audio-group-list">
        <div v-for="group in audioGroups" :key="group.groupId" class="audio-group-card">
          <div class="audio-group-card__header" @click="toggleExpand(group.groupId)">
            <a-checkbox
              :checked="isGroupSelected(group)"
              :indeterminate="isGroupIndeterminate(group)"
              @click.stop
              @change="toggleGroup(group)"
            />
            <div class="audio-group-card__info">
              <span class="audio-group-card__text">
                {{ group.messages.map((m) => m.message || '').join(' ') }}
              </span>
              <span class="audio-group-card__duration">
                <SoundOutlined style="margin-right: 4px" />
                {{ formatDuration(group.totalDuration) }}
                <span v-if="group.messages.length > 1" style="margin-left: 8px; color: var(--ant-color-text-quaternary)">
                  ({{ group.messages.length }} 段)
                </span>
              </span>
            </div>
            <a-button type="link" size="small" v-if="group.messages.length > 1">
              {{ expandedGroups.has(group.groupId) ? '收起' : '展开' }}
            </a-button>
          </div>

          <!-- 组内单条展开 -->
          <div v-if="expandedGroups.has(group.groupId) && group.messages.length > 1" class="audio-group-card__items">
            <div v-for="msg in group.messages" :key="msg.messageId" class="audio-item">
              <a-checkbox
                :checked="selectedMessageIds.has(msg.messageId)"
                @change="toggleMessage(msg.messageId)"
              />
              <span class="audio-item__text">{{ msg.message || '(无文字)' }}</span>
              <span class="audio-item__duration">{{ formatDuration(msg.audioDuration || 0) }}</span>
              <div v-if="msg.audioPath" class="audio-item__player">
                <AudioPlayer :audio-url="msg.audioPath" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-spin>

    <!-- 底部汇总 -->
    <template #footer>
      <div class="dialog-footer">
        <div class="dialog-footer__summary">
          <span>已选: {{ formatDuration(selectedTotalDuration) }}</span>
          <span v-if="!canConfirm" style="color: var(--ant-color-error); margin-left: 8px">
            还需 {{ formatDuration(minDuration - selectedTotalDuration) }}
          </span>
        </div>
        <div v-if="minDuration >= 10" class="dialog-footer__progress">
          <a-progress :percent="progressPercent" :size="'small'" :status="canConfirm ? 'success' : 'active'" />
        </div>
        <div class="dialog-footer__actions">
          <a-button @click="emit('update:open', false)">取消</a-button>
          <a-button type="primary" :disabled="!canConfirm" @click="handleConfirm">确认选择</a-button>
        </div>
      </div>
    </template>
  </a-modal>
</template>

<style scoped lang="scss">
.audio-group-list {
  max-height: 400px;
  overflow-y: auto;
}

.audio-group-card {
  border: 1px solid var(--ant-color-border);
  border-radius: 8px;
  margin-bottom: 8px;
  overflow: hidden;

  &__header {
    display: flex;
    align-items: center;
    padding: 10px 12px;
    cursor: pointer;
    gap: 10px;

    &:hover {
      background-color: var(--ant-color-bg-text-hover);
    }
  }

  &__info {
    flex: 1;
    min-width: 0;
  }

  &__text {
    display: block;
    font-size: 13px;
    color: var(--ant-color-text);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__duration {
    display: block;
    font-size: 12px;
    color: var(--ant-color-text-secondary);
    margin-top: 2px;
  }

  &__items {
    border-top: 1px solid var(--ant-color-border-secondary);
    padding: 4px 12px 8px 32px;
  }
}

.audio-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid var(--ant-color-border-secondary);

  &:last-child {
    border-bottom: none;
  }

  &__text {
    flex: 1;
    font-size: 12px;
    color: var(--ant-color-text);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__duration {
    font-size: 11px;
    color: var(--ant-color-text-tertiary);
    white-space: nowrap;
  }

  &__player {
    flex-shrink: 0;
    max-width: 150px;
  }
}

.dialog-footer {
  display: flex;
  align-items: center;
  gap: 12px;

  &__summary {
    font-size: 13px;
    color: var(--ant-color-text-secondary);
    white-space: nowrap;
  }

  &__progress {
    flex: 1;
    min-width: 100px;
  }

  &__actions {
    display: flex;
    gap: 8px;
    margin-left: auto;
  }
}
</style>

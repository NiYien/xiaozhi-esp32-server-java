<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/store/user'
import { useAvatar } from '@/composables/useAvatar'
import { checkToken } from '@/services/user'
import dayjs from 'dayjs'
// @ts-ignore
import jsonp from 'jsonp'
import MessageView from './MessageView.vue'

const { t } = useI18n()

// 类型定义
interface DailySentence {
  content: string
  note: string
}

const userStore = useUserStore()
const { getAvatarUrl } = useAvatar()

// 状态
const loading = ref(false)
const sentenceShow = ref(true)
const sentence = ref<DailySentence>({
  content: '每一天都是新的开始',
  note: 'Every day is a new beginning'
})

// 计算属性
const timeFix = computed(() => {
  const hour = dayjs().hour()
  if (hour < 9) return t('dashboard.greeting.morning')
  if (hour < 12) return t('dashboard.greeting.forenoon')
  if (hour < 14) return t('dashboard.greeting.noon')
  if (hour < 18) return t('dashboard.greeting.afternoon')
  if (hour < 22) return t('dashboard.greeting.evening')
  return t('dashboard.greeting.night')
})

const welcomeText = computed(() => {
  const arr = [
    t('dashboard.welcome.rest'),
    t('dashboard.welcome.eat'),
    t('dashboard.welcome.game'),
    t('dashboard.welcome.tired')
  ]
  const index = Math.floor(Math.random() * arr.length)
  return arr[index]
})

const userInfo = computed(() => userStore.userInfo)

// 页面加载时刷新用户信息（获取最新统计数据）
onMounted(async () => {
  try {
    const res = await checkToken()
    if (res.code === 200 && res.data?.user) {
      userStore.setUserInfo(res.data.user)
    }
  } catch {
    // 静默处理，不影响页面展示
  }
})

const userAvatar = computed(() => {
  if (userInfo.value?.avatar) {
    return getAvatarUrl(userInfo.value.avatar)
  }
  return '/user-avatar.png'
})

// 获取每日一句
const getSentence = () => {
  const day = dayjs().format('YYYY-MM-DD')
  jsonp(`https://sentence.iciba.com/index.php?c=dailysentence&m=getdetail&title=${day}`, {
    param: 'callback'
  }, (err: Error | null, data: unknown) => {
    if (err) {
      console.log(t('dashboard.getSentenceFailed'))
    } else {
      const result = data as { content?: string; note?: string } | null
      sentence.value = {
        content: result?.content || sentence.value.content,
        note: result?.note || sentence.value.note
      }
    }
  })
}

// 初始化
await Promise.all([
  getSentence()
])
</script>

<template>
  <div class="dashboard-view">
    <!-- 用户信息卡片 -->
    <a-card :bordered="false" class="user-info-card" :loading="loading">
      <div class="user-info-content">
        <a-avatar :src="userAvatar" :size="72" class="user-avatar" />
        <div class="user-greeting">
          <h2>{{ timeFix }}，{{ userInfo?.name || userInfo?.username }}，{{ welcomeText }}</h2>
          <a-tooltip :title="t('dashboard.clickToTranslate')" placement="bottomLeft">
            <p class="daily-sentence" @click="sentenceShow = !sentenceShow">
              {{ sentenceShow ? sentence.content : sentence.note }}
            </p>
          </a-tooltip>
        </div>
        <div class="user-statistics">
          <a-statistic
            :title="t('dashboard.conversationCount')"
            :value="userInfo?.totalMessage || 0"
            class="statistic-item"
          />
          <a-statistic
            :title="t('dashboard.activeDevices')"
            :value="userInfo?.aliveNumber || 0"
            class="statistic-item"
          />
          <a-statistic
            :title="t('dashboard.totalDevices')"
            :value="userInfo?.totalDevice || 0"
            class="statistic-item"
          />
        </div>
      </div>
    </a-card>

    <!-- 嵌入对话管理页面 -->
    <div class="message-view-container">
      <MessageView />
    </div>

    <a-back-top />
  </div>
</template>

<style scoped lang="scss">
.dashboard-view {
  padding: 24px;
  max-width: 1600px;
  margin: 0 auto;
}

// 用户信息卡片
.user-info-card {
  margin-bottom: 20px;
  border-radius: 12px;

  :deep(.ant-card-body) {
    padding: 32px;
  }
}

.user-info-content {
  display: flex;
  gap: 24px;
  align-items: center;
}

.user-avatar {
  flex-shrink: 0;
  border: 3px solid var(--primary-color);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.user-greeting {
  flex: 1;
  min-width: 0;

  h2 {
    font-size: 20px;
    font-weight: 600;
    margin: 0 0 12px 0;
    color: var(--text-color);
    line-height: 1.4;
  }

  .daily-sentence {
    font-size: 14px;
    color: var(--text-secondary);
    margin: 0;
    cursor: pointer;
    transition: color 0.3s;
    line-height: 1.6;

    &:hover {
      color: var(--primary-color);
    }
  }
}

.user-statistics {
  display: flex;
  gap: 32px;
  flex-shrink: 0;

  .statistic-item {
    text-align: right;

    :deep(.ant-statistic-title) {
      font-size: 14px;
      color: var(--text-secondary);
    }

    :deep(.ant-statistic-content) {
      font-size: 24px;
      font-weight: 600;
      color: var(--primary-color);
    }
  }
}

// 对话管理容器
.message-view-container {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

// 响应式
@media (max-width: 768px) {
  .dashboard-view {
    padding: 16px;
  }

  .user-info-card {
    :deep(.ant-card-body) {
      padding: 20px;
    }
  }

  .user-info-content {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .user-greeting {
    h2 {
      font-size: 18px;
    }
  }

  .user-statistics {
    flex-wrap: wrap;
    justify-content: center;
    gap: 24px;
  }
}

// 卡片样式统一
:deep(.ant-card) {
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);

  .ant-card-head {
    border-bottom: 1px solid var(--border-color);

    .ant-card-head-title {
      font-size: 16px;
      font-weight: 600;
    }
  }
}
</style>

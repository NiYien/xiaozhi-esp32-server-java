<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useAvatar } from '@/composables/useAvatar'
import { useAuth } from '@/composables/useAuth'
import {
  HomeOutlined,
  LaptopOutlined,
  SettingOutlined,
  LogoutOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { getAvatarUrl } = useAvatar()
const { logout } = useAuth()

// 客户端宽度
const clientWidth = ref(document.body.clientWidth)

// 是否是移动端
const isMobile = computed(() => clientWidth.value < 768)

// 用户信息
const userInfo = computed(() => userStore.userInfo)
const avatarUrl = computed(() => getAvatarUrl(userInfo.value?.avatar))

// Tab 导航项
const tabItems = [
  { key: '/u/dashboard', icon: HomeOutlined, label: '首页' },
  { key: '/u/devices', icon: LaptopOutlined, label: '设备' },
  { key: '/u/settings', icon: SettingOutlined, label: '设置' },
]

// 当前激活的 Tab
const activeTab = computed(() => {
  // 对话页归属于设备 Tab
  if (route.path.startsWith('/u/chat')) return '/u/devices'
  return route.path
})

/**
 * 处理 Tab 切换
 */
function handleTabChange(key: string) {
  router.push(key)
}

/**
 * 处理窗口大小变化
 */
function handleResize() {
  clientWidth.value = document.body.clientWidth
  userStore.setMobileType(isMobile.value)
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  handleResize()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <div class="user-layout" :class="{ 'is-mobile': isMobile }">
    <!-- 顶部导航栏 -->
    <header class="user-header">
      <div class="header-content">
        <div class="header-left">
          <span class="app-title" @click="router.push('/u/dashboard')">
            Connect AI
          </span>
        </div>

        <!-- 桌面端导航 -->
        <nav v-if="!isMobile" class="header-nav">
          <a
            v-for="item in tabItems"
            :key="item.key"
            class="nav-item"
            :class="{ active: activeTab === item.key }"
            @click="handleTabChange(item.key)"
          >
            <component :is="item.icon" />
            <span>{{ item.label }}</span>
          </a>
        </nav>

        <div class="header-right">
          <a-dropdown>
            <div class="user-info">
              <a-avatar :src="avatarUrl" :size="32">
                <template #icon><UserOutlined /></template>
              </a-avatar>
              <span v-if="!isMobile" class="username">{{ userInfo?.name || userInfo?.username }}</span>
            </div>
            <template #overlay>
              <a-menu>
                <a-menu-item key="settings" @click="router.push('/u/settings')">
                  <SettingOutlined />
                  <span style="margin-left: 8px">个人设置</span>
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout" @click="logout">
                  <LogoutOutlined />
                  <span style="margin-left: 8px">退出登录</span>
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </div>
    </header>

    <!-- 内容区 -->
    <main class="user-content">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" :key="$route.fullPath" />
        </transition>
      </router-view>
    </main>

    <!-- 移动端底部 Tab Bar -->
    <footer v-if="isMobile" class="user-tabbar">
      <div
        v-for="item in tabItems"
        :key="item.key"
        class="tabbar-item"
        :class="{ active: activeTab === item.key }"
        @click="handleTabChange(item.key)"
      >
        <component :is="item.icon" class="tabbar-icon" />
        <span class="tabbar-label">{{ item.label }}</span>
      </div>
    </footer>
  </div>
</template>

<style scoped lang="scss">
.user-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--ant-color-bg-layout);
}

// 顶部导航栏
.user-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--ant-color-bg-container);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);

  .header-content {
    max-width: 1200px;
    margin: 0 auto;
    padding: 0 24px;
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
}

.header-left {
  .app-title {
    font-size: 18px;
    font-weight: 600;
    color: var(--ant-color-primary);
    cursor: pointer;
    user-select: none;
  }
}

// 桌面端导航
.header-nav {
  display: flex;
  gap: 8px;

  .nav-item {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 6px 16px;
    border-radius: 6px;
    color: var(--ant-color-text-secondary);
    cursor: pointer;
    transition: all 0.2s;
    font-size: 14px;

    &:hover {
      color: var(--ant-color-primary);
      background: var(--ant-color-primary-bg);
    }

    &.active {
      color: var(--ant-color-primary);
      background: var(--ant-color-primary-bg);
      font-weight: 500;
    }
  }
}

.header-right {
  .user-info {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    padding: 4px 8px;
    border-radius: 6px;
    transition: background 0.2s;

    &:hover {
      background: var(--ant-color-bg-text-hover);
    }

    .username {
      font-size: 14px;
      color: var(--ant-color-text);
      max-width: 120px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

// 内容区
.user-content {
  flex: 1;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 16px 24px;
}

// 移动端底部 Tab Bar
.user-tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: var(--ant-color-bg-container);
  box-shadow: 0 -1px 4px rgba(0, 0, 0, 0.08);
  display: flex;
  justify-content: space-around;
  padding: 6px 0;
  padding-bottom: calc(6px + env(safe-area-inset-bottom));

  .tabbar-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    padding: 4px 16px;
    cursor: pointer;
    color: var(--ant-color-text-secondary);
    transition: color 0.2s;

    &.active {
      color: var(--ant-color-primary);
    }

    .tabbar-icon {
      font-size: 20px;
    }

    .tabbar-label {
      font-size: 11px;
    }
  }
}

// 移动端适配
.is-mobile {
  .user-content {
    padding: 12px 16px;
    // 底部留出 Tab Bar 空间
    padding-bottom: calc(60px + env(safe-area-inset-bottom));
  }

  .header-content {
    padding: 0 16px;
  }
}

// 页面过渡动画
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

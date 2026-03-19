import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'
import UserLayout from '../layouts/UserLayout.vue'

// 扩展 RouteMeta 类型
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    icon?: string
    requiresAuth?: boolean
    isAdmin?: boolean
    isUserRoute?: boolean // 用户端路由标记
    parent?: string
    hideInMenu?: boolean
    permission?: string // 单个权限
    permissions?: string[] // 多个权限（任一即可）
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: {
      title: 'router.title.login',
      requiresAuth: false,
    },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue'),
    meta: {
      title: 'router.title.register',
      requiresAuth: false,
    },
  },
  {
    path: '/forget',
    name: 'forget',
    component: () => import('../views/ForgetView.vue'),
    meta: {
      title: 'router.title.forget',
      requiresAuth: false,
    },
  },

  // 主应用路由
  {
    path: '/',
    component: MainLayout,
    // redirect: '/dashboard', // 在路由守卫中动态处理
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/DashboardView.vue'),
        meta: {
          title: 'router.title.dashboard',
          icon: 'DashboardOutlined',
          requiresAuth: true,
          permission: 'system:dashboard',
        },
      },
      {
        path: 'monitor',
        name: 'monitor',
        component: () => import('../views/MonitorView.vue'),
        meta: {
          title: 'router.title.monitor',
          icon: 'MonitorOutlined',
          requiresAuth: true,
          permission: 'system:monitor',
        },
      },
      {
        path: 'user',
        name: 'user',
        component: () => import('../views/UserView.vue'),
        meta: {
          title: 'router.title.user',
          icon: 'TeamOutlined',
          requiresAuth: true,
          permission: 'system:user',
        },
      },
      {
        path: 'device',
        name: 'device',
        component: () => import('../views/DeviceView.vue'),
        meta: {
          title: 'router.title.device',
          icon: 'RobotOutlined',
          requiresAuth: true,
          permission: 'system:device',
        },
      },
      {
        path: 'role',
        name: 'role',
        component: () => import('../views/RoleView.vue'),
        meta: {
          title: 'router.title.role',
          icon: 'UserAddOutlined',
          requiresAuth: true,
          permission: 'system:role',
        },
      },
      {
        path: 'knowledge',
        name: 'knowledge',
        component: () => import('../views/KnowledgeView.vue'),
        meta: {
          title: 'router.title.knowledge',
          icon: 'ReadOutlined',
          requiresAuth: true,
          permission: 'system:knowledge',
        },
      },
      {
        path: 'template',
        name: 'template',
        component: () => import('../views/TemplateView.vue'),
        meta: {
          title: 'router.title.template',
          icon: 'SnippetsOutlined',
          parent: 'router.parent.roleManagement',
          requiresAuth: true,
          permission: 'system:prompt-template',
          hideInMenu: true
        },
      },
      {
        path: 'memory/chat',
        name: 'memory-chat',
        component: () => import('../views/MemoryManagementView.vue'),
        meta: {
          title: 'router.title.shortTermMemory',
          parent: 'router.parent.memoryManagement',
          requiresAuth: true,
          permission: 'system:message',
        },
      },
      {
        path: 'memory/summary',
        name: 'memory-summary',
        component: () => import('../views/MemoryManagementView.vue'),
        meta: {
          title: 'router.title.summaryMemory',
          parent: 'router.parent.memoryManagement',
          requiresAuth: true,
          permission: 'system:message',
        },
      },
      // 配置管理
      {
        path: 'config/model',
        name: 'config-model',
        component: () => import('../views/config/ModelConfigView.vue'),
        meta: {
          title: 'router.title.modelConfig',
          parent: 'router.parent.configManagement',
          requiresAuth: true,
          permission: 'system:config:model',
        },
      },
      {
        path: 'config/agent',
        name: 'config-agent',
        component: () => import('../views/config/AgentView.vue'),
        meta: {
          title: 'router.title.agent',
          parent: 'router.parent.configManagement',
          requiresAuth: true,
          permission: 'system:config:agent',
        },
      },
      {
        path: 'config/stt',
        name: 'config-stt',
        component: () => import('../views/config/SttConfigView.vue'),
        meta: {
          title: 'router.title.sttConfig',
          parent: 'router.parent.configManagement',
          requiresAuth: true,
          permission: 'system:config:stt',
        },
      },
      {
        path: 'config/tts',
        name: 'config-tts',
        component: () => import('../views/config/TtsConfigView.vue'),
        meta: {
          title: 'router.title.ttsConfig',
          parent: 'router.parent.configManagement',
          requiresAuth: true,
          permission: 'system:config:tts',
        },
      },
      {
        path: 'config/firmware',
        name: 'config-firmware',
        component: () => import('../views/config/FirmwareView.vue'),
        meta: {
          title: 'router.title.firmware',
          parent: 'router.parent.configManagement',
          requiresAuth: true,
          permission: 'system:config:firmware',
        },
      },
      // 个人中心
      {
        path: 'setting/account',
        name: 'setting-account',
        component: () => import('../views/setting/AccountView.vue'),
        meta: {
          title: 'router.title.account',
          parent: 'router.parent.settings',
          requiresAuth: true,
          permission: 'system:setting',
        },
      },
      // 个人设置（暂时禁用）
      // {
      //   path: 'setting/config',
      //   name: 'setting-config',
      //   component: () => import('../views/setting/ConfigView.vue'),
      //   meta: {
      //     title: 'router.title.personalConfig',
      //     parent: 'router.parent.settings',
      //     requiresAuth: true,
      //     permission: 'system:setting',
      //   },
      // },
    ],
  },

  // 用户端路由
  {
    path: '/u',
    component: UserLayout,
    redirect: '/u/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'user-dashboard',
        component: () => import('../views/user/UserDashboard.vue'),
        meta: {
          title: '首页',
          requiresAuth: true,
          isUserRoute: true,
        },
      },
      {
        path: 'devices',
        name: 'user-devices',
        component: () => import('../views/user/UserDevices.vue'),
        meta: {
          title: '我的设备',
          requiresAuth: true,
          isUserRoute: true,
        },
      },
      {
        path: 'chat/:deviceId',
        name: 'user-chat',
        component: () => import('../views/user/UserChat.vue'),
        meta: {
          title: '对话历史',
          requiresAuth: true,
          isUserRoute: true,
        },
      },
      {
        path: 'settings',
        name: 'user-settings',
        component: () => import('../views/user/UserSettings.vue'),
        meta: {
          title: '个人设置',
          requiresAuth: true,
          isUserRoute: true,
        },
      },
    ],
  },

  // 异常页面
  {
    path: '/403',
    name: '403',
    component: () => import('../views/exception/403.vue'),
    meta: {
      title: 'router.title.error403',
      requiresAuth: false,
    },
  },
  {
    path: '/404',
    name: '404',
    component: () => import('../views/exception/404.vue'),
    meta: {
      title: 'router.title.error404',
      requiresAuth: false,
    },
  },

  // 捕获所有未匹配的路由
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

export default router

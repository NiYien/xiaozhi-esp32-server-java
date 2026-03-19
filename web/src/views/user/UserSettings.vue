<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { message } from 'ant-design-vue'
import {
  UserOutlined,
  CameraOutlined,
  LockOutlined,
  MailOutlined,
  PhoneOutlined,
} from '@ant-design/icons-vue'
import type { FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import type { UploadProps } from 'ant-design-vue'
import { useUserStore } from '@/store/user'
import { useAvatar } from '@/composables/useAvatar'
import { updateUser } from '@/services/user'
import { uploadFile } from '@/services/upload'
import type { User, UpdateUserParams } from '@/types/user'

const userStore = useUserStore()
const { getAvatarUrl } = useAvatar()

const userInfo = computed(() => userStore.userInfo || {})
const avatarUrl = computed(() => getAvatarUrl(userInfo.value.avatar))

// 头像上传状态
const avatarLoading = ref(false)

// 个人信息表单
const profileFormRef = ref<FormInstance>()
const profileForm = reactive({
  name: userInfo.value.name || '',
  tel: userInfo.value.tel || '',
  email: userInfo.value.email || '',
})

// 密码修改表单
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({
  oldPassword: '',
  password: '',
  confirmPassword: '',
})

// 密码强度
const passwordLevel = ref(0)
const passwordLevelVisible = ref(false)

const levelNames = ['弱', '弱', '中', '强']
const levelColors = ['#ff0000', '#ff0000', '#ff7e05', '#52c41a']
const passwordLevelName = computed(() => levelNames[passwordLevel.value])
const passwordLevelColor = computed(() => levelColors[passwordLevel.value])
const passwordPercent = computed(() => {
  if (passwordLevel.value === 0) return 10
  if (passwordLevel.value === 3) return 100
  return passwordLevel.value * 30
})

// 个人信息表单验证规则
const profileRules: Record<string, Rule[]> = {
  name: [],
  tel: [
    {
      pattern: /^1[3456789]\d{9}$/,
      message: '请输入正确的手机号',
      trigger: ['blur', 'change'],
    },
  ],
  email: [
    {
      type: 'email',
      message: '请输入正确的邮箱地址',
      trigger: ['blur', 'change'],
    },
  ],
}

// 密码表单验证规则
const passwordRules: Record<string, Rule[]> = {
  oldPassword: [
    { required: true, message: '请输入旧密码', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value) => {
        if (!value) {
          passwordLevel.value = 0
          return Promise.resolve()
        }

        let level = 0
        if (/[0-9]/.test(value)) level++
        if (/[a-zA-Z]/.test(value)) level++
        if (/[^0-9a-zA-Z_]/.test(value)) level++
        if (value.length < 6) level = 0

        passwordLevel.value = level

        if (level >= 2) {
          return Promise.resolve()
        }
        return Promise.reject('密码强度不够，需包含数字和字母，至少6位')
      },
      trigger: ['blur', 'change'],
    },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_rule, value) => {
        if (value && passwordForm.password && value !== passwordForm.password) {
          return Promise.reject('两次输入的密码不一致')
        }
        return Promise.resolve()
      },
      trigger: ['blur', 'change'],
    },
  ],
}

// 提交个人信息
const profileSubmitLoading = ref(false)
async function handleProfileSubmit() {
  try {
    await profileFormRef.value?.validate()

    profileSubmitLoading.value = true

    const updateData: UpdateUserParams = {
      userId: userInfo.value.userId,
      username: userInfo.value.username,
      name: profileForm.name,
      tel: profileForm.tel,
      email: profileForm.email,
    }

    const res = await updateUser(updateData)

    if (res.code === 200) {
      const userData = res.data as Partial<User>
      userStore.updateUserInfo({
        ...userData,
        state: userData.state?.toString(),
        isAdmin: userData.isAdmin?.toString(),
      })
      message.success('个人信息更新成功')
    } else {
      message.error(res.message || '更新失败')
    }
  } catch (error) {
    console.error('表单验证失败:', error)
  } finally {
    profileSubmitLoading.value = false
  }
}

// 提交密码修改
const passwordSubmitLoading = ref(false)
async function handlePasswordSubmit() {
  try {
    await passwordFormRef.value?.validate()

    passwordSubmitLoading.value = true

    const updateData: UpdateUserParams = {
      userId: userInfo.value.userId,
      username: userInfo.value.username,
      oldPassword: passwordForm.oldPassword,
      password: passwordForm.password,
    }

    const res = await updateUser(updateData)

    if (res.code === 200) {
      message.success('密码修改成功')
      passwordForm.oldPassword = ''
      passwordForm.password = ''
      passwordForm.confirmPassword = ''
      passwordLevel.value = 0
    } else {
      message.error(res.message || '密码修改失败')
    }
  } catch (error) {
    console.error('表单验证失败:', error)
  } finally {
    passwordSubmitLoading.value = false
  }
}

// 头像上传
const beforeAvatarUpload: UploadProps['beforeUpload'] = (file) => {
  const isImage = file.type.startsWith('image/')
  const isLt2M = file.size / 1024 / 1024 < 2

  if (!isImage) {
    message.error('只能上传图片文件')
    return false
  }
  if (!isLt2M) {
    message.error('图片大小不能超过 2MB')
    return false
  }

  avatarLoading.value = true
  uploadFile(file, 'avatar')
    .then((url) => {
      updateUserAvatar(url as string)
    })
    .catch((error) => {
      message.error('头像上传失败: ' + error)
      avatarLoading.value = false
    })

  return false
}

// 更新头像
async function updateUserAvatar(avatarFullUrl: string) {
  try {
    const relativePath = getRelativePath(avatarFullUrl)

    const updateData: UpdateUserParams = {
      userId: userInfo.value.userId,
      username: userInfo.value.username,
      avatar: relativePath,
    } as UpdateUserParams

    const res = await updateUser(updateData)

    if (res.code === 200) {
      userStore.updateUserInfo({
        ...userInfo.value,
        avatar: relativePath,
      })
      message.success('头像更新成功')
    } else {
      message.error(res.message || '头像更新失败')
    }
  } catch (error) {
    message.error('头像更新失败: ' + error)
  } finally {
    avatarLoading.value = false
  }
}

// 将完整 URL 转换为相对路径
function getRelativePath(fullUrl: string): string {
  if (!fullUrl) return ''
  if (!fullUrl.startsWith('http://') && !fullUrl.startsWith('https://')) {
    return fullUrl
  }
  try {
    const url = new URL(fullUrl)
    return url.pathname.startsWith('/') ? url.pathname.substring(1) : url.pathname
  } catch {
    const parts = fullUrl.split('/')
    const uploadIndex = parts.findIndex((part) => part === 'uploads')
    if (uploadIndex !== -1) {
      return parts.slice(uploadIndex).join('/')
    }
    return fullUrl
  }
}

// 密码输入框聚焦/失焦
function handlePasswordFocus() {
  passwordLevelVisible.value = true
}

function handlePasswordBlur() {
  setTimeout(() => {
    passwordLevelVisible.value = false
  }, 200)
}
</script>

<template>
  <div class="user-settings">
    <h2 class="page-title">个人设置</h2>

    <a-row :gutter="[24, 24]">
      <!-- 头像区域 -->
      <a-col :xs="24" :md="24" :lg="8">
        <a-card :bordered="false" class="settings-card">
          <div class="avatar-section">
            <a-upload
              name="file"
              :show-upload-list="false"
              :before-upload="beforeAvatarUpload"
              accept=".jpg,.jpeg,.png,.gif"
              class="avatar-uploader"
            >
              <div class="avatar-preview">
                <a-avatar :src="avatarUrl" :size="120">
                  <template #icon><UserOutlined style="font-size: 48px" /></template>
                </a-avatar>
                <div class="avatar-mask">
                  <a-spin v-if="avatarLoading" />
                  <CameraOutlined v-else style="font-size: 28px" />
                </div>
              </div>
            </a-upload>
            <div class="avatar-name">{{ userInfo.name || userInfo.username }}</div>
            <div class="avatar-tips">
              <p>点击头像更换</p>
              <p class="tip-sub">支持 JPG、PNG、GIF，不超过 2MB</p>
            </div>
          </div>
        </a-card>
      </a-col>

      <!-- 信息编辑区域 -->
      <a-col :xs="24" :md="24" :lg="16">
        <!-- 个人信息 -->
        <a-card title="基本信息" :bordered="false" class="settings-card">
          <a-form
            ref="profileFormRef"
            :model="profileForm"
            :rules="profileRules"
            layout="vertical"
            @finish="handleProfileSubmit"
          >
            <a-form-item label="昵称" name="name">
              <a-input
                v-model:value="profileForm.name"
                placeholder="请输入昵称"
              >
                <template #prefix><UserOutlined /></template>
              </a-input>
            </a-form-item>

            <a-form-item label="手机号" name="tel">
              <a-input
                v-model:value="profileForm.tel"
                placeholder="请输入手机号"
              >
                <template #prefix><PhoneOutlined /></template>
              </a-input>
            </a-form-item>

            <a-form-item label="邮箱" name="email">
              <a-input
                v-model:value="profileForm.email"
                placeholder="请输入邮箱"
                allow-clear
              >
                <template #prefix><MailOutlined /></template>
              </a-input>
            </a-form-item>

            <a-form-item>
              <a-button type="primary" html-type="submit" :loading="profileSubmitLoading">
                保存信息
              </a-button>
            </a-form-item>
          </a-form>
        </a-card>

        <!-- 密码修改 -->
        <a-card title="修改密码" :bordered="false" class="settings-card" style="margin-top: 16px">
          <a-form
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordRules"
            layout="vertical"
            @finish="handlePasswordSubmit"
          >
            <a-form-item label="旧密码" name="oldPassword">
              <a-input-password
                v-model:value="passwordForm.oldPassword"
                placeholder="请输入旧密码"
                allow-clear
              >
                <template #prefix><LockOutlined /></template>
              </a-input-password>
            </a-form-item>

            <a-form-item label="新密码" name="password">
              <a-popover
                v-model:open="passwordLevelVisible"
                placement="right"
                trigger="focus"
              >
                <template #content>
                  <div style="width: 220px">
                    <div :style="{ color: passwordLevelColor, marginBottom: '8px' }">
                      密码强度：<strong>{{ passwordLevelName }}</strong>
                    </div>
                    <a-progress
                      :percent="passwordPercent"
                      :show-info="false"
                      :stroke-color="passwordLevelColor"
                    />
                    <div style="margin-top: 8px; font-size: 12px; color: var(--ant-color-text-secondary)">
                      至少6位，包含数字和字母
                    </div>
                  </div>
                </template>
                <a-input-password
                  v-model:value="passwordForm.password"
                  placeholder="请输入新密码"
                  allow-clear
                  @focus="handlePasswordFocus"
                  @blur="handlePasswordBlur"
                >
                  <template #prefix><LockOutlined /></template>
                </a-input-password>
              </a-popover>
            </a-form-item>

            <a-form-item label="确认密码" name="confirmPassword">
              <a-input-password
                v-model:value="passwordForm.confirmPassword"
                placeholder="请再次输入新密码"
                allow-clear
              >
                <template #prefix><LockOutlined /></template>
              </a-input-password>
            </a-form-item>

            <a-form-item>
              <a-button type="primary" html-type="submit" :loading="passwordSubmitLoading">
                修改密码
              </a-button>
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<style scoped lang="scss">
.user-settings {
  padding-bottom: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 20px;
  color: var(--ant-color-text);
}

.settings-card {
  border-radius: 8px;
}

// 头像区域
.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 0;

  .avatar-uploader {
    cursor: pointer;
  }

  .avatar-preview {
    position: relative;
    transition: all 0.3s;

    &:hover {
      transform: scale(1.05);

      .avatar-mask {
        opacity: 1;
      }
    }

    .avatar-mask {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(0, 0, 0, 0.4);
      border-radius: 50%;
      opacity: 0;
      transition: opacity 0.3s;
      color: #fff;
    }
  }

  .avatar-name {
    margin-top: 16px;
    font-size: 18px;
    font-weight: 600;
    color: var(--ant-color-text);
  }

  .avatar-tips {
    margin-top: 8px;
    text-align: center;

    p {
      margin: 2px 0;
      color: var(--ant-color-text-secondary);
      font-size: 13px;
    }

    .tip-sub {
      color: var(--ant-color-text-tertiary);
      font-size: 12px;
    }
  }
}

// 移动端适配
@media (max-width: 768px) {
  .page-title {
    font-size: 18px;
  }

  .avatar-section {
    padding: 16px 0;
  }
}
</style>

<template>
  <div class="personal-page">
    <a-card :bordered="false" class="personal-card tq-glass">
      <template #title>
        <a-space>
          <UserOutlined />
          <span>个人信息</span>
        </a-space>
      </template>

      <!-- 头像 -->
      <div class="avatar-row">
        <div class="avatar-box" @click="pickImage">
          <a-avatar :size="96" :src="auth.userInfo?.avatar" class="avatar">
            {{ (auth.userInfo?.nickname || 'U').charAt(0) }}
          </a-avatar>
          <div class="avatar-edit"><CameraOutlined /> 更换</div>
        </div>
        <input ref="fileInput" type="file" accept="image/*" hidden @change="onFile" />
        <div class="avatar-tip">支持 JPG/PNG，上传前可裁剪</div>
      </div>

      <a-form layout="vertical" class="info-form">
        <a-form-item label="账号">
          <a-input :value="auth.userInfo?.username" disabled />
        </a-form-item>
        <a-form-item label="昵称">
          <a-input v-model:value="nickname" placeholder="请输入昵称" />
        </a-form-item>
        <a-button type="primary" :loading="savingNick" @click="saveNickname">保存昵称</a-button>
      </a-form>

      <a-divider />

      <a-form layout="vertical" class="info-form">
        <a-form-item label="原密码">
          <a-input-password v-model:value="oldPwd" placeholder="请输入原密码" />
        </a-form-item>
        <a-form-item label="新密码">
          <a-input-password v-model:value="newPwd" placeholder="请输入新密码（至少 6 位）" />
        </a-form-item>
        <a-form-item label="确认新密码">
          <a-input-password v-model:value="confirmPwd" placeholder="再次输入新密码" />
        </a-form-item>
        <a-button type="primary" danger :loading="savingPwd" @click="savePassword">修改密码</a-button>
      </a-form>
    </a-card>

    <AvatarCropperModal v-model:open="cropperOpen" :image-url="cropperSrc" @crop="uploadAvatar" />
  </div>
</template>

<script setup lang="ts">
/**
 * 个人信息页：整合头像上传（裁剪）、昵称、密码修改。
 */
import { UserOutlined, CameraOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getAuthHeaders } from '@/utils/request'
import { useAuthStore } from '@/stores/auth'
import AvatarCropperModal from '@/components/auth/AvatarCropperModal.vue'

useSeoMeta({ title: '个人信息 - 溯知 · TraceQA' })

const auth = useAuthStore()
const fileInput = ref<HTMLInputElement | null>(null)

const nickname = ref(auth.userInfo?.nickname || '')
const savingNick = ref(false)

const oldPwd = ref('')
const newPwd = ref('')
const confirmPwd = ref('')
const savingPwd = ref(false)

const cropperOpen = ref(false)
const cropperSrc = ref('')

onMounted(async () => {
  await auth.fetchMe()
  nickname.value = auth.userInfo?.nickname || ''
})

function pickImage(): void {
  fileInput.value?.click()
}

function onFile(event: Event): void {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) {
    return
  }
  if (fileInput.value) {
    fileInput.value.value = ''
  }
  cropperSrc.value = URL.createObjectURL(file)
  cropperOpen.value = true
}

async function uploadAvatar(blob: Blob): Promise<void> {
  const fd = new FormData()
  fd.append('file', blob, 'avatar.jpg')
  try {
    const res = await fetch('/api/auth/avatar', { method: 'POST', headers: getAuthHeaders(), body: fd })
    const json = (await res.json()) as { code?: number; data?: string; msg?: string }
    if (json.code === 200 && json.data) {
      await auth.fetchMe()
      message.success('头像已更新')
    } else {
      message.error(json.msg || '头像上传失败')
    }
  } catch {
    message.error('头像上传失败')
  }
}

async function saveNickname(): Promise<void> {
  const value = nickname.value.trim()
  if (!value) {
    message.warning('昵称不能为空')
    return
  }
  savingNick.value = true
  try {
    await auth.updateNickname(value)
    message.success('昵称已更新')
  } catch (err) {
    message.error((err as Error).message || '保存失败')
  } finally {
    savingNick.value = false
  }
}

async function savePassword(): Promise<void> {
  if (!oldPwd.value || !newPwd.value) {
    message.warning('请填写原密码与新密码')
    return
  }
  if (newPwd.value.length < 6) {
    message.warning('新密码至少 6 位')
    return
  }
  if (newPwd.value !== confirmPwd.value) {
    message.warning('两次输入的新密码不一致')
    return
  }
  savingPwd.value = true
  try {
    const res = await fetch('/api/auth/password', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify({ oldPassword: oldPwd.value, newPassword: newPwd.value })
    })
    const json = (await res.json()) as { code?: number; msg?: string }
    if (json.code === 200) {
      message.success('密码已修改')
      oldPwd.value = ''
      newPwd.value = ''
      confirmPwd.value = ''
    } else {
      message.error(json.msg || '修改失败')
    }
  } catch {
    message.error('修改失败')
  } finally {
    savingPwd.value = false
  }
}
</script>

<style scoped>
.personal-page {
  max-width: 560px;
  margin: 24px auto;
  padding: 0 16px;
}
.personal-card {
  border-radius: 14px;
}
.avatar-row {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 20px;
}
.avatar-box {
  position: relative;
  cursor: pointer;
}
.avatar {
  background: linear-gradient(135deg, #1677ff, #06b6d4);
  font-size: 36px;
}
.avatar-edit {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 12px;
  text-align: center;
  padding: 4px 0;
  border-radius: 0 0 50% 50%;
}
.avatar-tip {
  margin-top: 8px;
  color: #86909c;
  font-size: 12px;
}
.info-form {
  max-width: 340px;
}
</style>
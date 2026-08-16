<template>
  <div class="login-page">
    <a-card class="login-card" :bordered="false">
      <div class="login-card__header">
        <div class="login-card__logo">溯</div>
        <h2 class="login-card__title">溯知 · TraceQA</h2>
        <p class="login-card__subtitle">《数据挖掘》课程智能问答平台</p>
      </div>

      <!-- 登录 / 注册 切换 -->
      <a-segmented v-model:value="mode" block :options="modeOptions" class="login-card__seg" />

      <!-- 登录表单 -->
      <a-form v-if="mode === 'login'" layout="vertical" :model="loginForm" @finish="handleLogin">
        <a-form-item name="username" :rules="[{ required: true, message: '请输入用户名' }]">
          <a-input v-model:value="loginForm.username" placeholder="用户名" size="large">
            <template #prefix><UserOutlined /></template>
          </a-input>
        </a-form-item>
        <a-form-item name="password" :rules="[{ required: true, message: '请输入密码' }]">
          <a-input-password v-model:value="loginForm.password" placeholder="密码" size="large">
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" block size="large" :loading="loading">登 录</a-button>
        </a-form-item>
      </a-form>

      <!-- 注册表单 -->
      <a-form v-else layout="vertical" :model="registerForm" @finish="handleRegister">
        <a-form-item
          name="username"
          :rules="[
            { required: true, message: '请输入用户名' },
            { min: 3, max: 32, message: '用户名长度需在 3-32 之间' }
          ]"
        >
          <a-input v-model:value="registerForm.username" placeholder="用户名（3-32 位）" size="large">
            <template #prefix><UserOutlined /></template>
          </a-input>
        </a-form-item>
        <a-form-item
          name="password"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 6, max: 32, message: '密码长度需在 6-32 之间' }
          ]"
        >
          <a-input-password v-model:value="registerForm.password" placeholder="密码（6-32 位）" size="large">
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>
        <a-form-item
          name="confirm"
          :rules="[
            { required: true, message: '请再次输入密码' },
            { validator: validateConfirm }
          ]"
        >
          <a-input-password v-model:value="registerForm.confirm" placeholder="确认密码" size="large">
            <template #prefix><SafetyOutlined /></template>
          </a-input-password>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" block size="large" :loading="loading">注 册</a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 登录 / 注册页。
 *
 * <p>支持账号登录与自助注册。注册成功后自动登录并进入问答页。</p>
 */
import { UserOutlined, LockOutlined, SafetyOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'

useSeoMeta({ title: '登录 - 溯知 · TraceQA' })

const auth = useAuthStore()
const loading = ref(false)
const mode = ref<'login' | 'register'>('login')
const modeOptions = [
  { label: '登录', value: 'login' },
  { label: '注册', value: 'register' }
]

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', confirm: '' })

async function handleLogin(): Promise<void> {
  loading.value = true
  try {
    await auth.login(loginForm.username, loginForm.password)
    message.success('登录成功')
    await goHome()
  } catch (err) {
    message.error((err as Error).message || '登录失败')
  } finally {
    loading.value = false
  }
}

async function handleRegister(): Promise<void> {
  loading.value = true
  try {
    await auth.register(registerForm.username, registerForm.password)
    // 注册成功后自动登录
    await auth.login(registerForm.username, registerForm.password)
    message.success('注册成功，已自动登录')
    await goHome()
  } catch (err) {
    message.error((err as Error).message || '注册失败')
  } finally {
    loading.value = false
  }
}

/** 登录/注册成功后跳转：优先回退到来源页，否则进入问答页 */
async function goHome(): Promise<void> {
  const route = useRoute()
  const redirect = route.query.redirect as string | undefined
  await navigateTo(redirect && redirect.startsWith('/') ? redirect : '/chat')
}

/** 确认密码校验 */
function validateConfirm(_rule: unknown, value: string, callback: (error?: Error) => void): void {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #e8f0fe 0%, #f6f7fb 100%);
}

.login-card {
  width: 380px;
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

.login-card__header {
  text-align: center;
  margin-bottom: 20px;
}

.login-card__logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, #1677ff, #06b6d4);
  color: #fff;
  font-size: 24px;
  font-weight: 700;
}

.login-card__title {
  margin: 12px 0 4px;
  font-size: 20px;
  color: #1f2329;
}

.login-card__subtitle {
  margin: 0;
  color: #86909c;
  font-size: 13px;
}

.login-card__seg {
  margin-bottom: 20px;
}
</style>

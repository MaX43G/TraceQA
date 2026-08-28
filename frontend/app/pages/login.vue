<template>
  <div class="login-page">
    <div class="login-page__blob login-page__blob--1"/>
    <div class="login-page__blob login-page__blob--2"/>
    <a-card class="login-card tq-glass tq-slide-up" :bordered="false">
      <div class="login-card__header">
        <div class="login-card__logo">溯</div>
        <h2 class="login-card__title">溯知 · TraceQA</h2>
        <p class="login-card__subtitle">《数据挖掘》课程智能问答平台</p>
      </div>

      <!-- 登录 / 注册 切换 -->
      <a-segmented v-model:value="mode" block :options="modeOptions" class="login-card__seg"/>

      <!-- 登录表单 -->
      <a-form v-if="mode === 'login'" layout="vertical" :model="loginForm" @finish="handleLogin">
        <a-form-item name="username" :rules="[{ required: true, message: '请输入用户名' }]">
          <a-input v-model:value="loginForm.username" placeholder="用户名" size="large">
            <template #prefix>
              <UserOutlined/>
            </template>
          </a-input>
        </a-form-item>
        <a-form-item name="password" :rules="[{ required: true, message: '请输入密码' }]">
          <a-input-password v-model:value="loginForm.password" placeholder="密码" size="large">
            <template #prefix>
              <LockOutlined/>
            </template>
          </a-input-password>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" block size="large" :loading="loading">登录</a-button>
        </a-form-item>
      </a-form>

      <!-- 注册表单 -->
      <a-form v-else layout="vertical" :model="registerForm" @finish="handleRegister">
        <a-form-item
            name="username"
            :rules="[
            { required: true, message: '请输入账号' },
            { pattern: /^[a-zA-Z0-9]+$/, message: '账号只能由英文字母和数字组成' },
            { min: 3, max: 32, message: '账号长度需在 3-32 之间' }
          ]"
        >
          <a-input v-model:value="registerForm.username" placeholder="账号（仅英文数字，注册后不可修改）" size="large">
            <template #prefix>
              <UserOutlined/>
            </template>
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
            <template #prefix>
              <LockOutlined/>
            </template>
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
            <template #prefix>
              <SafetyOutlined/>
            </template>
          </a-input-password>
        </a-form-item>
        <a-form-item
            name="nickname"
            :rules="[
            { required: true, message: '请输入昵称' },
            { max: 32, message: '昵称长度不能超过 32' }
          ]"
        >
          <a-input v-model:value="registerForm.nickname" placeholder="昵称" size="large">
            <template #prefix>
              <SmileOutlined/>
            </template>
          </a-input>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" block size="large" :loading="loading">注册</a-button>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 登录 / 注册页。
 *
 * <p>支持账号登录与自助注册。注册成功后自动登录并进入问答页。
 * 表单基于 antd Form 原生 rules 校验（简单可靠）。</p>
 */
import {UserOutlined, LockOutlined, SafetyOutlined, SmileOutlined} from '@ant-design/icons-vue'
import {message} from 'ant-design-vue'
import {useRoute} from 'vue-router'
import {useAuthStore} from '@/stores/auth'

useSeoMeta({title: '登录 - 溯知 · TraceQA'})

const auth = useAuthStore()
const route = useRoute()
const loading = ref(false)
const mode = ref<'login' | 'register'>('login')
const modeOptions = [
  {label: '登录', value: 'login'},
  {label: '注册', value: 'register'}
]

const loginForm = reactive({username: '', password: ''})
const registerForm = reactive({username: '', password: '', confirm: '', nickname: ''})

async function handleLogin(): Promise<void> {
  loading.value = true
  try {
    await auth.login(loginForm.username, loginForm.password)
    await message.success('登录成功')
    await goHome()
  } catch (err) {
    await message.error((err as Error).message || '登录失败')
  } finally {
    loading.value = false
  }
}

async function handleRegister(): Promise<void> {
  loading.value = true
  try {
    await auth.register(registerForm.username, registerForm.password, registerForm.confirm, registerForm.nickname)
    // 注册成功后自动登录
    await auth.login(registerForm.username, registerForm.password)
    await message.success('注册成功，已自动登录')
    await goHome()
  } catch (err) {
    await message.error((err as Error).message || '注册失败')
  } finally {
    loading.value = false
  }
}

/** 登录/注册成功后跳转：优先回退到来源页，否则进入问答页 */
async function goHome(): Promise<void> {
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
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #e8f0fe 0%, #f6f7fb 100%);
}

.login-page__blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.4;
}

.login-page__blob--1 {
  width: 340px;
  height: 340px;
  background: #69c0ff;
  top: -80px;
  right: 10%;
}

.login-page__blob--2 {
  width: 300px;
  height: 300px;
  background: #b37feb;
  bottom: -80px;
  left: 8%;
}

.login-card {
  position: relative;
  z-index: 1;
  width: 420px;
  max-width: 92vw;
  border-radius: 16px;
  box-shadow: 0 12px 40px rgba(22, 119, 255, 0.12);
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
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 8px;
  box-shadow: 0 4px 12px rgba(22, 119, 255, 0.25);
}

.login-card__title {
  margin: 0;
  font-size: 20px;
  color: #1f2329;
}

.login-card__subtitle {
  margin: 4px 0 0;
  color: #86909c;
  font-size: 13px;
}

.login-card__seg {
  margin-bottom: 20px;
}
</style>

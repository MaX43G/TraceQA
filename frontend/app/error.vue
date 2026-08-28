<template>
  <div class="err-page">
    <div class="err-blob err-blob--1"/>
    <div class="err-blob err-blob--2"/>
    <div class="err-card tq-glass">
      <div class="err-code" :class="isNotFound ? 'err-code--404' : 'err-code--500'">
        {{ error?.statusCode || 500 }}
      </div>
      <div class="err-title">
        {{ isNotFound ? '页面走丢了' : '服务开小差了' }}
      </div>
      <div class="err-desc">
        {{ isNotFound ? '你访问的页面不存在或已被移除。' : '服务器遇到了一点问题，请稍后重试。' }}
      </div>
      <div v-if="error?.statusMessage" class="err-message">{{ error.statusMessage }}</div>
      <div class="err-actions">
        <a-button type="primary" size="large" @click="goHome">
          <template #icon>
            <HomeOutlined/>
          </template>
          返回首页
        </a-button>
        <a-button size="large" @click="goBack">
          <template #icon>
            <ArrowLeftOutlined/>
          </template>
          返回上一页
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 全局错误页：404 / 500 等 HTTP 错误统一展示（美观提示 + 返回入口）。
 */
import {HomeOutlined, ArrowLeftOutlined} from '@ant-design/icons-vue'

const props = defineProps<{
  error?: { statusCode?: number; statusMessage?: string }
}>()

const isNotFound = computed(() => (props.error?.statusCode ?? 0) === 404)

useSeoMeta({title: `${props.error?.statusCode || 500} - 溯知 · TraceQA`, robots: 'noindex'})

function goHome(): void {
  clearError()
  navigateTo('/')
}

function goBack(): void {
  clearError()
  if (window.history.length > 1) {
    window.history.back()
  } else {
    navigateTo('/')
  }
}
</script>

<style scoped>
.err-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: linear-gradient(180deg, #f0f5ff 0%, #f5f7fb 60%, #fff 100%);
  padding: 24px;
}

.err-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.35;
}

.err-blob--1 {
  width: 360px;
  height: 360px;
  background: #69c0ff;
  top: -80px;
  left: -60px;
}

.err-blob--2 {
  width: 320px;
  height: 320px;
  background: #ffa39e;
  bottom: -100px;
  right: -40px;
}

.err-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 480px;
  border-radius: 16px;
  padding: 48px 40px;
  text-align: center;
  box-shadow: 0 8px 40px rgba(22, 119, 255, 0.12);
}

.err-code {
  font-size: 96px;
  font-weight: 800;
  line-height: 1;
  background: linear-gradient(135deg, #1677ff, #06b6d4);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.err-code--404 {
  background: linear-gradient(135deg, #fa8c16, #f5222d);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.err-title {
  margin-top: 16px;
  font-size: 22px;
  font-weight: 600;
  color: #1f2329;
}

.err-desc {
  margin-top: 8px;
  color: #4e5969;
}

.err-message {
  margin-top: 8px;
  font-size: 12px;
  color: #86909c;
  word-break: break-all;
}

.err-actions {
  margin-top: 28px;
  display: flex;
  justify-content: center;
  gap: 12px;
}
</style>
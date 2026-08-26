<template>
  <div class="chat-input">
    <a-textarea
      :key="resetKey"
      v-model:value="text"
      :auto-size="{ minRows: 2, maxRows: 6 }"
      :placeholder="placeholder"
      :disabled="disabled"
      @keydown="handleKeydown"
    />
    <div class="chat-input__footer">
      <span class="chat-input__tip">{{ generating ? 'AI 正在回答，请稍候…' : 'Enter 发送，Shift + Enter 换行' }}</span>
      <a-button type="primary" :disabled="!text.trim() || disabled || generating" @click="submit">
        <template #icon><SendOutlined /></template>
        发送
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 聊天输入组件：Enter 快捷发送；AI 回答期间禁用发送（不提供中途停止）。
 */
import { SendOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  /** 是否禁止输入（生成中） */
  disabled?: boolean
  /** 是否正在生成（禁用发送） */
  generating?: boolean
}>()

const emit = defineEmits<{
  (e: 'send', content: string): void
}>()

const text = ref('')
/** 每次发送自增，通过 :key 强制重建 textarea，保证提问栏被真正清空 */
const resetKey = ref(0)

const placeholder = computed<string>(() =>
  props.disabled ? 'AI 正在回答，请稍候…' : '请输入你的问题，例如：什么是 K 均值聚类？'
)

/** 回车发送、Shift+回车换行 */
function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey && !props.generating) {
    event.preventDefault()
    submit()
  }
}

function submit(): void {
  const content = text.value.trim()
  if (!content || props.disabled || props.generating) {
    return
  }
  emit('send', content)
  text.value = ''
  resetKey.value++
}
</script>

<style scoped>
.chat-input {
  border: 1px solid #e5e6eb;
  border-radius: 12px;
  background: #fff;
  padding: 12px 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.chat-input__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.chat-input__tip {
  color: #86909c;
  font-size: 12px;
}
</style>

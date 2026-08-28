<template>
  <div class="chat-input">
    <a-textarea
      v-model:value="text"
      :auto-size="{ minRows: 2, maxRows: 6 }"
      :placeholder="placeholder"
      :disabled="disabled"
      @keydown="handleKeydown"
    />
    <div class="chat-input__footer">
      <span class="chat-input__tip">{{ generating ? 'AI 正在回答，请稍候…' : 'Enter 发送，Shift + Enter 换行' }}</span>
      <a-space>
        <a-tooltip :title="listening ? '正在聆听，点击停止' : '语音输入'">
          <a-button
            :class="{ 'is-listening': listening }"
            :disabled="disabled || generating"
            shape="circle"
            @click="toggleVoice"
          >
            <template #icon><AudioOutlined /></template>
          </a-button>
        </a-tooltip>
        <a-button type="primary" :disabled="!text.trim() || disabled || generating" @click="submit">
          <template #icon><SendOutlined /></template>
          发送
        </a-button>
      </a-space>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 聊天输入组件：Enter 快捷发送 + 语音输入。
 * 语音输入采用浏览器原生 Web Speech API（SpeechRecognition），前端实时识别并填入输入框，
 * 完全免费、无需后端参与；Chrome/Edge 支持，其它浏览器自动隐藏。
 */
import { SendOutlined, AudioOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'

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
const resetKey = ref(0)
const listening = ref(false)

// 浏览器原生语音识别（免费、前端实时；Chrome/Edge 支持）
const SpeechRecognition =
  (typeof window !== 'undefined' && (window as any).SpeechRecognition) ||
  (typeof window !== 'undefined' && (window as any).webkitSpeechRecognition) ||
  null
const supported = Boolean(SpeechRecognition)

/** 是否希望持续聆听（用户未手动停止） */
let keepListening = false
/** 已识别的最终文本（跨自动重启保留） */
let finalText = ''
/** 当前识别实例 */
let recognition: any = null

/**
 * 创建并启动一个新的识别实例（continuous + interim，实时识别）。
 * onend 自动重启以持续聆听，直到用户手动停止。
 */
function startRecognition(): void {
  if (!SpeechRecognition) {
    return
  }
  try {
    const rec = new SpeechRecognition()
    rec.lang = 'zh-CN'
    rec.continuous = true
    rec.interimResults = true
    rec.maxAlternatives = 1

    rec.onstart = () => {
      listening.value = true
    }
    // 实时识别：最终片段累积，临时片段实时追加到输入框
    rec.onresult = (e: any) => {
      let interim = ''
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const item = e.results[i]
        if (item.isFinal) {
          finalText += item[0].transcript
        } else {
          interim += item[0].transcript
        }
      }
      text.value = finalText + interim
    }
    rec.onerror = () => {
      keepListening = false
      listening.value = false
    }
    rec.onend = () => {
      listening.value = false
      // 用户未手动停止则自动重启，保证持续聆听与实时识别
      if (keepListening) {
        startRecognition()
      }
    }

    recognition = rec
    rec.start()
  } catch {
    keepListening = false
    listening.value = false
  }
}

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

/** 语音输入开关（浏览器不支持时提示） */
function toggleVoice(): void {
  if (!supported) {
    message.warning('当前浏览器不支持实时语音识别，请使用 Chrome/Edge')
    return
  }
  if (listening.value || keepListening) {
    // 停止：置位标志并停止当前实例
    keepListening = false
    listening.value = false
    try {
      recognition?.stop()
    } catch {
      // 已自然结束则忽略
    }
  } else {
    // 开始：清空旧的已识别文本并启动
    finalText = ''
    keepListening = true
    startRecognition()
  }
}

function submit(): void {
  const content = text.value.trim()
  if (!content || props.disabled || props.generating) {
    return
  }
  text.value = ''
  resetKey.value++
  emit('send', content)
}

function clear(): void {
  text.value = ''
  nextTick(() => {
    resetKey.value++
  })
}

defineExpose({ clear })
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

.chat-input__footer :deep(.is-listening) {
  background: #ff4d4f;
  color: #fff;
  border-color: #ff4d4f;
  animation: tqPulse 1s ease-in-out infinite;
}

@keyframes tqPulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}
</style>

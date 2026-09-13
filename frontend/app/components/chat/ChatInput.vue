<template>
  <div class="chat-input">
    <!-- 检索模式选择器 -->
    <div v-if="showStrategySelector" class="chat-input__strategy">
      <a-space :size="4" wrap>
        <span class="chat-input__strategy-label">模式：</span>
        <a-tooltip title="自动模式：Agent 根据问题复杂度自主选择检索策略">
          <a-tag
              :color="isAutoMode ? 'blue' : undefined"
              style="cursor: pointer"
              @click="setAutoMode"
          >
            自动
          </a-tag>
        </a-tooltip>
        <a-tooltip title="手动模式：自由组合检索策略，精确控制检索行为">
          <a-tag
              :color="isManualMode ? 'green' : undefined"
              style="cursor: pointer"
              @click="setManualMode"
          >
            手动
          </a-tag>
        </a-tooltip>
      </a-space>

      <!-- 手动模式子选项 -->
      <a-space v-if="isManualMode" :size="4" wrap style="margin-top: 6px">
        <span class="chat-input__strategy-label">检索方式：</span>
        <a-tooltip title="假设性文档：先让 AI 生成假设性回答，再用该回答检索，提升语义匹配">
          <a-tag
              :color="selectedStrategies.includes('hyde') ? 'blue' : undefined"
              style="cursor: pointer"
              @click="toggleStrategy('hyde')"
          >
            HyDE
          </a-tag>
        </a-tooltip>
        <a-tooltip title="向量检索：基于文本语义相似度检索">
          <a-tag
              :color="selectedStrategies.includes('vector') ? 'blue' : undefined"
              style="cursor: pointer"
              @click="toggleStrategy('vector')"
          >
            向量
          </a-tag>
        </a-tooltip>
        <a-tooltip title="关键词检索：TF-IDF 关键词 + ES BM25 全文检索">
          <a-tag
              :color="selectedStrategies.includes('keyword') ? 'blue' : undefined"
              style="cursor: pointer"
              @click="toggleStrategy('keyword')"
          >
            关键词
          </a-tag>
        </a-tooltip>
        <a-tooltip :title="graphModeTooltip">
          <a-tag
              :color="selectedStrategies.includes('graph') ? 'blue' : undefined"
              style="cursor: pointer"
              @click="cycleGraphMode"
          >
            图谱{{ graphModeLabel }}
          </a-tag>
        </a-tooltip>
      </a-space>
    </div>
    <a-textarea
        v-model:value="text"
        :auto-size="{ minRows: 2, maxRows: 6 }"
        :placeholder="placeholder"
        :disabled="disabled"
        @keydown="handleKeydown"
    />
    <div class="chat-input__footer">
      <a-space>
        <a-tooltip title="切换手动/自动检索模式">
          <a-button
              type="text"
              size="small"
              :disabled="generating"
              @click="showStrategySelector = !showStrategySelector"
          >
            <template #icon>
              <SettingOutlined/>
            </template>
          </a-button>
        </a-tooltip>
        <span class="chat-input__tip">{{ generating ? 'AI 正在回答，请稍候…' : 'Enter 发送，Shift + Enter 换行' }}</span>
      </a-space>
      <a-space>
        <a-tooltip :title="listening ? '正在聆听，点击停止' : '语音输入'">
          <a-button
              :class="{ 'is-listening': listening }"
              :disabled="disabled || generating"
              shape="circle"
              @click="toggleVoice"
          >
            <template #icon>
              <AudioOutlined/>
            </template>
          </a-button>
        </a-tooltip>
        <a-button type="primary" :disabled="!text.trim() || disabled || generating" @click="submit">
          <template #icon>
            <SendOutlined/>
          </template>
          发送
        </a-button>
      </a-space>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 聊天输入组件：Enter 快捷发送 + 语音输入 + 手动检索策略选择。
 *
 * <p>语音输入采用浏览器原生 Web Speech API（SpeechRecognition），前端实时识别并填入输入框，
 * 完全免费、无需后端参与；Chrome/Edge 支持，其它浏览器自动隐藏。</p>
 *
 * <p>手动检索策略：用户可点击设置按钮展开策略选择器，自由组合 HyDE、向量、关键词、图谱四种检索方式。</p>
 */
import {SendOutlined, AudioOutlined, SettingOutlined} from '@ant-design/icons-vue'
import {message} from 'ant-design-vue'

const props = defineProps<{
  /** 是否禁止输入（生成中） */
  disabled?: boolean
  /** 是否正在生成（禁用发送） */
  generating?: boolean
}>()

const emit = defineEmits<{
  (e: 'send', content: string): void
  (e: 'send-manual', content: string, strategies: string[], graphMode: string): void
  (e: 'mode-change', mode: 'auto' | 'manual', strategies: string[], graphMode: string): void
}>()

const text = ref('')
const resetKey = ref(0)
const listening = ref(false)

/** 手动检索策略选择器是否展开 */
const showStrategySelector = ref(false)
/** 当前选中的检索策略列表 */
const selectedStrategies = ref<string[]>([])
/** 当前模式：auto / manual */
const currentMode = ref<'auto' | 'manual'>('auto')
/** 图谱子模式：both / local / global */
const graphMode = ref<'both' | 'local' | 'global'>('both')

/** 是否为自动模式 */
const isAutoMode = computed(() => currentMode.value === 'auto')
/** 是否为手动模式 */
const isManualMode = computed(() => currentMode.value === 'manual')


/** 图谱子模式标签 */
const graphModeLabel = computed(() => {
  if (!selectedStrategies.value.includes('graph')) return ''
  return graphMode.value === 'both' ? '' : `(${graphMode.value})`
})
/** 图谱子模式提示 */
const graphModeTooltip = computed(() => {
  const modes = ['知识图谱实体关系检索', '当前：', graphMode.value === 'both' ? 'local + global 双路并行' : graphMode.value === 'local' ? '仅实体关联(local)' : '仅主题聚合(global)', '，点击切换']
  return modes.join('')
})

/** 切换自动模式 */
function setAutoMode(): void {
  currentMode.value = 'auto'
  selectedStrategies.value = []
  emitModeChange()
}

/** 切换手动模式 */
function setManualMode(): void {
  currentMode.value = 'manual'
  emitModeChange()
}



/** 循环图谱子模式：both → local → global → both */
function cycleGraphMode(): void {
  if (!selectedStrategies.value.includes('graph')) {
    // 首次点击：选中图谱并设为 both
    selectedStrategies.value.push('graph')
    graphMode.value = 'both'
  } else {
    // 已选中：循环子模式
    if (graphMode.value === 'both') {
      graphMode.value = 'local'
    } else if (graphMode.value === 'local') {
      graphMode.value = 'global'
    } else {
      graphMode.value = 'both'
    }
  }
  emitModeChange()
}

/** 切换单个策略 */
function toggleStrategy(strategy: string): void {
  const idx = selectedStrategies.value.indexOf(strategy)
  if (idx >= 0) {
    selectedStrategies.value.splice(idx, 1)
  } else {
    selectedStrategies.value.push(strategy)
  }
  currentMode.value = 'manual'
  emitModeChange()
}

/** 通知父组件当前模式 */
function emitModeChange(): void {
  emit('mode-change', currentMode.value, [...selectedStrategies.value], graphMode.value)
}

/** 获取检索策略字符串（用于传给后端） */
function getRetrievalStrategy(): string {
  if (isAutoMode.value) {
    return 'auto'
  }
  return selectedStrategies.value.join(',')
}

// 浏览器原生语音识别
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
  if (isAutoMode.value) {
    emit('send', content)
  } else {
    emit('send-manual', content, [...selectedStrategies.value], graphMode.value)
  }
}

function clear(): void {
  text.value = ''
  nextTick(() => {
    resetKey.value++
  })
}

defineExpose({clear})
</script>

<style scoped>
.chat-input {
  border: 1px solid #e5e6eb;
  border-radius: 12px;
  background: #fff;
  padding: 12px 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.chat-input__strategy {
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.chat-input__strategy-label {
  color: #86909c;
  font-size: 12px;
  line-height: 24px;
}

.chat-input__strategy :deep(.ant-tag) {
  font-size: 12px;
  border-radius: 4px;
  user-select: none;
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

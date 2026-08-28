<template>
  <div class="chat-msg" :class="isUser ? 'chat-msg--user' : 'chat-msg--assistant'">
    <div class="chat-msg__avatar">
      <a-avatar :style="avatarStyle">{{ avatarText }}</a-avatar>
    </div>
    <div class="chat-msg__body">
      <template v-if="isUser">
        <div class="chat-msg__user-bubble">{{ msg.content }}</div>
        <div class="chat-msg__actions">
          <a-button type="text" size="small" danger @click="emit('delete', <number>msg.id)">
            <template #icon>
              <DeleteOutlined/>
            </template>
            删除
          </a-button>
        </div>
      </template>
      <template v-else>
        <ThinkingTracePanel v-if="hasThinking" :nodes="msg.thinkingTrace ?? []"/>
        <RetrievalStatsPanel v-if="msg.stats" :stats="msg.stats"/>
        <div class="chat-msg__ai-bubble">
          <MarkdownViewer :content="displayContent" :typing="props.streaming" :available-indexes="usedIndexes"
                          @cite-click="handleCite"/>
          <div v-if="props.streaming" class="chat-msg__streaming">正在生成…</div>
        </div>
        <CitationPanel v-if="hasReferences" ref="citationPanel" :references="msg.references ?? []"
                       :used-indexes="usedIndexes" @view="openViewer"/>
        <div v-if="props.msg.followup?.length && !props.streaming" class="chat-msg__followup">
          <span class="chat-msg__followup-label">猜你想问</span>
          <a-tag v-for="(q, i) in props.msg.followup" :key="i" class="chat-msg__followup-tag" @click="emit('ask', q)">
            {{ q }}
          </a-tag>
        </div>
        <div class="chat-msg__actions">
          <a-space :size="4">
            <a-tooltip :title="speaking ? '停止朗读' : '朗读本条回答'">
              <a-button type="text" size="small" :class="{ 'speak-btn--active': speaking }" @click="toggleSpeak">
                <template #icon>
                  <SoundOutlined :spin="speaking"/>
                </template>
                {{ speaking ? '停止' : '朗读' }}
              </a-button>
            </a-tooltip>
            <a-button type="text" size="small" @click="copyContent">
              <template #icon>
                <CopyOutlined/>
              </template>
              复制
            </a-button>
            <a-button type="text" size="small" danger @click="emit('delete', <number>msg.id)">
              <template #icon>
                <DeleteOutlined/>
              </template>
              删除
            </a-button>
          </a-space>
        </div>
      </template>
    </div>
  </div>

  <!-- 文献全文查看弹窗 -->
  <a-modal v-model:open="viewerOpen"
           :title="viewerRef ? `文献${viewerRef.index}：${viewerRef.title || viewerRef.filePath}` : '文献全文'"
           :footer="null" width="72vw" :body-style="{ padding: '14px 20px 20px' }">
    <div v-if="viewerRef" class="viewer-body">
      <a-tag color="blue">{{ viewerRef.filePath }}</a-tag>
      <div v-if="viewerRef.headings?.length" class="viewer-headings">
        <a-tag v-for="h in viewerRef.headings" :key="h" color="cyan">{{ h }}</a-tag>
      </div>
      <div class="viewer-markdown markdown-body" v-html="viewerMarkdown"/>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
/**
 * 单条聊天消息组件。
 *
 * <p>用户消息右侧气泡；AI 消息包含「思考折叠面板 + Markdown 打字机 +
 * 引用溯源角标 + 复制/删除操作」。</p>
 */
import {CopyOutlined, DeleteOutlined, SoundOutlined} from '@ant-design/icons-vue'
import {message} from 'ant-design-vue'
import MarkdownViewer from './MarkdownViewer.vue'
import ThinkingTracePanel from './ThinkingTracePanel.vue'
import CitationPanel from './CitationPanel.vue'
import RetrievalStatsPanel from './RetrievalStatsPanel.vue'
import {renderMarkdown} from '@/utils/markdown'
import type {ChatMessageVO, ReferenceVO} from '@/utils/api-types'
import type {RetrievalStats} from '@/composables/useChatStream'

const props = defineProps<{
  /** 消息对象（含流式临时消息） */
  msg: ChatMessageVO & {
    streaming?: boolean
    buffer?: string
    stats?: RetrievalStats
    followup?: string[]
  }
  /** 是否处于生成中 */
  streaming?: boolean
}>()

const emit = defineEmits<{
  (e: 'delete', messageId: number): void
  (e: 'ask', question: string): void
}>()

const citationPanel = ref<InstanceType<typeof CitationPanel> | null>(null)

/** 是否用户消息 */
const isUser = computed<boolean>(() => props.msg.role === 'USER')

/** 头像文本 */
const avatarText = computed<string>(() => (isUser.value ? '我' : '溯'))

/** 头像样式 */
const avatarStyle = computed<Record<string, string>>(() =>
    isUser.value ? {backgroundColor: '#95de64'} : {backgroundColor: '#1677ff'}
)

/** 是否存在思考链路 */
const hasThinking = computed<boolean>(() => (props.msg.thinkingTrace?.length ?? 0) > 0)

/** 是否存在引用来源 */
const hasReferences = computed<boolean>(() => (props.msg.references?.length ?? 0) > 0)

/** 回答中实际引用的序号（[citation:N] ∩ references），用于过滤无内容角标与未引用文献 */
const usedIndexes = computed<Set<number>>(() => {
  const used = new Set<number>()
  const re = /\[citation:(\d+)]/g
  const content = displayContent.value || ''
  let match: RegExpExecArray | null
  while ((match = re.exec(content)) !== null) {
    used.add(Number(match[1]))
  }
  const valid = new Set((props.msg.references ?? []).map((r) => r.index).filter((n): n is number => n != null))
  return new Set([...used].filter((n) => valid.has(n)))
})

/** 展示内容：流式阶段取缓冲，否则取正文 */
const displayContent = computed<string>(() =>
    props.streaming ? props.msg.buffer ?? '' : props.msg.content ?? ''
)

/** 文献全文查看弹窗状态 */
const viewerOpen = ref(false)
const viewerRef = ref<ReferenceVO | null>(null)

/** 文献全文按 Markdown + 公式渲染 */
const viewerMarkdown = computed<string>(() => renderMarkdown(viewerRef.value?.content || ''))

/** 打开文献全文 */
function openViewer(ref: ReferenceVO): void {
  viewerRef.value = ref
  viewerOpen.value = true
}

/** 点击角标：高亮对应引用并打开全文 */
function handleCite(index: number): void {
  citationPanel.value?.highlight(index)
  const ref = (props.msg.references ?? []).find((r) => r.index === index)
  if (ref) {
    openViewer(ref)
  }
}

/** 复制消息内容（纯文本） */
async function copyContent(): Promise<void> {
  const text = displayContent.value
  if (!text) {
    return
  }
  try {
    // navigator.clipboard 仅在安全上下文（HTTPS/localhost）可用；HTTP 环境用 execCommand 兜底
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      copyWithFallback(text)
    }
    await message.success('已复制')
  } catch {
    await message.error('复制失败')
  }
}

/** 非安全上下文（HTTP）复制兜底：隐藏 textarea + execCommand */
function copyWithFallback(text: string): void {
  const ta = document.createElement('textarea')
  ta.value = text
  ta.setAttribute('readonly', '')
  ta.style.position = 'fixed'
  ta.style.top = '-9999px'
  ta.style.opacity = '0'
  document.body.appendChild(ta)
  ta.focus()
  ta.select()
  // setSelectionRange 仅在选中后有效；execCommand 已弃用，仅作 HTTP 兜底
  try {
    ;(ta as HTMLTextAreaElement).setSelectionRange(0, text.length)
    ;(document as unknown as { execCommand: (commandId: string) => boolean }).execCommand('copy')
  } finally {
    document.body.removeChild(ta)
  }
}

/** 是否正在朗读本条回答 */
const speaking = ref(false)

/** 朗读 / 停止本条回答（浏览器 Web Speech API，客户端合成，免费无 Key） */
function toggleSpeak(): void {
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) {
    message.warning('当前浏览器不支持语音朗读')
    return
  }
  if (speaking.value) {
    window.speechSynthesis.cancel()
    speaking.value = false
    return
  }
  const content = displayContent.value
  if (!content) {
    return
  }
  const u = new SpeechSynthesisUtterance(content)
  u.lang = 'zh-CN'
  u.rate = 1
  u.pitch = 1
  u.onend = () => {
    speaking.value = false
  }
  u.onerror = () => {
    speaking.value = false
  }
  window.speechSynthesis.speak(u)
  speaking.value = true
}
</script>

<style scoped>
.chat-msg {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  animation: chatMsgIn 0.35s ease both;
}

@keyframes chatMsgIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.speak-btn--active {
  color: #1677ff;
}

.chat-msg--user {
  flex-direction: row-reverse;
}

.chat-msg__avatar {
  flex-shrink: 0;
}

.chat-msg__body {
  max-width: 78%;
  min-width: 0;
}

/* 移动端：消息气泡更宽 */
@media (max-width: 768px) {
  .chat-msg {
    gap: 8px;
  }

  .chat-msg__body {
    max-width: 90%;
  }
}

.chat-msg__user-bubble {
  background: #1677ff;
  color: #fff;
  border-radius: 12px 4px 12px 12px;
  padding: 10px 14px;
  line-height: 1.6;
  font-size: 14px;
  white-space: pre-wrap;
  word-break: break-word;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.2);
}

.chat-msg__ai-bubble {
  background: #fff;
  border-radius: 4px 12px 12px 12px;
  padding: 12px 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  border: 1px solid #f0f0f0;
}

.chat-msg__streaming {
  color: #86909c;
  font-size: 12px;
  margin-top: 6px;
}

.chat-msg__actions {
  margin-top: 4px;
  opacity: 1;
}

.chat-msg__followup {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.chat-msg__followup-label {
  font-size: 12px;
  color: #86909c;
  margin-right: 2px;
}

.chat-msg__followup-tag {
  cursor: pointer;
  background: #e6f4ff;
  border: 1px solid #91caff;
  color: #1677ff;
}

/* 文献全文弹窗：窗口放大 + 内容纵向滚动（自动换行，无需横向拖动） */
.viewer-headings {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.viewer-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.viewer-markdown {
  flex: 1;
  max-height: 62vh;
  min-height: 240px;
  overflow-y: auto;
  background: #f7f8fa;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 14px 18px;
}
</style>

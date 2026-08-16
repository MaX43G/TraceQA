<template>
  <div class="chat-page">
    <!-- 桌面端侧边栏 -->
    <aside class="chat-page__aside chat-page__aside--desktop">
      <SessionList
        :sessions="chat.sessions"
        :current-session-id="chat.currentSessionId"
        @new="handleNew"
        @select="handleSelect"
        @pin="handlePin"
        @remove="handleRemove"
      />
    </aside>

    <!-- 移动端会话抽屉 -->
    <a-drawer
      v-model:open="sidebarOpen"
      placement="left"
      :width="280"
      :closable="false"
      body-style="padding:0"
    >
      <SessionList
        :sessions="chat.sessions"
        :current-session-id="chat.currentSessionId"
        @new="handleNew"
        @select="(id) => { handleSelect(id); sidebarOpen = false }"
        @pin="handlePin"
        @remove="handleRemove"
      />
    </a-drawer>

    <main class="chat-page__main">
      <div class="chat-page__toolbar">
        <a-space :size="8">
          <a-button class="chat-page__menu-btn" type="text" @click="sidebarOpen = true">
            <template #icon><MenuOutlined /></template>
          </a-button>
          <a-select
            v-model:value="chat.knowledgeBaseId"
            placeholder="选择知识库"
            allow-clear
            style="width: 160px"
            class="chat-page__kb-select"
            @change="handleKbChange"
          >
            <a-select-option v-for="kb in kbs" :key="kb.id" :value="kb.id">{{ kb.name }}</a-select-option>
          </a-select>
          <ModelSelector />
        </a-space>
        <a-button v-if="chat.currentSessionId" type="text" @click="handleExport">
          <template #icon><ExportOutlined /></template>
          导出对话
        </a-button>
      </div>

      <div ref="listRef" class="chat-page__list">
        <div v-if="chat.messages.length === 0" class="chat-page__welcome">
          <div class="chat-page__welcome-logo">溯</div>
          <h2>溯知 · TraceQA</h2>
          <p>基于知识图谱与向量检索的《数据挖掘》课程智能问答</p>
          <a-space wrap>
            <a-tag v-for="q in quickQuestions" :key="q" class="chat-page__quick" @click="askQuick(q)">
              {{ q }}
            </a-tag>
          </a-space>
        </div>
        <template v-else>
          <ChatMessageItem
            v-for="msg in chat.messages"
            :key="msg.id"
            :msg="msg"
            :streaming="isStreamingMsg(msg)"
            @delete="handleDeleteMessage"
          />
        </template>
      </div>

      <div class="chat-page__input">
        <ChatInput :disabled="chat.generating" :generating="chat.generating" @send="handleSend" @stop="stopGenerating" />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
/**
 * 智能问答页（SSR + SEO）。
 *
 * <p>布局：左侧会话列表 + 右侧聊天区（工具栏 / 消息流 / 输入框）。
 * 流式回答基于 SSE 实时渲染（打字机 + 状态图 + 引用溯源），支持随时中断。</p>
 */
import { ExportOutlined, MenuOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { useModelStore } from '@/stores/model'
import { streamChat } from '@/composables/useChatStream'
import SessionList from '@/components/chat/SessionList.vue'
import ChatMessageItem from '@/components/chat/ChatMessageItem.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import ModelSelector from '@/components/chat/ModelSelector.vue'
import type { SessionVO, KnowledgeBaseDTO, ChatMessageVO, ThinkingNodeVO, ReferenceVO } from '@/utils/api-types'
import type { StreamMessage } from '@/stores/chat'
import { list1 as apiListKbs } from '@/api/traceqa/zhishiku'

useSeoMeta({
  title: '智能问答 - 溯知 · TraceQA',
  description: '基于知识图谱与向量检索的《数据挖掘》课程智能问答'
})

const chat = useChatStore()
const auth = useAuthStore()
const modelStore = useModelStore()
const listRef = ref<HTMLElement | null>(null)
/** 移动端会话抽屉开关 */
const sidebarOpen = ref(false)

/** 知识库列表 */
const kbs = ref<KnowledgeBaseDTO[]>([])
const kbsLoaded = ref(false)

/** 快捷提问示例 */
const quickQuestions = ['什么是 K 均值聚类？', '解释一下 Apriori 关联规则算法', '决策树是如何进行特征选择的？']

/** 判断是否为流式临时消息 */
function isStreamingMsg(msg: ChatMessageVO | StreamMessage): boolean {
  return 'streaming' in msg && msg.streaming
}

onMounted(async () => {
  // 认证由全局路由守卫（middleware/auth.global.ts）保证
  modelStore.initFromStorage()
  await Promise.all([chat.loadSessions(), loadKnowledgeBases(), modelStore.loadServerModels()])
})

async function loadKnowledgeBases(): Promise<void> {
  if (kbsLoaded.value) {
    return
  }
  try {
    const res = await apiListKbs()
    kbs.value = res.data ?? []
    kbsLoaded.value = true
  } catch (err) {
    message.error((err as Error).message || '知识库加载失败')
  }
}

function handleKbChange(): void {
  // 切换知识库时重置会话
  chat.currentSessionId = null
  chat.messages = []
}

function handleNew(): void {
  chat.currentSessionId = null
  chat.messages = []
}

async function handleSelect(sessionId: number | string): Promise<void> {
  await chat.openSession(sessionId)
}

async function handlePin(session: SessionVO): Promise<void> {
  await chat.pinSession(session.id, session.pinned !== 1)
}

async function handleRemove(session: SessionVO): Promise<void> {
  Modal.confirm({
    title: '删除会话',
    content: `确定删除「${session.title}」吗？删除后不可恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      await chat.removeSession(session.id)
      message.success('会话已删除')
    }
  })
}

async function handleDeleteMessage(messageId: number): Promise<void> {
  await chat.removeMessage(messageId)
  // 后端已级联删除配对的 USER/ASSISTANT 消息，刷新列表保持 UI 一致
  if (chat.currentSessionId) {
    await chat.openSession(chat.currentSessionId)
  }
  message.success('消息已删除')
}

async function handleExport(): Promise<void> {
  try {
    const md = await chat.exportCurrentSession()
    if (!md) {
      message.warning('当前会话为空')
      return
    }
    const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `traceqa-${chat.currentSessionId}.md`
    a.click()
    URL.revokeObjectURL(url)
    message.success('已导出 Markdown')
  } catch (err) {
    message.error((err as Error).message || '导出失败')
  }
}

/** 点击快捷提问 */
async function askQuick(question: string): Promise<void> {
  await handleSend(question)
}

/** 发送消息（SSE 流式消费） */
async function handleSend(content: string): Promise<void> {
  if (chat.generating) {
    return
  }
  chat.generating = true

  // 确保存在会话
  if (!chat.currentSessionId) {
    await chat.newSession()
  }
  const sessionId = chat.currentSessionId

  // 追加临时用户消息
  chat.messages.push({
    id: -Date.now() - 1,
    sessionId,
    role: 'USER',
    content,
    thinkingTrace: [],
    references: [],
    latencyMs: 0,
    createTime: new Date().toISOString()
  } as ChatMessageVO)

  // 追加流式 AI 消息占位（reactive 保证后续增量实时响应渲染）
  const streamMsg = reactive({
    id: -Date.now() - 2,
    sessionId,
    role: 'ASSISTANT',
    content: '',
    thinkingTrace: [] as ThinkingNodeVO[],
    references: [] as ReferenceVO[],
    latencyMs: 0,
    createTime: new Date().toISOString(),
    streaming: true,
    buffer: ''
  }) as StreamMessage
  chat.messages.push(streamMsg)

  // 携带模型选择：优先服务端模型（平台默认 Key/URL），其次自定义模型
  const serverModel = modelStore.activeServerModel
  const modelConfig = modelStore.activeCustomConfig
  // 中止控制器：页面卸载时中断未完成的流式请求
  const abortController = new AbortController()
  activeStreamAbort = abortController
  await streamChat(
    {
      sessionId,
      knowledgeBaseId: chat.knowledgeBaseId,
      content,
      ...(serverModel ? { serverModel } : {}),
      ...(modelConfig ? { model: modelConfig.model, baseUrl: modelConfig.baseUrl, apiKey: modelConfig.apiKey } : {})
    },
    {
      onThinking: (node) => {
        mergeThinkingNode(streamMsg, node)
      },
      onDelta: (chunk) => {
        streamMsg.buffer += chunk
      },
      onReferences: (references) => {
        streamMsg.references = references
      },
      onDone: () => {
        streamMsg.streaming = false
        streamMsg.content = streamMsg.buffer
      },
      onError: (err) => {
        streamMsg.streaming = false
        streamMsg.content = streamMsg.buffer || err.msg || '服务异常'
        message.error(err.msg || 'AI 服务暂时不可用')
      },
      onEnd: async () => {
        chat.generating = false
        activeStreamAbort = null
        // 用户主动停止：保留本地已生成内容，不重新加载后端（可能尚未保存）
        if (userStopped) {
          userStopped = false
          return
        }
        // 刷新会话与消息（获取真实 ID）
        await chat.loadSessions()
        if (chat.currentSessionId) {
          await chat.openSession(chat.currentSessionId)
        }
      }
    },
    abortController.signal
  )
}

/** 合并思考节点（按 stage 更新状态） */
function mergeThinkingNode(streamMsg: StreamMessage, node: ThinkingNodeVO): void {
  const nodes = streamMsg.thinkingTrace ?? []
  const idx = nodes.findIndex((n) => n.stage === node.stage)
  if (idx === -1) {
    nodes.push(node)
  } else {
    nodes[idx] = node
  }
  streamMsg.thinkingTrace = [...nodes]
}

/** 当前进行中的流式请求 AbortController */
let activeStreamAbort: AbortController | null = null
/** 是否由用户主动停止当前回答 */
let userStopped = false

/** 用户主动中断回答：保留已生成内容并结束本次流 */
function stopGenerating(): void {
  if (!chat.generating) {
    return
  }
  userStopped = true
  const last = chat.messages[chat.messages.length - 1]
  if (last && isStreamingMsg(last)) {
    if (!last.buffer) {
      // 尚未生成任何内容（思考/检索阶段停止）：移除空占位，与后端「不保存空消息」保持一致
      chat.messages.pop()
    } else {
      last.streaming = false
      last.content = last.buffer || last.content
    }
  }
  chat.generating = false
  activeStreamAbort?.abort()
  activeStreamAbort = null
}

onBeforeUnmount(() => {
  activeStreamAbort?.abort()
})

/** 消息或流式缓冲变化时自动滚动到底部 */
watch(
  () => {
    const last = chat.messages[chat.messages.length - 1]
    return last
      ? [chat.messages.length, last.content, (last as StreamMessage).buffer]
      : [0, '', '']
  },
  () => {
    nextTick(() => {
      listRef.value?.scrollTo({ top: listRef.value.scrollHeight, behavior: 'smooth' })
    })
  },
  { deep: true }
)
</script>

<style scoped>
.chat-page {
  display: flex;
  height: calc(100vh - 56px);
}

.chat-page__aside {
  width: 260px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #f0f0f0;
  overflow: hidden;
}

.chat-page__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
}

.chat-page__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
}

.chat-page__list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 4px 24px;
}

.chat-page__welcome {
  text-align: center;
  padding-top: 14vh;
  color: #4e5969;
}

.chat-page__welcome-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: linear-gradient(135deg, #1677ff, #06b6d4);
  color: #fff;
  font-size: 32px;
  font-weight: 700;
}

.chat-page__welcome h2 {
  margin: 16px 0 8px;
  color: #1f2329;
}

.chat-page__quick {
  cursor: pointer;
  padding: 4px 12px;
}

.chat-page__menu-btn {
  display: none;
}

.chat-page__input {
  padding: 12px 0 20px;
}

/* ---- 移动端适配 ---- */
@media (max-width: 768px) {
  .chat-page__aside--desktop {
    display: none;
  }

  .chat-page__menu-btn {
    display: inline-flex;
  }

  .chat-page__main {
    padding: 0 12px;
  }

  .chat-page__toolbar {
    gap: 8px;
  }

  .chat-page__kb-select {
    width: 120px !important;
  }

  .chat-page__list {
    padding: 8px 2px 16px;
  }

  .chat-page__welcome {
    padding-top: 10vh;
  }
}
</style>

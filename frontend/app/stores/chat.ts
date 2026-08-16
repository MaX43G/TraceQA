/**
 * 对话状态（Pinia Store）。
 *
 * <p>管理会话列表、当前会话消息与流式生成的临时消息。
 * 流式回答基于 {@link useChatStream} 消费 SSE，逐增量追加渲染。</p>
 */
import { defineStore } from 'pinia'
import {
  listSessions,
  listMessages,
  createSession,
  deleteSession,
  deleteMessage,
  togglePin,
  exportMarkdown
} from '@/api/traceqa/duihua'
import type { ChatMessageVO, SessionVO } from '@/utils/api-types'

/** 前端临时流式消息（对应后端 AI 消息，持久化前使用） */
export interface StreamMessage extends ChatMessageVO {
  /** 是否仍在生成（打字机效果） */
  streaming: boolean
  /** 内容增量缓冲 */
  buffer: string
}

export const useChatStore = defineStore('chat', {
  state: () => ({
    /** 会话列表 */
    sessions: [] as SessionVO[],
    /** 当前会话 ID */
    currentSessionId: null as number | null,
    /** 当前会话消息列表 */
    messages: [] as ChatMessageVO[],
    /** 知识库筛选（null 表示全局检索） */
    knowledgeBaseId: null as number | null,
    /** 是否正在生成回答 */
    generating: false
  }),

  actions: {
    /** 拉取会话列表 */
    async loadSessions(): Promise<void> {
      const res = await listSessions()
      this.sessions = res.data ?? []
    },

    /** 创建会话 */
    async newSession(): Promise<void> {
      const res = await createSession({
        title: '新对话',
        knowledgeBaseId: this.knowledgeBaseId ?? null
      })
      const session = res.data
      if (session) {
        this.sessions.unshift(session)
        this.currentSessionId = session.id
        this.messages = []
      }
    },

    /** 切换会话并加载消息（雪花 ID 以字符串传输，避免精度丢失） */
    async openSession(sessionId: number | string): Promise<void> {
      this.currentSessionId = sessionId as number
      const res = await listMessages({ id: sessionId as unknown as number })
      this.messages = res.data ?? []
    },

    /** 删除会话 */
    async removeSession(sessionId: number): Promise<void> {
      await deleteSession({ id: sessionId })
      this.sessions = this.sessions.filter((s) => s.id !== sessionId)
      if (this.currentSessionId === sessionId) {
        this.currentSessionId = null
        this.messages = []
      }
    },

    /** 删除单条消息 */
    async removeMessage(messageId: number): Promise<void> {
      await deleteMessage({ id: messageId })
      this.messages = this.messages.filter((m) => m.id !== messageId)
    },

    /** 置顶/取消置顶 */
    async pinSession(sessionId: number, pinned: boolean): Promise<void> {
      await togglePin({ id: sessionId, pinned })
      await this.loadSessions()
    },

    /** 导出会话为 Markdown */
    async exportCurrentSession(): Promise<string> {
      if (!this.currentSessionId) {
        return ''
      }
      return exportMarkdown({ id: this.currentSessionId })
    }
  }
})

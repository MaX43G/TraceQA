/**
 * SSE 流式对话消费工具（fetch 流解析）。
 *
 * <p>后端以 {@code event: xxx \n data: {...}} 格式推送：
 * {@code thinking} 思考节点、{@code delta} 内容增量、{@code references} 引用、
 * {@code done} 结束、{@code error} 错误。此处逐行解析并分发回调。</p>
 */
import { getAuthHeaders } from '@/utils/request'
import type { ThinkingNodeVO, ReferenceVO } from '@/utils/api-types'

/** SSE 事件回调集合 */
export interface ChatStreamHandlers {
  /** 思考节点（running/done/failed 均通过该回调） */
  onThinking?: (node: ThinkingNodeVO) => void
  /** 内容增量 */
  onDelta?: (content: string) => void
  /** 引用来源 */
  onReferences?: (references: ReferenceVO[]) => void
  /** 结束（携带会话/消息 ID） */
  onDone?: (payload: { sessionId?: number; messageId?: number; title?: string }) => void
  /** 服务端错误 */
  onError?: (error: { code?: number; msg?: string }) => void
  /** 流结束（无论成功失败均触发） */
  onEnd?: () => void
}

/** 发起流式对话请求并消费事件 */
export async function streamChat(
  body: {
    sessionId?: number | null
    knowledgeBaseId?: number | null
    content: string
    /** 选中的服务端模型名（平台默认 Key/URL） */
    serverModel?: string
    /** 自定义模型：OpenAI 兼容地址 / API Key / 模型名（仅本次请求） */
    model?: string
    baseUrl?: string
    apiKey?: string
  },
  handlers: ChatStreamHandlers,
  signal?: AbortSignal
): Promise<void> {
  let res: Response | undefined
  try {
    res = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify(body),
      signal
    })
  } catch (err) {
    handlers.onError?.({ msg: '网络异常，请检查后端服务是否可用' })
    handlers.onEnd?.()
    return
  }

  if (!res || !res.ok || !res.body) {
    // 后端返回非 SSE（如鉴权失败），尽力解析统一错误结构
    let msg = `请求失败(${res?.status ?? '未知'})`
    try {
      const json = await res?.json()
      msg = json?.msg || msg
      handlers.onError?.({ code: json?.code, msg })
    } catch {
      handlers.onError?.({ msg })
    }
    handlers.onEnd?.()
    return
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      // 按事件块（空行分隔）解析
      let sepIndex: number
      while ((sepIndex = buffer.indexOf('\n\n')) !== -1) {
        const block = buffer.slice(0, sepIndex)
        buffer = buffer.slice(sepIndex + 2)
        dispatchBlock(block, handlers)
      }
    }
  } catch (err) {
    if ((err as Error).name !== 'AbortError') {
      handlers.onError?.({ msg: '流式连接中断' })
    }
  } finally {
    handlers.onEnd?.()
  }
}

/** 解析单个 SSE 事件块并分发 */
function dispatchBlock(block: string, handlers: ChatStreamHandlers): void {
  const lines = block.split('\n')
  let event = 'message'
  const dataLines: string[] = []

  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }
  if (dataLines.length === 0) {
    return
  }

  // 合并可能跨行的 data，尝试解析 JSON
  const raw = dataLines.join('\n')
  let payload: unknown
  try {
    payload = JSON.parse(raw)
  } catch {
    payload = raw
  }

  switch (event) {
    case 'thinking':
      handlers.onThinking?.(payload as ThinkingNodeVO)
      break
    case 'delta': {
      const p = payload as { content?: string }
      if (p && p.content) {
        handlers.onDelta?.(p.content)
      }
      break
    }
    case 'references':
      handlers.onReferences?.((payload as { references?: ReferenceVO[] })?.references ?? [])
      break
    case 'done':
      handlers.onDone?.(payload as { sessionId?: number; messageId?: number; title?: string })
      break
    case 'error':
      handlers.onError?.(payload as { code?: number; msg?: string })
      break
    default:
      break
  }
}

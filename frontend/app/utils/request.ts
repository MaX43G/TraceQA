/**
 * 统一 HTTP 请求封装（供 umijs/openapi 生成的 API 客户端调用）。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>自动附加登录令牌头（Authorization: Bearer，存于 localStorage）；</li>
 *   <li>统一解析后端 {@code {code,msg,data,traceId}} 响应结构，仅返回 data；</li>
 *   <li>非 200 业务码抛出 {@link ApiError}，前端仅依据 {@code code} 判断；</li>
 *   <li>兼容 Markdown 文本导出等非 JSON 响应。</li>
 * </ul>
 */

/** 后端统一响应结构 */
export interface ApiResponse<T = unknown> {
  /** 业务错误码，200 为成功 */
  code: number
  /** 提示信息 */
  msg: string
  /** 业务数据 */
  data: T
  /** 链路追踪 ID */
  traceId: string
}

/** 请求选项（兼容 umijs/openapi 生成的调用签名） */
export interface RequestOptions {
  /** HTTP 方法 */
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  /** 请求体（JSON） */
  data?: unknown
  /** URL 查询参数 */
  params?: Record<string, unknown>
  /** 附加请求头 */
  headers?: Record<string, string>
  /** 其余透传给 fetch 的选项 */
  [key: string]: unknown
}

/** 统一业务异常 */
export class ApiError extends Error {
  /** 业务错误码 */
  code: number
  /** 链路追踪 ID */
  traceId: string

  constructor(msg: string, code: number, traceId = '-') {
    super(msg)
    this.name = 'ApiError'
    this.code = code
    this.traceId = traceId
  }
}

/** 认证 Token 存储键 */
export const TOKEN_KEY = 'tq_token'

/** 获取认证请求头（SSR 环境无 window 时返回空） */
export function getAuthHeaders(): Record<string, string> {
  if (typeof window === 'undefined') {
    return {}
  }
  const token = window.localStorage.getItem(TOKEN_KEY)
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/** 组装查询字符串（过滤空值） */
function buildQuery(params?: Record<string, unknown>): string {
  if (!params) {
    return ''
  }
  const usp = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      usp.append(key, String(value))
    }
  })
  const qs = usp.toString()
  return qs ? `?${qs}` : ''
}

/**
 * 解析响应体：
 * - JSON 响应返回完整 {@code {code,msg,data,traceId}} 结构（与生成类型一致）；
 * - 非 JSON（如 Markdown 导出）返回原文。
 * 非 200 业务码抛出 {@link ApiError}。
 */
async function unwrapResponse<T>(res: Response): Promise<T> {
  const contentType = res.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    const json = (await res.json()) as ApiResponse<T>
    if (json.code !== 200) {
      throw new ApiError(json.msg || '请求失败', json.code, json.traceId)
    }
    return json as T
  }
  return (await res.text()) as T
}

/** 认证失败错误码：清除本地令牌并跳转登录页 */
function handleAuthFailure(error: ApiError): void {
  if (error.code !== 40100 && error.code !== 40101) {
    return
  }
  if (typeof window === 'undefined') {
    return
  }
  window.localStorage.removeItem(TOKEN_KEY)
  // 避免已在登录页时重复跳转
  if (!window.location.pathname.startsWith('/login')) {
    const redirect = encodeURIComponent(window.location.pathname + window.location.search)
    window.location.href = `/login?redirect=${redirect}`
  }
}

/**
 * 统一请求入口。
 *
 * @param url     接口路径（如 /api/auth/login）
 * @param options 请求选项
 */
export default async function request<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', data, params, headers = {}, ...rest } = options
  const query = buildQuery(params)
  const isFormData = data instanceof FormData

  try {
    const res = await fetch(`${url}${query}`, {
      method,
      headers: {
        ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
        ...getAuthHeaders(),
        ...headers
      },
      body: isFormData ? (data as FormData) : data !== undefined ? JSON.stringify(data) : undefined,
      ...rest
    })

    if (!res.ok) {
      // 后端全局异常已返回统一结构，尝试解包
      try {
        const json = (await res.json()) as ApiResponse<unknown>
        throw new ApiError(json.msg || `请求失败(${res.status})`, json.code || res.status, json.traceId)
      } catch (e) {
        if (e instanceof ApiError) {
          throw e
        }
        throw new ApiError(`网络异常(${res.status})`, res.status)
      }
    }
    return await unwrapResponse<T>(res)
  } catch (error) {
    // 统一在此处处理认证失败（避免重复触发）
    if (error instanceof ApiError) {
      handleAuthFailure(error)
    }
    throw error
  }
}

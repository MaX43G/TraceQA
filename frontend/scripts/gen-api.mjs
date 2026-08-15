/**
 * OpenAPI -> TypeScript API 客户端生成脚本。
 *
 * <p>从后端自动生成的 OpenAPI 规范（/v3/api-docs）生成类型安全的接口客户端，
 * 严格遵循「API 契约由后端驱动，前端严禁手写魔法字符串」的规范。</p>
 *
 * <p>使用：{@code npm run gen:api}</p>
 */
import { generateService } from '@umijs/openapi'
import { writeFileSync, mkdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = dirname(fileURLToPath(import.meta.url))
const API_DOCS_URL = process.env.API_DOCS_URL || 'http://localhost:8080/v3/api-docs'
const SPEC_PATH = resolve(ROOT, 'openapi.json')
const SERVERS_PATH = resolve(ROOT, '../app/api')

/** 拉取后端 OpenAPI 规范到本地 */
async function downloadSpec() {
  console.log(`[gen:api] 拉取 OpenAPI 规范：${API_DOCS_URL}`)
  const res = await fetch(API_DOCS_URL)
  if (!res.ok) {
    throw new Error(`拉取 OpenAPI 规范失败：HTTP ${res.status}`)
  }
  const spec = await res.json()
  writeFileSync(SPEC_PATH, JSON.stringify(spec, null, 2), 'utf-8')
  console.log(`[gen:api] 规范已保存：${SPEC_PATH}`)
  return SPEC_PATH
}

/** 生成 TS 客户端 */
async function main() {
  const schemaPath = await downloadSpec()
  mkdirSync(SERVERS_PATH, { recursive: true })

  await generateService({
    schemaPath,
    serversPath: SERVERS_PATH,
    projectName: 'traceqa',
    // 请求封装：统一鉴权与响应解包
    requestLibPath: '@/utils/request',
    // 排除 SSE 流式接口（由专用 composable 消费）
    exclude: [
      '/api/chat/stream',
      '/api/documents/{id}/progress'
    ]
  })
  console.log(`[gen:api] API 客户端已生成：${SERVERS_PATH}`)
}

main().catch((err) => {
  console.error('[gen:api] 生成失败：', err)
  process.exit(1)
})

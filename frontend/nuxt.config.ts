// https://nuxt.com/docs/api/configuration/nuxt-config
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import {AntDesignVueResolver} from 'unplugin-vue-components/resolvers'

// 访问 process.env（不依赖 @types/node，通过 globalThis 安全取用）
const processEnv: Record<string, string | undefined> =
    (globalThis as { process?: { env?: Record<string, string | undefined> } }).process?.env ?? {}
/** 后端代理目标（默认 localhost:8080） */
const API_PROXY = processEnv.API_PROXY_TARGET || 'http://localhost:8080'

/** Vite 插件自定义类型的解析上下文（仅用到 resolve） */
interface ResolveContext {
    resolve: (id: string, importer?: string | undefined, options?: { skipSelf?: boolean }) =>
        Promise<{ id: string } | null | undefined>
}

/** dayjs ESM 解析插件对象（结构类型，避免 import vite 类型） */
interface DayjsEsmPlugin {
    name: string
    enforce: 'pre'
    config?: (config: unknown, env: { command?: string }) => void
    resolveId?: (this: ResolveContext, source: string, importer?: string, options?: {
        ssr?: boolean
    }) => Promise<unknown>
}

/**
 * 开发模式下的 dayjs ESM 解析插件。
 *
 * <p>仅在 Vite serve（dev）、且非 SSR 时生效：将 {@code dayjs} 及
 * {@code dayjs/plugin/*} 重定向到其 ESM 构建（具备正确的 default 导出），
 * 规避浏览器端 “does not provide an export named 'default'” 报错。
 * 生产构建保持原生解析。</p>
 */
function fixDayjsEsmDev(): DayjsEsmPlugin {
    let isDev = false
    return {
        name: 'traceqa-dayjs-esm-dev',
        // 在 Vite 内部依赖优化插件之前执行，确保 dayjs 重定向优先
        enforce: 'pre',
        config(_config: unknown, env: { command?: string }): void {
            isDev = env.command === 'serve'
        },
        async resolveId(this: ResolveContext, source: string, importer?: string, options?: {
            ssr?: boolean
        }): Promise<unknown> {
            // 仅重定向客户端模块；SSR（vite-node）由 Node 原生处理 dayjs
            if (!isDev || options?.ssr) {
                return null
            }
            const isDayjs = source === 'dayjs'
            const pluginMatch = source.match(/^dayjs\/plugin\/(.+)$/)
            if (!isDayjs && !pluginMatch) {
                return null
            }
            // 让 Vite 正常解析，得到 dayjs 在 .pnpm 中的真实安装路径
            const resolved = await this.resolve(source, importer, {skipSelf: true})
            if (!resolved) {
                return null
            }
            // 由真实路径推导 ESM 构建目录：.../node_modules/dayjs/<...> → .../esm/<...>
            const sep = resolved.id.lastIndexOf('/dayjs/')
            if (sep === -1) {
                return null
            }
            const esmRoot = `${resolved.id.slice(0, sep)}/dayjs/esm`
            if (isDayjs) {
                return `${esmRoot}/index.js`
            }
            return `${esmRoot}/plugin/${pluginMatch![1]}/index.js`
        }
    }
}

export default defineNuxtConfig({
    compatibilityDate: '2025-07-15',
    devtools: {enabled: true},

    // 模块：Pinia 状态管理（SSR 兼容）
    modules: ['@pinia/nuxt'],

    // Ant Design Vue 全局样式
    css: [
        'ant-design-vue/dist/reset.css',
        'katex/dist/katex.min.css',
        'highlight.js/styles/github-dark.css',
        '@/assets/css/main.css'
    ],

    // 服务端渲染 + 简单 SEO
    ssr: true,
    app: {
        // 页面切换渐变过渡动画
        pageTransition: {name: 'page', mode: 'out-in'},
        head: {
            charset: 'utf-8',
            viewport: 'width=device-width, initial-scale=1',
            htmlAttrs: {lang: 'zh-CN'},
            title: '溯知 · TraceQA - 数据挖掘课程智能问答平台',
            meta: [
                {name: 'description', content: '溯知（TraceQA）——基于知识图谱与向量检索的《数据挖掘》课程智能问答平台'},
                {name: 'keywords', content: '数据挖掘,TraceQA,溯知,RAG,知识图谱,智能问答'},
                {property: 'og:title', content: '溯知 · TraceQA'},
                {property: 'og:description', content: '基于知识图谱与向量检索的《数据挖掘》课程智能问答平台'},
                {property: 'og:type', content: 'website'}
            ],
            link: [{rel: 'icon', type: 'image/x-icon', href: '/favicon.ico'}]
        }
    },

    // 前端代理解决浏览器跨域：/api 请求转发至后端服务
    routeRules: {
        '/api/**': {
            proxy: `${API_PROXY}/api/**`
        },
        // LightRAG WebUI：经后端反向代理（管理员 Cookie 鉴权）转发，不暴露真实端口
        '/lightrag-webui/**': {
            proxy: `${API_PROXY}/lightrag-webui/**`
        },
        // LightRAG WebUI 内对 API 文档的链接常指向根路径（/docs、/redoc、/openapi.json），
        // 这些路径同样经后端反向代理（管理员 Cookie 鉴权）转发到 LightRAG
        '/docs': {
            proxy: `${API_PROXY}/lightrag-webui/docs`
        },
        '/redoc': {
            proxy: `${API_PROXY}/lightrag-webui/redoc`
        },
        '/openapi.json': {
            proxy: `${API_PROXY}/lightrag-webui/openapi.json`
        },
        // 可观测性工具：Grafana / Prometheus 经后端反向代理（管理员 Cookie 鉴权）访问
        '/grafana/**': {
            proxy: `${API_PROXY}/grafana/**`
        },
        '/prometheus/**': {
            proxy: `${API_PROXY}/prometheus/**`
        },
        // 页面 HTML 不缓存，避免浏览器加载旧版 JS（新版部署后状态图等失效）
        '/': {headers: {'Cache-Control': 'no-store'}},
        '/chat': {headers: {'Cache-Control': 'no-store'}},
        '/login': {headers: {'Cache-Control': 'no-store'}},
        '/admin': {headers: {'Cache-Control': 'no-store'}},
        '/admin/**': {headers: {'Cache-Control': 'no-store'}}
    },

    typescript: {
        strict: true
    },

    build: {
        transpile: ['ant-design-vue']
    },

    // 修复 Vite dev 下 dayjs 插件 CJS/UMD 互操作问题
    // （浏览器端会报 "does not provide an export named 'default'"，导致前端 JS 全部失效）
    // 方案：仅开发模式将 dayjs 及其插件解析到 ESM 构建；生产构建交由打包器原生处理。
    vite: {
        plugins: [
            // 结构类型与 vite Plugin 兼容；不引入 vite 类型依赖，故此处以 any 断言
            fixDayjsEsmDev() as any,
            // 自动导入 Vue/Pinia/@vueuse API，减少每个组件的手写 import
            AutoImport({
                imports: ['vue', 'vue-router', 'pinia', '@vueuse/core'],
                dts: false
            }),
            // ant-design-vue 按需引入，显著减小打包体积
            // dts: true 生成组件类型声明，让 IDE/Volar 识别组件及其插槽（#overlay/#prefix 等）
            Components({
                resolvers: [AntDesignVueResolver({importStyle: false})],
                dts: true
            })
        ]
    }
})
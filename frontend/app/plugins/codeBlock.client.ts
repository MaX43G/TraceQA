import {initCodeBlockHandlers} from '@/utils/codeBlock'

/**
 * 客户端插件：初始化代码块工具栏事件（复制 / 运行）。
 * 仅客户端执行（.client.ts），使用 document 级事件委托，对 v-html 渲染的代码块生效。
 */
export default defineNuxtPlugin(() => {
    if (import.meta.client) {
        initCodeBlockHandlers()
    }
})
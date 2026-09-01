/**
 * markdown-it 插件缺少官方类型声明，此处补充最小类型。
 */
declare module 'markdown-it-task-lists' {
    import type MarkdownIt from 'markdown-it'

    interface TaskListsOptions {
        enabled?: boolean
        label?: boolean
        labelAfter?: boolean
    }

    const plugin: (md: MarkdownIt, options?: TaskListsOptions) => void
    export default plugin
}

declare module 'markdown-it-footnote' {
    import type MarkdownIt from 'markdown-it'

    const plugin: (md: MarkdownIt, options?: Record<string, unknown>) => void
    export default plugin
}
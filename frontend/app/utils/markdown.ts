/**
 * Markdown 渲染工具（基于 markdown-it）。
 *
 * <p>功能：</p>
 * <ul>
 *   <li>代码高亮（highlight.js）；</li>
 *   <li>将回答中的 {@code [citation:N]} 引用标记渲染为可点击角标
 *       {@code <sup class="tq-cite" data-idx="N">[N]</sup>}，供前端绑定点击高亮事件。</li>
 * </ul>
 */
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import katex from 'katex'

/** 引用标记正则：匹配 [citation:1] 形式 */
const CITATION_RE = /\[citation:(\d+)]/g

/** 块级公式 $$...$$（支持跨行） */
const MATH_BLOCK_RE = /\$\$([\s\S]+?)\$\$/g
/** 行内公式 $...$（单行） */
const MATH_INLINE_RE = /\$([^$\n]+?)\$/g
/** 中文字符（数学公式不应包含中文） */
const CJK_RE = /[\u4e00-\u9fa5]/

/** 判定是否为真正的数学公式：含中文的 `$...$` 多半是误匹配的正文，跳过以保原文 */
function isMathCandidate(tex: string): boolean {
    return !CJK_RE.test(tex)
}

/**
 * 将文本中的 LaTeX 公式（$$...$$ 块级、$...$ 行内）渲染为 KaTeX HTML。
 * 在 markdown 渲染前执行，故对 markdown 文本与原始 HTML 内的公式均生效；
 * 含中文或被误匹配的 `$...$`、非法 LaTeX 均保留原文，避免破坏正文。
 */
function renderMath(text: string): string {
    if (!text) {
        return text
    }
    let out = text.replace(MATH_BLOCK_RE, (_m, tex: string) => {
        if (!isMathCandidate(tex)) {
            return _m
        }
        try {
            const html = katex.renderToString(tex.trim(), { displayMode: true, throwOnError: true })
            return `<div class="katex-block">${html}</div>`
        } catch {
            return _m
        }
    })
    out = out.replace(MATH_INLINE_RE, (_m, tex: string) => {
        if (!isMathCandidate(tex)) {
            return _m
        }
        try {
            const html = katex.renderToString(tex.trim(), { throwOnError: true })
            return `<span class="katex-inline">${html}</span>`
        } catch {
            return _m
        }
    })
    return out
}

/**
 * 将回答文本中的引用标记替换为 HTML 角标（在 markdown 渲染前执行）。
 *
 * @param content          回答文本
 * @param availableIndexes 实际存在引用的序号集合；仅这些序号渲染为可点击角标，
 *                         其他（如模型误输出的 [citation:0]）保留为普通文本，避免出现无内容角标。
 */
export function decorateCitations(content: string, availableIndexes?: Set<number>): string {
    if (!content) {
        return content
    }
    return content.replace(CITATION_RE, (_match, idx: string) => {
        const n = Number(idx)
        if (availableIndexes && !availableIndexes.has(n)) {
            // 引用不存在：移除标记，避免渲染无内容角标
            return ''
        }
        return `<sup class="tq-cite" data-idx="${idx}">[${idx}]</sup>`
    })
}

const md = new MarkdownIt({
    html: true,
    linkify: true,
    breaks: true,
    highlight(str: string, lang: string): string {
        if (lang && hljs.getLanguage(lang)) {
            try {
                return `<pre class="hljs"><code>${hljs.highlight(str, {
                    language: lang,
                    ignoreIllegals: true
                }).value}</code></pre>`
            } catch {
                // 忽略高亮异常，退回转义展示
            }
        }
        return `<pre class="hljs"><code>${md.utils.escapeHtml(str)}</code></pre>`
    }
})

/**
 * 渲染 Markdown 为 HTML 字符串。
 *
 * @param content          原始 Markdown（可能包含 [citation:N]）
 * @param availableIndexes 实际存在引用的序号集合（可选，用于过滤无内容角标）
 * @returns 可直接 v-html 的 HTML
 */
export function renderMarkdown(content: string, availableIndexes?: Set<number>): string {
    if (!content) {
        return ''
    }
    return md.render(renderMath(decorateCitations(content, availableIndexes)))
}

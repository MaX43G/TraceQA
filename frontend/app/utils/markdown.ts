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
import DOMPurify from 'dompurify'

/** 引用标记正则：匹配 [citation:1] 形式 */
const CITATION_RE = /\[citation:(\d+)]/g

/** 块级公式 $$...$$（支持跨行） */
const MATH_BLOCK_RE = /\$\$([\s\S]+?)\$\$/g
/** 行内公式 $...$（单行） */
const MATH_INLINE_RE = /\$([^$\n]+?)\$/g
/** 中文字符（用于区分公式与正文） */
const CJK_RE = /[\u4e00-\u9fa5]/

/**
 * 判定是否为真正的数学公式：
 * - 不含中文 → 视为公式；
 * - 含中文但使用了 LaTeX 命令（如 \text、\frac 等，含反斜杠）→ 视为公式（公式中可含 \text{中文}）；
 * - 含中文且无任何 LaTeX 命令 → 多为被误匹配的正文，跳过以保原文。
 */
function isMathCandidate(tex: string): boolean {
    if (!CJK_RE.test(tex)) {
        return true
    }
    return tex.includes('\\')
}

/** 将公式中的中文串包进 \text{...}，使 KaTeX 按文本渲染（而非当成数学符号） */
function protectCjk(tex: string): string {
    return tex.replace(/([\u4e00-\u9fa5]+)/g, '\\text{$1}')
}

/**
 * 渲染单个公式：先保护中文，再以宽松模式渲染。
 * strict=false + throwOnError=false：即使公式存在轻微瑕疵（如 OCR 产生的多余 # 等），
 * 也尽量渲染而非整段丢弃，保证内容完整可见。
 */
function renderTex(tex: string, displayMode: boolean): string {
    const options = { displayMode, throwOnError: false, strict: false }
    return katex.renderToString(protectCjk(tex.trim()), options)
}

/**
 * 将文本中的 LaTeX 公式（$$...$$ 块级、$...$ 行内）渲染为 KaTeX HTML。
 * 在 markdown 渲染前执行，故对 markdown 文本与原始 HTML 内的公式均生效；
 * 被误匹配的纯中文正文（无 LaTeX 命令）跳过保留原文。
 */
function renderMath(text: string): string {
    if (!text) {
        return text
    }
    let out = text.replace(MATH_BLOCK_RE, (_m, tex: string) => {
        if (!isMathCandidate(tex)) {
            return _m
        }
        return `<div class="katex-block">${renderTex(tex, true)}</div>`
    })
    out = out.replace(MATH_INLINE_RE, (_m, tex: string) => {
        if (!isMathCandidate(tex)) {
            return _m
        }
        return `<span class="katex-inline">${renderTex(tex, false)}</span>`
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
    linkify: false,
    breaks: true,
    highlight(str: string, lang: string): string {
        if (lang && hljs.getLanguage(lang)) {
            try {
                const langName = hljs.getLanguage(lang)!.name
                return `<pre><code class="hljs language-${langName}">${hljs.highlight(str, {
                    language: lang,
                    ignoreIllegals: true
                }).value}</code></pre>`
            } catch {
            }
        }
        return `<pre><code class="hljs">${md.utils.escapeHtml(str)}</code></pre>`
    }
})

/**
 * 渲染 Markdown 为 HTML 字符串。
 *
 * <p>渲染结果经 DOMPurify 消毒后返回，避免用户/AI 内容中的原始 HTML 引入 XSS。</p>
 *
 * @param content          原始 Markdown（可能包含 [citation:N]）
 * @param availableIndexes 实际存在引用的序号集合（可选，用于过滤无内容角标）
 * @returns 可安全用于 v-html 的 HTML
 */
export function renderMarkdown(content: string, availableIndexes?: Set<number>): string {
    if (!content) {
        return ''
    }
    const rendered = md.render(renderMath(decorateCitations(content, availableIndexes)))
    return DOMPurify.sanitize(rendered, {
        ADD_ATTR: ['style', 'data-idx'],
        ADD_TAGS: ['sup']
    })
}

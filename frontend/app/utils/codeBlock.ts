/**
 * 代码块工具栏处理：复制 / 运行。
 *
 * <p>「复制」将代码写入剪贴板；「运行」在前端执行代码：
 * JavaScript 用 Web Worker 隔离运行（原生、安全、无 DOM 访问），
 * Python 用 Pyodide（WASM）在浏览器内执行（代码不上传服务器，更安全）。</p>
 */

/** Base64 解码（兼容中文 UTF-8） */
function decodeB64(data: string): string {
    try {
        return decodeURIComponent(escape(atob(data)))
    } catch {
        return atob(data)
    }
}

/** 运行 JS（在 Web Worker 中隔离执行，捕获 console.log/error） */
function runJs(code: string): Promise<string> {
    return new Promise((resolve) => {
        const workerSource = `
            self.onmessage = async (e) => {
                const logs = []
                const log = (...args) => logs.push(args.map(String).join(' '))
                console.log = console.error = log
                try {
                    const result = await (0, eval)(e.data)
                    if (result !== undefined) logs.push(String(result))
                } catch (err) {
                    logs.push('Error: ' + (err && err.message ? err.message : err))
                }
                self.postMessage(logs.join('\\n'))
            }
        `
        const blob = new Blob([workerSource], {type: 'application/javascript'})
        const url = URL.createObjectURL(blob)
        const worker = new Worker(url)
        worker.onmessage = (e) => {
            URL.revokeObjectURL(url)
            resolve(e.data || '(无输出)')
        }
        worker.onerror = (e) => {
            URL.revokeObjectURL(url)
            resolve('Error: ' + e.message)
        }
        worker.postMessage(code)
    })
}

/** Pyodide 单例（优先本地自托管 /pyodide/，缺失则回退 CDN；仅首次运行 Python 时加载） */
let pyodidePromise: Promise<any> | null = null
const PYODIDE_VERSION = 'v0.26.4'
const PYODIDE_LOCAL = '/pyodide/'
const PYODIDE_CDN = `https://cdn.jsdelivr.net/pyodide/${PYODIDE_VERSION}/full/`

function getPyodide(): Promise<any> {
    if (!pyodidePromise) {
        pyodidePromise = loadPyodideFrom(PYODIDE_LOCAL).catch(() => loadPyodideFrom(PYODIDE_CDN))
    }
    return pyodidePromise
}

/** 从指定 indexURL 加载 Pyodide */
function loadPyodideFrom(base: string): Promise<any> {
    return new Promise((resolve, reject) => {
        const script = document.createElement('script')
        script.src = `${base}pyodide.js`
        script.onload = async () => {
            try {
                const pyodide = await (window as any).loadPyodide({indexURL: base})
                resolve(pyodide)
            } catch (e) {
                reject(e)
            }
        }
        script.onerror = () => reject(new Error(`无法加载 Pyodide（${base}）`))
        document.head.appendChild(script)
    })
}

/** 运行 Python（Pyodide WASM，捕获 stdout/stderr） */
async function runPython(code: string): Promise<string> {
    const pyodide = await getPyodide()
    let output = ''
    pyodide.setStdout({batched: (s: string) => {
        output += s + '\n'
    }})
    pyodide.setStderr({batched: (s: string) => {
        output += s + '\n'
    }})
    await pyodide.runPythonAsync(code)
    return output || '(无输出)'
}

/** 复制到剪贴板 */
async function copyCode(code: string): Promise<void> {
    try {
        await navigator.clipboard.writeText(code)
    } catch {
        // 降级：旧方案（仅文本域）
        const ta = document.createElement('textarea')
        ta.value = code
        document.body.appendChild(ta)
        ta.select()
        document.execCommand('copy')
        document.body.removeChild(ta)
    }
}

/**
 * 初始化代码块事件（document 级事件委托，供 v-html 渲染的代码块使用）。
 * 建议在客户端插件/页面挂载时调用一次。
 */
export function initCodeBlockHandlers(): void {
    document.addEventListener('click', async (e) => {
        const target = e.target as HTMLElement
        const btn = target.closest<HTMLElement>('[data-action]')
        if (!btn) {
            return
        }
        const action = btn.dataset.action
        const code = decodeB64(btn.dataset.code || '')
        const lang = btn.dataset.lang || ''

        if (action === 'copy') {
            await copyCode(code)
            const old = btn.textContent
            btn.textContent = '已复制'
            setTimeout(() => {
                btn.textContent = old
            }, 1200)
            return
        }

        if (action === 'run') {
            const block = btn.closest('.code-block')
            if (!block) {
                return
            }
            let out = block.querySelector<HTMLElement>('.code-block-output')
            if (!out) {
                out = document.createElement('pre')
                out.className = 'code-block-output'
                block.appendChild(out)
            }
            out.textContent = '运行中…'
            try {
                if (lang === 'javascript' || lang === 'js') {
                    out.textContent = await runJs(code)
                } else if (lang === 'python' || lang === 'py') {
                    out.textContent = await runPython(code)
                } else {
                    out.textContent = '系统暂不支持该语言运行（支持 JavaScript / Python）'
                }
            } catch (err: any) {
                out.textContent = '运行出错：' + (err && err.message ? err.message : String(err))
            }
        }
    })
}
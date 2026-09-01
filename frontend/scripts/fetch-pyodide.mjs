/**
 * 自托管 Pyodide：下载官方发布包并解压到 frontend/public/pyodide/。
 * 之后前端会优先从本地 /pyodide/ 加载（无需依赖 CDN）。
 *
 * 用法：node scripts/fetch-pyodide.mjs
 * 注意：会向 public/pyodide/ 写入约 100MB 文件（会增大前端镜像体积）。
 */
import {execSync} from 'node:child_process'
import {mkdirSync} from 'node:fs'
import {join} from 'node:path'

const VERSION = '0.26.4'
const DEST = join(process.cwd(), 'public', 'pyodide')
const URL = `https://github.com/pyodide/pyodide/releases/download/v${VERSION}/pyodide-${VERSION}.tar.bz2`
const TMP = '/tmp/pyodide.tar.bz2'

mkdirSync(DEST, {recursive: true})
console.log(`下载 Pyodide ${VERSION} ...`)
execSync(`curl -L --fail -o ${TMP} "${URL}"`, {stdio: 'inherit'})
console.log('解压到 ' + DEST)
execSync(`tar -xjf ${TMP} -C "${DEST}" --strip-components=1`, {stdio: 'inherit'})
console.log('完成：自托管 Pyodide 已就绪（/pyodide/）')
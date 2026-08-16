<template>
  <div>
    <div class="toolbar">
      <a-select v-model:value="selectedKbId" placeholder="选择知识库" style="width: 260px" @change="loadDocs">
        <a-select-option v-for="kb in kbs" :key="kb.id" :value="kb.id">{{ kb.name }}</a-select-option>
      </a-select>
      <a-upload
        :show-upload-list="false"
        :disabled="!selectedKbId"
        accept=".md,.txt"
        :before-upload="handleBeforeUpload"
        :custom-request="handleUpload"
      >
        <a-tooltip title="支持 Markdown(.md) / 文本(.txt)。PDF、PPT、Word、图片等格式请先用 MinerU 等工具转换为 Markdown 后再上传">
          <a-button type="primary" :disabled="!selectedKbId" :loading="uploading">
            <template #icon><UploadOutlined /></template>
            上传文档
          </a-button>
        </a-tooltip>
      </a-upload>
      <a-upload
        :show-upload-list="false"
        :disabled="!selectedKbId"
        accept=".zip"
        :custom-request="handleBatchUpload"
      >
        <a-tooltip title="批量导入：上传 zip 压缩包，自动解压其中的全部 .md/.txt 文档">
          <a-button :disabled="!selectedKbId" :loading="uploading">
            <template #icon><FileZipOutlined /></template>
            批量导入
          </a-button>
        </a-tooltip>
      </a-upload>
    </div>
    <div v-if="queueStats" class="queue-stats">
      <span class="queue-stats__label">解析队列：</span>
      <a-tag color="blue">待处理 {{ queueStats.pending }}</a-tag>
      <a-tag color="processing">处理中 {{ queueStats.processing }}</a-tag>
      <a-tag v-if="(queueStats.dead ?? 0) > 0" color="red">死信 {{ queueStats.dead }}</a-tag>
      <a-tag v-else color="default">死信 0</a-tag>
    </div>

    <a-table :data-source="docs" :columns="columns" row-key="id" :loading="loading" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-space direction="vertical" style="width: 100%">
            <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
            <a-progress
              v-if="record.status === 'PROCESSING' || record.status === 'PENDING'"
              :percent="progressMap[record.id] ?? 30"
              size="small"
              status="active"
            />
            <span v-if="partInfo[record.id]" class="part-progress">
              已上传 {{ partInfo[record.id].done }} / {{ partInfo[record.id].total }} 块
            </span>
          </a-space>
        </template>
        <template v-else-if="column.key === 'stats'">
          <span class="stats">分块 {{ record.chunkCount ?? 0 }} / 实体 {{ record.entityCount ?? 0 }} / 关系 {{ record.relationCount ?? 0 }}</span>
        </template>
        <template v-else-if="column.key === 'error'">
          <a-tooltip v-if="record.errorMsg" :title="record.errorMsg">
            <span style="color: #ff4d4f">{{ record.errorMsg }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'action'">
          <ConfirmDelete title="确定删除该文档？" @confirm="handleDelete(record)" />
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
/**
 * 文档管理（管理员）：上传后异步解析（202 Accepted），
 * 通过 SSE 轮询解析进度并展示进度面板。
 */
import { UploadOutlined, FileZipOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import ConfirmDelete from '@/components/common/ConfirmDelete.vue'
import { list1 as apiListKbs } from '@/api/traceqa/zhishiku'
import { upload, delete2 as apiDeleteDoc, listByKb } from '@/api/traceqa/wendang'
import { getAuthHeaders } from '@/utils/request'
import type { KnowledgeBaseDTO, DocumentVO } from '@/utils/api-types'

const columns = [
  { title: '文件名', dataIndex: 'originalName', key: 'originalName' },
  { title: '类型', dataIndex: 'fileType', key: 'fileType' },
  { title: '大小', dataIndex: 'fileSize', key: 'fileSize' },
  { title: '状态', key: 'status' },
  { title: '解析统计', key: 'stats' },
  { title: '错误信息', key: 'error' },
  { title: '操作', key: 'action' }
]

const kbs = ref<KnowledgeBaseDTO[]>([])
const selectedKbId = ref<number | null>(null)
const docs = ref<DocumentVO[]>([])
const loading = ref(false)
const uploading = ref(false)
const progressMap = reactive<Record<number, number>>({})
/** 分块进度（SSE 推送） */
const partInfo = reactive<Record<number, { total: number; done: number }>>({})
/** 解析队列统计 */
const queueStats = ref<{ pending?: number; processing?: number; dead?: number } | null>(null)

async function loadQueueStats(): Promise<void> {
  try {
    const res = await fetch('/api/documents/queue/stats', { headers: getAuthHeaders() })
    const json = (await res.json()) as { code?: number; data?: { pending?: number; processing?: number; dead?: number } }
    if (json.code === 200 && json.data) {
      queueStats.value = json.data
    }
  } catch {
    // 忽略：队列统计不可用时静默
  }
}

async function load(): Promise<void> {
  const res = await apiListKbs()
  kbs.value = res.data ?? []
  if (kbs.value.length > 0 && selectedKbId.value === null) {
    selectedKbId.value = kbs.value[0].id
    await loadDocs()
  }
}

async function loadDocs(): Promise<void> {
  if (!selectedKbId.value) {
    docs.value = []
    return
  }
  loading.value = true
  try {
    const res = await listByKb({ knowledgeBaseId: selectedKbId.value })
    docs.value = res.data ?? []
  } finally {
    loading.value = false
  }
}

/** 上传前校验：仅允许 .md/.txt 文本格式 */
function handleBeforeUpload(file: File): boolean {
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!['md', 'txt'].includes(ext)) {
    message.error(`不支持的文件类型：${ext || '未知'}。PDF/PPT/Word/图片等请先用 MinerU 等工具转换为 Markdown 后再上传`)
    return false
  }
  return true
}

/** 自定义上传：调用后端接口并跟踪解析进度 */
async function handleUpload(options: Record<string, unknown>): Promise<void> {
  const file = options.file as File
  const kbId = selectedKbId.value
  if (!kbId || !file) {
    return
  }
  uploading.value = true
  try {
    const res = await upload({ knowledgeBaseId: kbId }, {}, file)
    const uploadData = res.data
    if (!uploadData) {
      throw new Error('上传失败')
    }
    message.success('上传成功，正在后台解析')
    progressMap[uploadData.documentId] = 10
    await loadDocs()
    // 通过 SSE 追踪解析进度
    await trackProgress(uploadData.documentId)
    // 解析结束后刷新列表
    await loadDocs()
  } catch (err) {
    message.error((err as Error).message || '上传失败')
  } finally {
    uploading.value = false
  }
}

/** 消费文档解析进度 SSE */
async function trackProgress(documentId: number): Promise<void> {
  try {
    const res = await fetch(`/api/documents/${documentId}/progress`, {
      headers: getAuthHeaders()
    })
    if (!res.body) {
      return
    }
    const reader = res.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      let sep: number
      while ((sep = buffer.indexOf('\n\n')) !== -1) {
        const block = buffer.slice(0, sep)
        buffer = buffer.slice(sep + 2)
        const dataLine = block
          .split('\n')
          .find((l) => l.startsWith('data:'))
        if (dataLine) {
          try {
            const payload = JSON.parse(dataLine.slice(5).trim())
            if (payload.progress !== undefined) {
              progressMap[payload.documentId] = payload.progress
            }
            if (payload.status === 'DONE' || payload.status === 'FAILED') {
              return
            }
          } catch {
            // 忽略解析失败的块
          }
        }
      }
    }
  } catch {
    // SSE 中断：忽略，下次刷新列表可见最终状态
  }
}

/** 批量导入：zip 压缩包，自动解压其中的 .md/.txt 文档 */
async function handleBatchUpload(options: Record<string, unknown>): Promise<void> {
  const file = options.file as File
  const kbId = selectedKbId.value
  if (!kbId || !file) {
    return
  }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file)
    const res = await fetch(`/api/documents/batch?knowledgeBaseId=${kbId}`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: fd
    })
    const json = (await res.json()) as {
      code?: number
      msg?: string
      data?: { successCount?: number; failedCount?: number; errors?: string[] }
    }
    if (json.code === 200 && json.data) {
      message.success(`批量导入完成：成功 ${json.data.successCount ?? 0}，失败 ${json.data.failedCount ?? 0}`)
      if (json.data.errors?.length) {
        message.warning(json.data.errors.slice(0, 5).join('；'))
      }
    } else {
      message.error(json.msg || '批量导入失败')
    }
    await loadDocs()
  } catch {
    message.error('批量导入失败，请确认上传的是 zip 压缩包')
  } finally {
    uploading.value = false
  }
}

async function handleDelete(record: DocumentVO): Promise<void> {
  try {
    await apiDeleteDoc({ id: record.id })
    message.success('已删除')
    await loadDocs()
  } catch (err) {
    message.error((err as Error).message || '删除失败')
  }
}

function statusColor(status?: string): string {
  switch (status) {
    case 'DONE':
      return 'green'
    case 'FAILED':
      return 'red'
    case 'PROCESSING':
      return 'blue'
    default:
      return 'default'
  }
}

function statusLabel(status?: string): string {
  switch (status) {
    case 'DONE':
      return '已完成'
    case 'FAILED':
      return '失败'
    case 'PROCESSING':
      return '解析中'
    default:
      return '等待解析'
  }
}

onMounted(() => {
  load()
  loadQueueStats()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.queue-stats {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.queue-stats__label {
  font-size: 13px;
  color: #4e5969;
}

.stats {
  font-size: 12px;
  color: #4e5969;
}
</style>

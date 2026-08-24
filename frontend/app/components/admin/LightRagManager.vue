<template>
  <div class="lightrag-manager">
    <a-alert v-if="!loading && !lightrag" type="warning" show-icon message="LightRAG 面板加载失败" style="margin-bottom: 12px" />

    <!-- 顶部操作条 -->
    <a-space style="margin-bottom: 12px">
      <a-button type="primary" :loading="webuiLoading" @click="openWebui">
        <template #icon><GlobalOutlined /></template>
        打开 LightRAG WebUI
      </a-button>
      <a-button :loading="refreshing" @click="load(true)">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </a-space>

    <template v-if="lightrag">
      <a-row :gutter="16">
        <a-col :span="12">
          <a-card size="small" title="流水线状态">
            <a-descriptions :column="1" size="small">
              <a-descriptions-item label="是否繁忙">
                <a-tag :color="pipeline?.busy ? 'processing' : 'green'">{{ pipeline?.busy ? '繁忙' : '空闲' }}</a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="当前任务">{{ pipeline?.job_name || '-' }}</a-descriptions-item>
              <a-descriptions-item label="批次进度">
                <a-progress
                  :percent="batchPercent"
                  size="small"
                  :status="pipeline?.busy ? 'active' : 'normal'"
                />
                <span class="lightrag-sub">第 {{ pipeline?.cur_batch ?? 0 }} / {{ pipeline?.batchs ?? 0 }} 批，待索引 {{ pipeline?.docs ?? 0 }} 篇</span>
              </a-descriptions-item>
              <a-descriptions-item label="最近消息">{{ pipeline?.latest_message || '-' }}</a-descriptions-item>
              <a-descriptions-item label="恢复状态">
                <a-tag v-if="pipeline?.recovery_required" color="red">需恢复（{{ pipeline?.recovery_kind || '未知' }}）</a-tag>
                <a-tag v-else color="green">正常</a-tag>
              </a-descriptions-item>
              <a-descriptions-item v-if="pipeline?.recovery_required && pipeline?.recovery_message" label="恢复说明">
                {{ pipeline?.recovery_message }}
              </a-descriptions-item>
            </a-descriptions>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card size="small" title="文档状态分布">
            <a-descriptions :column="1" size="small">
              <a-descriptions-item v-for="s in statusList" :key="s.status" :label="s.status">
                <a-tag :color="statusColor(s.status)">{{ s.count }}</a-tag>
              </a-descriptions-item>
              <a-descriptions-item v-if="!statusList.length">暂无数据</a-descriptions-item>
            </a-descriptions>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card size="small" title="模型信息">
            <div class="lightrag-sub">运行中模型（{{ runningModelList.length }}）</div>
            <template v-if="runningModelList.length">
              <a-tag v-for="m in runningModelList" :key="m" color="blue" class="label-chip">{{ m }}</a-tag>
            </template>
            <div v-else class="lightrag-sub">无</div>
            <div class="lightrag-sub" style="margin-top: 8px">可用模型数：{{ modelCount }}</div>
          </a-card>
        </a-col>
      </a-row>

      <a-row :gutter="16" style="margin-top: 16px">
        <a-col :span="12">
          <a-card size="small" title="图谱热门实体（Top 20）">
            <template v-if="popularLabels.length">
              <a-tag v-for="label in popularLabels.slice(0, 20)" :key="label" class="label-chip">{{ label }}</a-tag>
            </template>
            <a-empty v-else description="暂无实体" :image="false" />
          </a-card>
        </a-col>
        <a-col :span="12">
          <a-card size="small" title="运维操作">
            <div class="ops-row">
              <a-popconfirm title="确认重试 LightRAG 中解析失败的文档？" @confirm="runOp('reprocess-failed', '重试失败文档')">
                <a-button>重试失败文档</a-button>
              </a-popconfirm>
              <a-popconfirm title="确认清空 LightRAG 全部缓存？" @confirm="runOp('clear-cache', '清空缓存')">
                <a-button>清空缓存</a-button>
              </a-popconfirm>
              <a-popconfirm title="确认取消当前索引流水线？文档将被标记为失败" @confirm="runOp('cancel-pipeline', '取消流水线')">
                <a-button>取消流水线</a-button>
              </a-popconfirm>
              <a-popconfirm title="确认触发目录扫描？" @confirm="runOp('scan', '触发扫描')">
                <a-button>触发扫描</a-button>
              </a-popconfirm>
            </div>
            <div v-if="opMsg" class="lightrag-sub" style="margin-top: 8px">{{ opMsg }}</div>
          </a-card>
        </a-col>
      </a-row>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * LightRAG 管理页：展示引擎流水线状态、文档状态分布、模型信息、图谱实体，
 * 并提供运维操作与「打开 WebUI」入口。数据来自 /api/monitor/lightrag（后端带短缓存）。
 */
import { getAuthHeaders } from '@/utils/request'
import { GlobalOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'

interface PipelineStatus {
  busy?: boolean
  job_name?: string
  job_start?: string
  docs?: number
  batchs?: number
  cur_batch?: number
  latest_message?: string
  history_messages?: string[]
  recovery_required?: boolean
  recovery_kind?: string
  recovery_message?: string
}

interface OllamaModel {
  name?: string
  model?: string
}

interface LightragPanel {
  pipeline?: PipelineStatus
  statusCounts?: Record<string, number>
  models?: { models?: OllamaModel[] }
  runningModels?: { models?: OllamaModel[] }
  popularLabels?: string[]
}

const lightrag = ref<LightragPanel | null>(null)
const loading = ref(true)
const refreshing = ref(false)
const opMsg = ref('')
const webuiLoading = ref(false)

const pipeline = computed<PipelineStatus>(() => lightrag.value?.pipeline ?? {})

const batchPercent = computed<number>(() => {
  const batchs = pipeline.value.batchs ?? 0
  const cur = pipeline.value.cur_batch ?? 0
  if (!batchs) {
    return 0
  }
  return Math.round((cur / batchs) * 100)
})

const statusList = computed<{ status: string; count: number }[]>(() => {
  const counts = lightrag.value?.statusCounts ?? {}
  return Object.entries(counts)
    .map(([status, count]) => ({ status, count: Number(count) ?? 0 }))
    .sort((a, b) => statusOrder(a.status) - statusOrder(b.status))
})

const runningModelList = computed<string[]>(() => {
  const models = lightrag.value?.runningModels?.models ?? []
  return models.map((m) => m.name || m.model || '未知').filter(Boolean)
})

const modelCount = computed<number>(() => lightrag.value?.models?.models?.length ?? 0)

const popularLabels = computed<string[]>(() => lightrag.value?.popularLabels ?? [])

function statusOrder(status: string): number {
  const order: Record<string, number> = { PENDING: 0, PREPROCESSED: 1, PROCESSING: 2, PROCESSED: 3, FAILED: 4 }
  return order[status] ?? 99
}

function statusColor(status: string): string {
  switch (status) {
    case 'PROCESSED':
      return 'green'
    case 'FAILED':
      return 'red'
    case 'PROCESSING':
      return 'blue'
    case 'PENDING':
      return 'orange'
    default:
      return 'default'
  }
}

async function load(manual = false): Promise<void> {
  if (manual) {
    refreshing.value = true
  }
  try {
    const res = await fetch('/api/monitor/lightrag', { headers: getAuthHeaders() })
    const json = (await res.json()) as { code?: number; data?: LightragPanel }
    if (json.code === 200 && json.data) {
      lightrag.value = json.data
    }
  } catch {
    // 忽略，保留上次数据
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

/** 获取 LightRAG WebUI 访问会话并打开（仅管理员） */
async function openWebui(): Promise<void> {
  webuiLoading.value = true
  try {
    const res = await fetch('/api/monitor/lightrag/webui-session', {
      method: 'POST',
      headers: getAuthHeaders()
    })
    const json = (await res.json()) as { code?: number; msg?: string }
    if (json.code !== 200) {
      throw new Error(json.msg || '获取访问会话失败')
    }
    window.open('/lightrag-webui/webui/', '_blank', 'noopener')
  } catch (err) {
    message.error((err as Error).message || '打开 WebUI 失败')
  } finally {
    webuiLoading.value = false
  }
}

/** 执行 LightRAG 运维操作（带二次确认） */
async function runOp(action: string, label: string): Promise<void> {
  opMsg.value = ''
  try {
    const res = await fetch(`/api/monitor/lightrag/${action}`, {
      method: 'POST',
      headers: { ...getAuthHeaders(), 'Content-Type': 'application/json' }
    })
    const json = (await res.json()) as { code?: number; msg?: string; data?: { message?: string; status?: string } }
    if (json.code === 200) {
      const text = json.data?.message || json.data?.status || '操作已提交'
      opMsg.value = `${label}：${text}`
      message.success(`${label}成功`)
      await load()
    } else {
      throw new Error(json.msg || '操作失败')
    }
  } catch (err) {
    opMsg.value = `${label}失败：${(err as Error).message}`
    message.error(opMsg.value)
  }
}

onMounted(() => {
  load()
  // 面板数据由后端带短缓存，自动轮询保持最新
  useIntervalFn(() => load(), 10000)
})
</script>

<style scoped>
.lightrag-sub {
  font-size: 12px;
  color: #86909c;
}
.label-chip {
  margin: 2px;
}
.ops-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
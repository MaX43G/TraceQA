<template>
  <div class="monitor-panel">
    <a-alert v-if="!loading && !data" type="error" show-icon message="监控数据加载失败" style="margin-bottom: 12px" />

    <template v-if="data">
      <!-- 概览卡片 -->
      <a-row :gutter="16">
        <a-col :span="6">
          <a-card size="small" class="metric-card">
            <div class="metric-label">总请求数</div>
            <div class="metric-value">{{ data.totalRequests }}</div>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card size="small" class="metric-card">
            <div class="metric-label">平均延迟 (ms)</div>
            <div class="metric-value">{{ data.avgLatencyMs }}</div>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card size="small" class="metric-card">
            <div class="metric-label">缓存命中率</div>
            <div class="metric-value">{{ data.cacheHitRate }}%</div>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card size="small" class="metric-card">
            <div class="metric-label">熔断状态</div>
            <a-tag :color="cbColor">{{ data.circuitBreaker }}</a-tag>
          </a-card>
        </a-col>
      </a-row>

      <!-- 队列 + 会话 -->
      <a-row :gutter="16" style="margin-top: 16px">
        <a-col :span="8">
          <a-card size="small" title="解析队列">
            <a-descriptions :column="1" size="small">
              <a-descriptions-item label="待处理">{{ data.queue?.pending }}</a-descriptions-item>
              <a-descriptions-item label="处理中">{{ data.queue?.processing }}</a-descriptions-item>
              <a-descriptions-item label="死信">
                <a-tag :color="(data.queue?.dead ?? 0) > 0 ? 'red' : 'default'">{{ data.queue?.dead }}</a-tag>
              </a-descriptions-item>
            </a-descriptions>
          </a-card>
        </a-col>
        <a-col :span="8">
          <a-card size="small" title="缓存统计">
            <a-descriptions :column="1" size="small">
              <a-descriptions-item label="命中">{{ data.cacheHits }}</a-descriptions-item>
              <a-descriptions-item label="未命中">{{ data.cacheMisses }}</a-descriptions-item>
            </a-descriptions>
          </a-card>
        </a-col>
        <a-col :span="8">
          <a-card size="small" title="在线会话（约）">
            <div class="metric-value">{{ data.activeSessions }}</div>
          </a-card>
        </a-col>
      </a-row>

      <!-- Top 接口 -->
      <a-card size="small" title="Top 请求接口" style="margin-top: 16px">
        <a-table
          :data-source="topPaths"
          :columns="pathColumns"
          row-key="path"
          size="small"
          :pagination="false"
        />
      </a-card>

      <!-- 最近异常 -->
      <a-card size="small" title="最近异常日志" style="margin-top: 16px">
        <template v-if="data.recentErrors?.length">
          <ul class="error-list">
            <li v-for="(e, i) in data.recentErrors" :key="i">{{ e }}</li>
          </ul>
        </template>
        <a-empty v-else description="暂无异常" />
      </a-card>
    </template>

    <!-- LightRAG 引擎面板 -->
    <a-divider orientation="left">LightRAG 引擎</a-divider>
    <a-alert v-if="!lightragLoading && !lightrag" type="warning" show-icon message="LightRAG 面板加载失败" style="margin-bottom: 12px" />
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
 * 系统监控面板：展示请求量/延迟/缓存命中/熔断/队列/异常等运行指标，以及
 * LightRAG 引擎面板（流水线状态、文档状态分布、模型信息、图谱实体、运维操作）。
 * 每 5 秒自动刷新；LightRAG 面板由后端带短缓存。
 */
import { getAuthHeaders } from '@/utils/request'
import { message } from 'ant-design-vue'

interface MonitorData {
  totalRequests?: number
  avgLatencyMs?: number
  cacheHits?: number
  cacheMisses?: number
  cacheHitRate?: number
  circuitBreaker?: string
  activeSessions?: number
  queue?: { pending?: number; processing?: number; dead?: number }
  recentErrors?: string[]
}

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

const data = ref<MonitorData | null>(null)
const loading = ref(true)

const lightrag = ref<LightragPanel | null>(null)
const lightragLoading = ref(true)
const opMsg = ref('')

const topPaths = computed<{ path: string; count: number }[]>(() => {
  const paths = data.value?.topPaths ?? {}
  return Object.entries(paths).map(([path, count]) => ({ path, count }))
})
const pathColumns = [
  { title: '接口', dataIndex: 'path' },
  { title: '请求数', dataIndex: 'count' }
]

const cbColor = computed<string>(() => {
  switch (data.value?.circuitBreaker) {
    case 'CLOSED':
      return 'green'
    case 'HALF_OPEN':
      return 'orange'
    case 'OPEN':
      return 'red'
    default:
      return 'default'
  }
})

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

async function load(): Promise<void> {
  try {
    const res = await fetch('/api/monitor', { headers: getAuthHeaders() })
    const json = (await res.json()) as { code?: number; data?: MonitorData }
    if (json.code === 200 && json.data) {
      data.value = json.data
    }
  } catch {
    // 忽略，保留上次数据
  } finally {
    loading.value = false
  }
}

async function loadLightrag(): Promise<void> {
  try {
    const res = await fetch('/api/monitor/lightrag', { headers: getAuthHeaders() })
    const json = (await res.json()) as { code?: number; data?: LightragPanel }
    if (json.code === 200 && json.data) {
      lightrag.value = json.data
    }
  } catch {
    // 忽略，保留上次数据
  } finally {
    lightragLoading.value = false
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
      await loadLightrag()
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
  loadLightrag()
  useIntervalFn(() => {
    load()
    loadLightrag()
  }, 5000)
})
</script>

<style scoped>
.metric-card {
  text-align: center;
}
.metric-label {
  color: #86909c;
  font-size: 13px;
  margin-bottom: 6px;
}
.metric-value {
  font-size: 24px;
  font-weight: 600;
  color: #1f2329;
}
.error-list {
  margin: 0;
  padding-left: 18px;
  color: #ff4d4f;
  font-size: 12px;
  max-height: 200px;
  overflow-y: auto;
}
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
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
  </div>
</template>

<script setup lang="ts">
/**
 * 系统监控面板：展示请求量/延迟/缓存命中/熔断/队列/异常等后端运行指标（管理员）。
 * LightRAG 引擎相关信息见独立的「LightRAG 管理」页（LightRagManager）。
 * 每 5 秒自动刷新。
 */
import { getAuthHeaders } from '@/utils/request'

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

const data = ref<MonitorData | null>(null)
const loading = ref(true)

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

onMounted(() => {
  load()
  useIntervalFn(() => load(), 5000)
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
</style>
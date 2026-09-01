<template>
  <div class="monitor-panel">
    <a-alert v-if="!loading && !data" type="error" show-icon message="监控数据加载失败" style="margin-bottom: 12px"/>

    <template v-if="data">
      <a-space style="margin-bottom: 12px">
        <a-button type="primary" :loading="grafanaLoading" @click="openGrafana">
          <template #icon>
            <BarChartOutlined/>
          </template>
          打开 Grafana 大盘
        </a-button>
        <a-button :loading="prometheusLoading" @click="openPrometheus">
          <template #icon>
            <LineChartOutlined/>
          </template>
          打开 Prometheus
        </a-button>
      </a-space>

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

      <!-- 可视化图表 -->
      <a-row :gutter="16" style="margin-top: 16px">
        <a-col :xs="24" :sm="12" :md="6">
          <a-card size="small" title="HTTP 状态分布">
            <VChart :option="statusPieOption" height="240px"/>
          </a-card>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <a-card size="small" title="请求方法分布">
            <VChart :option="methodBarOption" height="240px"/>
          </a-card>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <a-card size="small" title="Top 请求接口">
            <VChart :option="topPathsBarOption" height="240px"/>
          </a-card>
        </a-col>
        <a-col :xs="24" :sm="12" :md="6">
          <a-card size="small" title="缓存命中率">
            <VChart :option="cacheGaugeOption" height="240px"/>
          </a-card>
        </a-col>
      </a-row>

      <!-- 延迟分位 + 会话/运行时 -->
      <a-row :gutter="16" style="margin-top: 16px">
        <a-col :xs="24" :md="12">
          <a-card size="small" title="延迟分位 (ms)">
            <a-descriptions :column="1" size="small">
              <a-descriptions-item label="P50">{{ data.latencyPercentiles?.p50 ?? 0 }}</a-descriptions-item>
              <a-descriptions-item label="P95">{{ data.latencyPercentiles?.p95 ?? 0 }}</a-descriptions-item>
              <a-descriptions-item label="P99">{{ data.latencyPercentiles?.p99 ?? 0 }}</a-descriptions-item>
            </a-descriptions>
          </a-card>
        </a-col>
        <a-col :xs="24" :md="12">
          <a-card size="small" title="JVM 运行时">
            <a-descriptions :column="1" size="small">
              <a-descriptions-item label="运行时长">{{ fmtUptime(runtime?.uptimeSeconds) }}</a-descriptions-item>
              <a-descriptions-item label="堆内存">{{ runtime?.heapUsedMb }} / {{ runtime?.heapMaxMb }} MB
              </a-descriptions-item>
              <a-descriptions-item label="线程数">{{ runtime?.threads }}</a-descriptions-item>
            </a-descriptions>
          </a-card>
        </a-col>
      </a-row>

      <!-- 缓存 + 会话 -->
      <a-row :gutter="16" style="margin-top: 16px">
        <a-col :span="12">
          <a-card size="small" title="缓存统计">
            <a-descriptions :column="1" size="small">
              <a-descriptions-item label="命中">{{ data.cacheHits }}</a-descriptions-item>
              <a-descriptions-item label="未命中">{{ data.cacheMisses }}</a-descriptions-item>
            </a-descriptions>
          </a-card>
        </a-col>
        <a-col :span="12">
          <a-card size="small" title="在线会话（约）">
            <div class="metric-value">{{ data.activeSessions }}</div>
          </a-card>
        </a-col>
      </a-row>

      <!-- Top 接口 + 路径错误率 -->
      <a-row :gutter="16" style="margin-top: 16px">
        <a-col :span="12">
          <a-card size="small" title="Top 请求接口">
            <a-table :data-source="topPaths" :columns="pathColumns" row-key="path" size="small" :pagination="false"/>
          </a-card>
        </a-col>
        <a-col :span="12">
          <a-card size="small" title="接口错误次数 (Top 10)">
            <a-table :data-source="pathErrorRows" :columns="errorColumns" row-key="path" size="small"
                     :pagination="false"/>
          </a-card>
        </a-col>
      </a-row>

      <!-- 慢请求 -->
      <a-card size="small" title="慢请求（>= 2000ms，最近）" style="margin-top: 16px">
        <a-table :data-source="slowRows" :columns="slowColumns" row-key="time" size="small" :pagination="false"/>
      </a-card>

      <!-- 最近异常 -->
      <a-card size="small" title="最近异常日志" style="margin-top: 16px">
        <template v-if="data.recentErrors?.length">
          <ul class="error-list">
            <li v-for="(e, i) in data.recentErrors" :key="i">{{ e }}</li>
          </ul>
        </template>
        <a-empty v-else description="暂无异常"/>
      </a-card>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 系统监控面板：展示请求量/延迟分位/状态分布/JVM/慢请求/缓存/熔断/会话/异常等后端运行指标（管理员）。
 * LightRAG 引擎相关信息见独立的「LightRAG 管理」页（LightRagManager）。
 * 每分钟自动刷新。
 */
import {getAuthHeaders} from '@/utils/request'
import {BarChartOutlined, LineChartOutlined} from '@ant-design/icons-vue'
import { useIntervalFn } from '@vueuse/core'
import VChart from '@/components/common/VChart.vue'

interface SlowRequest {
  path?: string
  method?: string
  costMs?: number
  status?: number
  time?: string
}

interface MonitorData {
  totalRequests?: number
  avgLatencyMs?: number
  cacheHits?: number
  cacheMisses?: number
  cacheHitRate?: number
  circuitBreaker?: string
  activeSessions?: number
  latencyPercentiles?: { p50?: number; p95?: number; p99?: number }
  statusCounts?: Record<string, number>
  methodCounts?: Record<string, number>
  topPaths?: Record<string, number>
  pathErrors?: Record<string, number>
  slowRequests?: SlowRequest[]
  runtime?: { uptimeSeconds?: number; heapUsedMb?: number; heapMaxMb?: number; threads?: number }
  recentErrors?: string[]
}

const data = ref<MonitorData | null>(null)
const loading = ref(true)

const topPaths = computed<{ path: string; count: number }[]>(() => {
  const paths = data.value?.topPaths ?? {}
  return Object.entries(paths).map(([path, count]) => ({path, count}))
})
const pathColumns = [
  {title: '接口', dataIndex: 'path'},
  {title: '请求数', dataIndex: 'count'}
]

const pathErrorRows = computed<{ path: string; count: number }[]>(() => {
  const errors = data.value?.pathErrors ?? {}
  return Object.entries(errors).map(([path, count]) => ({path, count}))
})
const errorColumns = [
  {title: '接口', dataIndex: 'path'},
  {title: '错误数', dataIndex: 'count'}
]

const slowRows = computed<SlowRequest[]>(() => data.value?.slowRequests ?? [])
const slowColumns = [
  {title: '时间', dataIndex: 'time'},
  {title: '方法', dataIndex: 'method'},
  {title: '接口', dataIndex: 'path'},
  {title: '状态', dataIndex: 'status'},
  {title: '耗时 (ms)', dataIndex: 'costMs'}
]

const statusCounts = computed<Record<string, number>>(() => data.value?.statusCounts ?? {})

const runtime = computed(() => data.value?.runtime)

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

function fmtUptime(seconds?: number): string {
  if (!seconds) {
    return '-'
  }
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return d > 0 ? `${d}天 ${h}时 ${m}分` : `${h}时 ${m}分`
}

// ---- ECharts 图表配置 ----
const STATUS_COLORS: Record<string, string> = {'2xx': '#52c41a', '3xx': '#1677ff', '4xx': '#fa8c16', '5xx': '#ff4d4f'}

const statusPieOption = computed<object>(() => {
  const counts = statusCounts.value
  return {
    tooltip: {trigger: 'item'},
    legend: {bottom: 0, textStyle: {fontSize: 11}},
    series: [
      {
        type: 'pie',
        radius: ['42%', '70%'],
        itemStyle: {borderRadius: 6, borderColor: '#fff', borderWidth: 2},
        label: {show: true, formatter: '{b}: {c}'},
        data: Object.entries(counts).map(([k, v]) => ({
          name: k,
          value: v,
          itemStyle: {color: STATUS_COLORS[k] || '#999'}
        }))
      }
    ]
  }
})

const methodBarOption = computed<object>(() => {
  const m = data.value?.methodCounts ?? {}
  return {
    tooltip: {trigger: 'axis'},
    grid: {left: 8, right: 8, bottom: 8, top: 30, containLabel: true},
    xAxis: {type: 'category', data: Object.keys(m)},
    yAxis: {type: 'value', minInterval: 1},
    series: [{
      type: 'bar',
      data: Object.values(m),
      itemStyle: {color: '#1677ff', borderRadius: [4, 4, 0, 0]},
      barMaxWidth: 32
    }]
  }
})

const topPathsBarOption = computed<object>(() => {
  const rows = topPaths.value.slice(0, 8)
  return {
    tooltip: {trigger: 'axis', axisPointer: {type: 'shadow'}},
    grid: {left: 8, right: 8, bottom: 8, top: 30, containLabel: true},
    xAxis: {type: 'value', minInterval: 1},
    yAxis: {type: 'category', data: rows.map((r) => r.path).reverse()},
    series: [{
      type: 'bar',
      data: rows.map((r) => r.count).reverse(),
      itemStyle: {color: '#722ed1', borderRadius: [0, 4, 4, 0]},
      barMaxWidth: 18
    }]
  }
})

const cacheGaugeOption = computed<object>(() => {
  const rate = data.value?.cacheHitRate ?? 0
  return {
    series: [
      {
        type: 'gauge',
        startAngle: 210,
        endAngle: -30,
        min: 0,
        max: 100,
        progress: {show: true, width: 14, itemStyle: {color: '#13c2c2'}},
        axisLine: {lineStyle: {width: 14}},
        axisLabel: {show: false},
        pointer: {show: false},
        detail: {valueAnimation: true, formatter: '{value}%', fontSize: 18, color: '#1f2329'},
        data: [{value: rate}]
      }
    ]
  }
})

/** 打开 Grafana 大盘（经后端代理 /grafana/**，需先获取可观测性会话 Cookie） */
const grafanaLoading = ref(false)

async function openGrafana(): Promise<void> {
  grafanaLoading.value = true
  try {
    await fetch('/api/monitor/observability/session', {method: 'POST', headers: getAuthHeaders()})
    window.open('/grafana/', '_blank', 'noopener')
  } catch {
    // 忽略
  } finally {
    grafanaLoading.value = false
  }
}

/** 打开 Prometheus（经后端代理 /prometheus/**） */
const prometheusLoading = ref(false)

async function openPrometheus(): Promise<void> {
  prometheusLoading.value = true
  try {
    await fetch('/api/monitor/observability/session', {method: 'POST', headers: getAuthHeaders()})
    window.open('/prometheus/', '_blank', 'noopener')
  } catch {
    // 忽略
  } finally {
    prometheusLoading.value = false
  }
}

async function load(): Promise<void> {
  try {
    const res = await fetch('/api/monitor', {headers: getAuthHeaders()})
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
  useIntervalFn(() => load(), 60000)
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
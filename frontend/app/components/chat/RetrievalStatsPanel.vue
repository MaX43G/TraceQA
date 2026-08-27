<template>
  <div class="stats-panel">
    <div class="stats-panel__header">
      <a-space size="small">
        <PieChartOutlined />
        <span>检索分析</span>
        <a-tag color="purple">{{ stats.elapsedMs ?? 0 }} ms</a-tag>
      </a-space>
    </div>

    <div class="stats-panel__paths">
      <div class="stat-item">
        <div class="stat-item__label">图谱</div>
        <a-tag :color="hitsColor(stats.graphHits)">{{ stats.graphHits ?? 0 }}</a-tag>
      </div>
      <div class="stat-item">
        <div class="stat-item__label">向量</div>
        <a-tag :color="hitsColor(stats.vectorHits)">{{ stats.vectorHits ?? 0 }}</a-tag>
      </div>
      <div class="stat-item">
        <div class="stat-item__label">关键词</div>
        <a-tag :color="hitsColor(stats.keywordHits)">{{ stats.keywordHits ?? 0 }}</a-tag>
      </div>
      <div class="stat-item">
        <div class="stat-item__label">融合结果</div>
        <a-tag :color="hitsColor(stats.fusedCount)">{{ stats.fusedCount ?? 0 }}</a-tag>
      </div>
    </div>

    <!-- 三路命中分布 -->
    <div class="stats-panel__chart">
      <VChart :option="pathDonutOption" height="150px" />
    </div>

    <template v-if="sourceDocs.length">
      <div class="stats-panel__src-title">来源文档分布</div>
      <div class="stats-panel__src">
        <a-tooltip v-for="s in sourceDocs" :key="s.file" :title="`${s.file}：${s.count} 条片段`">
          <span class="src-chip" :style="{ width: srcWidth(s.count) }">{{ s.file }}</span>
        </a-tooltip>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 检索可解释性面板：展示三路命中数、来源文档分布与耗时。
 */
import { PieChartOutlined } from '@ant-design/icons-vue'
import VChart from '@/components/common/VChart.vue'
import type { RetrievalStats } from '@/composables/useChatStream'

const props = defineProps<{
  stats: RetrievalStats
}>()

/** 三路命中分布环形图 */
const pathDonutOption = computed<object>(() => {
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { fontSize: 11 } },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['50%', '42%'],
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 1 },
        label: { formatter: '{b}: {c}' },
        data: [
          { name: '图谱', value: props.stats.graphHits ?? 0, itemStyle: { color: '#722ed1' } },
          { name: '向量', value: props.stats.vectorHits ?? 0, itemStyle: { color: '#1677ff' } },
          { name: '关键词', value: props.stats.keywordHits ?? 0, itemStyle: { color: '#13c2c2' } }
        ]
      }
    ]
  }
})

const sourceDocs = computed<{ file: string; count: number }[]>(() => {
  const docs = props.stats.sourceDocs ?? {}
  return Object.entries(docs)
    .map(([file, count]) => ({ file, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 6)
})

const maxCount = computed<number>(() => Math.max(1, ...sourceDocs.value.map((s) => s.count)))

function hitsColor(count?: number): string {
  return (count ?? 0) > 0 ? 'green' : 'default'
}

function srcWidth(count: number): string {
  const pct = Math.max(18, Math.round((count / maxCount.value) * 100))
  return `${pct}%`
}
</script>

<style scoped>
.stats-panel {
  background: #f6f7fb;
  border: 1px solid #eef0f6;
  border-radius: 8px;
  padding: 8px 12px;
  margin-bottom: 8px;
}
.stats-panel__header {
  color: #4e5969;
  font-size: 13px;
  margin-bottom: 8px;
}
.stats-panel__paths {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.stat-item__label {
  font-size: 12px;
  color: #86909c;
}
.stats-panel__chart {
  margin-top: 6px;
}
.stats-panel__src-title {
  margin-top: 8px;
  font-size: 12px;
  color: #86909c;
}
.stats-panel__src {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 4px;
}
.src-chip {
  font-size: 12px;
  color: #1f2329;
  background: #e8f1ff;
  border: 1px solid #cfe4ff;
  border-radius: 4px;
  padding: 2px 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  cursor: default;
}
</style>
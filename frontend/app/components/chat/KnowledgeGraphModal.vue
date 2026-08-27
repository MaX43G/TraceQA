<template>
  <a-modal v-model:open="open" title="知识图谱路径" :footer="null" width="760" :body-style="{ padding: '16px' }">
    <div v-if="loading" class="kg-state">正在加载知识图谱…</div>
    <div v-else-if="!nodes.length" class="kg-state">暂无可可视化的知识图谱路径。</div>
    <template v-else>
      <div class="kg-legend">
        <span
          v-for="item in legendList"
          :key="item.type"
          class="kg-legend__item"
        >
          <i class="kg-legend__dot" :style="{ background: item.color }" />
          {{ item.type }}
        </span>
      </div>
      <div ref="container" class="kg-container" />
    </template>
  </a-modal>
</template>

<script setup lang="ts">
/**
 * 知识图谱路径可视化弹窗：用用户问题作为查询，调用后端 /api/graph/viz 获取子图，
 */
import cytoscape, { type Core } from 'cytoscape'
import { getAuthHeaders } from '@/utils/request'

interface GraphNode {
  id: string
  label?: string
  type?: string
}
interface GraphEdge {
  source: string
  target: string
  label?: string
}

const open = defineModel<boolean>('open', { default: false })

const props = defineProps<{
  /** 查询文本（通常是用户的问题，用于提取实体词） */
  query?: string
}>()

const nodes = ref<GraphNode[]>([])
const edges = ref<GraphEdge[]>([])
const loading = ref(false)
const container = ref<HTMLElement | null>(null)
let cy: Core | null = null

/** 实体类型 → 颜色的固定映射，未知类型按顺序取调色板 */
const TYPE_COLORS: Record<string, string> = {
  algorithm: '#eb2f96',
  concept: '#13c2c2',
  method: '#fa8c16',
  entity: '#1677ff',
  relation: '#52c41a',
  person: '#722ed1',
  organization: '#2f54eb',
  location: '#f5222d'
}
const PALETTE = ['#1677ff', '#722ed1', '#eb2f96', '#fa8c16', '#13c2c2', '#52c41a', '#2f54eb', '#f5222d']

/** 图例：按实体类型去重并分配颜色 */
const legendList = computed<{ type: string; color: string }[]>(() => {
  const seen = new Map<string, string>()
  let idx = 0
  for (const n of nodes.value) {
    const type = (n.type || '').trim() || '未知'
    if (!seen.has(type)) {
      seen.set(type, TYPE_COLORS[type] || PALETTE[idx % PALETTE.length])
      idx++
    }
  }
  return Array.from(seen, ([type, color]) => ({ type, color }))
})

/** 节点类型 → 颜色映射（用于 cytoscape data.color） */
function nodeColor(type?: string): string {
  const t = (type || '').trim()
  if (TYPE_COLORS[t]) {
    return TYPE_COLORS[t]
  }
  const list = legendList.value
  const found = list.find((l) => l.type === t)
  return found ? found.color : PALETTE[0]
}

async function load(): Promise<void> {
  loading.value = true
  nodes.value = []
  edges.value = []
  try {
    const res = await fetch('/api/graph/viz', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify({ query: props.query || '' })
    })
    const json = (await res.json()) as { code?: number; data?: { nodes?: GraphNode[]; edges?: GraphEdge[] } }
    if (json.code === 200 && json.data) {
      nodes.value = json.data.nodes ?? []
      edges.value = json.data.edges ?? []
    }
  } catch {
    nodes.value = []
  } finally {
    loading.value = false
    if (nodes.value.length) {
      await nextTick()
      render()
    }
  }
}

function render(): void {
  if (!container.value) {
    return
  }
  cy?.destroy()
  const elements = {
    nodes: nodes.value.map((n) => ({
      data: { id: n.id, label: n.label || n.id, color: nodeColor(n.type) }
    })),
    edges: edges.value.map((e, i) => ({
      data: { id: `e${i}`, source: e.source, target: e.target }
    }))
  }
  cy = cytoscape({
    container: container.value,
    elements,
    style: [
      {
        selector: 'node',
        style: {
          'background-color': 'data(color)',
          'border-color': '#ffffff',
          'border-width': 1.5,
          label: 'data(label)',
          color: '#1f2329',
          'font-size': 10,
          'text-valign': 'center',
          'text-halign': 'center',
          width: 'label',
          height: 'label',
          padding: '10px',
          shape: 'round-rectangle',
          'text-wrap': 'wrap',
          'text-max-width': '90px',
          'text-overflow-wrap': 'anywhere'
        }
      },
      {
        selector: 'node:selected',
        style: {
          'border-color': '#ffd666',
          'border-width': 3,
          color: '#1f2329'
        }
      },
      {
        selector: 'edge',
        style: {
          width: 1.2,
          'line-color': '#b8c7e8',
          'target-arrow-color': '#b8c7e8',
          'target-arrow-shape': 'triangle',
          'curve-style': 'bezier'
        }
      }
    ],
    layout: {
      name: 'cose',
      animate: true,
      fit: true,
      padding: 20,
      nodeRepulsion: () => 6000
    },
    minZoom: 0.2,
    maxZoom: 2.5
  })
}

watch(
  () => [open.value, props.query],
  ([isOpen]) => {
    if (isOpen) {
      load()
    }
  },
  { deep: true }
)

onBeforeUnmount(() => {
  cy?.destroy()
})
</script>

<style scoped>
.kg-state {
  text-align: center;
  color: #86909c;
  padding: 40px 0;
}
.kg-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin-bottom: 10px;
}
.kg-legend__item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #4e5969;
}
.kg-legend__dot {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  display: inline-block;
}
.kg-container {
  width: 100%;
  height: 420px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fafcff;
}
</style>
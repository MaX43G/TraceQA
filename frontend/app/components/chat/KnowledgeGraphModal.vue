<template>
  <a-modal v-model:open="open" title="知识图谱路径" :footer="null" width="660" :body-style="{ padding: '16px' }">
    <div v-if="loading" class="kg-state">正在加载知识图谱…</div>
    <div v-else-if="!nodes.length" class="kg-state">该回答暂无可可视化的知识图谱路径（资料库中未命中相关实体）。</div>
    <svg v-else :viewBox="`0 0 ${W} ${H}`" class="kg-svg">
      <!-- 边 -->
      <line
        v-for="(e, i) in edges"
        :key="`e${i}`"
        :x1="pos(e.source).x" :y1="pos(e.source).y"
        :x2="pos(e.target).x" :y2="pos(e.target).y"
        class="kg-edge"
      />
      <!-- 节点 -->
      <g
        v-for="n in nodes"
        :key="n.id"
        :transform="`translate(${pos(n.id).x},${pos(n.id).y})`"
        class="kg-node"
      >
        <circle :r="NODE_R" class="kg-node-circle" />
        <text text-anchor="middle" dy="5" class="kg-node-label">{{ n.label }}</text>
      </g>
    </svg>
  </a-modal>
</template>

<script setup lang="ts">
/**
 * 知识图谱路径可视化弹窗：根据回答文本粗提取实体词，调用后端 /api/graph/viz 获取子图，
 * 用 SVG 以圆形布局渲染节点与边。
 */
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
  /** 用于粗提取实体词的文本（通常是回答内容） */
  content?: string
}>()

const W = 600
const H = 420
const NODE_R = 24

const nodes = ref<GraphNode[]>([])
const edges = ref<GraphEdge[]>([])
const loading = ref(false)

/** 节点 id -> 圆形布局坐标 */
const posMap = ref<Record<string, { x: number; y: number }>>({})

function pos(id: string): { x: number; y: number } {
  return posMap.value[id] ?? { x: W / 2, y: H / 2 }
}

/** 从文本粗提取候选实体词 */
function extractTerms(text: string): string[] {
  if (!text) {
    return []
  }
  const seen = new Set<string>()
  const out: string[] = []
  const parts = text.split(/[\s，。；、？！：:（）()\[\]{}<>《》"'“”‘’—…~`@#$%^&*+=|/\\]+/)
  for (const part of parts) {
    const t = part.trim()
    if (t.length >= 2 && t.length <= 16 && !seen.has(t)) {
      seen.add(t)
      out.push(t)
    }
    if (out.length >= 6) {
      break
    }
  }
  return out
}

/** 圆形布局：把节点均匀分布到圆周上 */
function layout(): void {
  const count = nodes.value.length
  if (!count) {
    return
  }
  const cx = W / 2
  const cy = H / 2
  const radius = Math.min(W, H) / 2 - 60
  const map: Record<string, { x: number; y: number }> = {}
  nodes.value.forEach((n, i) => {
    const angle = (2 * Math.PI * i) / count - Math.PI / 2
    map[n.id] = {
      x: cx + radius * Math.cos(angle),
      y: cy + radius * Math.sin(angle)
    }
  })
  posMap.value = map
}

watch(
  () => [open.value, props.content],
  async ([isOpen, content]) => {
    if (!isOpen) {
      return
    }
    loading.value = true
    nodes.value = []
    edges.value = []
    try {
      const terms = extractTerms((content as string) || '')
      const res = await fetch('/api/graph/viz', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
        body: JSON.stringify({ terms })
      })
      const json = (await res.json()) as { code?: number; data?: { nodes?: GraphNode[]; edges?: GraphEdge[] } }
      if (json.code === 200 && json.data) {
        nodes.value = json.data.nodes ?? []
        edges.value = json.data.edges ?? []
        layout()
      }
    } catch {
      nodes.value = []
    } finally {
      loading.value = false
    }
  },
  { deep: true }
)
</script>

<style scoped>
.kg-state {
  text-align: center;
  color: #86909c;
  padding: 40px 0;
}
.kg-svg {
  width: 100%;
  height: auto;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fafcff;
}
.kg-edge {
  stroke: #b8c7e8;
  stroke-width: 1.2;
}
.kg-node-circle {
  fill: #e6f4ff;
  stroke: #1677ff;
  stroke-width: 1.5;
}
.kg-node:hover .kg-node-circle {
  fill: #1677ff;
}
.kg-node-label {
  font-size: 11px;
  fill: #1f2329;
  pointer-events: none;
}
.kg-node:hover .kg-node-label {
  fill: #fff;
}
</style>
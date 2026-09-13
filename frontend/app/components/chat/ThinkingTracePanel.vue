<template>
  <div class="thinking-panel">
    <div class="thinking-panel__header">
      <a-space size="small">
        <SyncOutlined v-if="anyRunning" spin style="color: #1677ff"/>
        <BulbOutlined v-else style="color: #faad14"/>
        <span>Agent 工作流</span>
        <a-tag v-if="nodes.length > 0" color="blue">{{ nodes.length }} 个节点</a-tag>
        <a-tag v-if="totalCostMs > 0" color="green">{{ formatTotalCost }}</a-tag>
      </a-space>
    </div>

    <!-- 状态图流转：横向流程图（按节点到达顺序显示） -->
    <div v-if="nodes.length > 0" class="flow">
      <template v-for="(node, i) in nodes" :key="node.stage">
        <div class="flow-node" :class="getNodeClass(node)" :title="getNodeTitle(node)">
          <div class="flow-node__dot">
            <LoadingOutlined v-if="node.status === 'running'" spin/>
            <CheckCircleFilled v-else-if="node.status === 'done'"/>
            <CloseCircleFilled v-else-if="node.status === 'failed'"/>
            <EllipsisOutlined v-else/>
          </div>
          <div class="flow-node__label">{{ node.stage }}</div>
          <div v-if="node.costMs != null && node.status === 'done'" class="flow-node__cost">
            {{ formatCost(node.costMs) }}
          </div>
          <div v-if="getNodeDetail(node)" class="flow-node__detail">{{ getNodeDetail(node) }}</div>
        </div>
        <div v-if="i < nodes.length - 1" class="flow-arrow">→</div>
      </template>
    </div>

    <!-- 空状态：等待节点到达 -->
    <div v-else class="flow-empty">
      <a-spin size="small"/>
      <span class="flow-empty__text">正在初始化...</span>
    </div>

    <!-- 节点详细数据（展开/折叠） -->
    <div v-if="hasAnyData" class="data-section">
      <a-button type="link" size="small" @click="showData = !showData">
        <template #icon>
          <DownOutlined v-if="!showData"/>
          <UpOutlined v-else/>
        </template>
        {{ showData ? '收起详情' : '查看工作流详情' }}
      </a-button>
      <div v-if="showData" class="data-list">
        <div v-for="node in nodesWithData" :key="node.stage" class="data-item">
          <div class="data-item__header">
            <span class="data-item__stage">{{ node.stage }}</span>
            <a-tag v-if="node.costMs != null" size="small" color="green">{{ formatCost(node.costMs) }}</a-tag>
          </div>
          <div class="data-item__body">
            <template v-for="(value, key) in node.data" :key="String(key)">
              <div v-if="String(key) === 'prompt'" class="data-kv">
                <a-button type="link" size="small" @click="openPrompt(value as string, node.data?.systemPrompt as string | undefined)">
                  查看完整提示词 ({{ (value as string).length }} 字符)
                </a-button>
              </div>
              <div v-else-if="String(key) !== 'systemPrompt'" class="data-kv">
                <span class="data-kv__key">{{ formatKey(String(key)) }}：</span>
                <span class="data-kv__value">{{ formatValue(value) }}</span>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- 完整提示词弹窗 -->
    <a-modal v-model:open="promptModalOpen" title="完整提示词" :footer="null" width="720px" destroy-on-close>
      <div class="prompt-content">{{ promptContent }}</div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
/**
 * Agent 状态图流转可视化面板。
 *
 * <p>以「横向流程图」展示多 Agent 工作流的节点流转与状态：
 * 按节点到达顺序动态显示，支持实时计时与状态着色。
 * 同时支持查看各步骤的详细结构化数据（costMs、data 字段）。</p>
 */
import {
  SyncOutlined,
  BulbOutlined,
  LoadingOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  EllipsisOutlined,
  DownOutlined,
  UpOutlined
} from '@ant-design/icons-vue'
import type {ThinkingNodeVO} from '@/utils/api-types'

const props = defineProps<{
  /** 思考节点列表（按到达顺序） */
  nodes: ThinkingNodeVO[]
}>()

/** 是否存在运行中的节点 */
const anyRunning = computed<boolean>(() => props.nodes.some((n) => n.status === 'running'))

/** 所有节点总耗时（并行节点取最晚结束时间，不重复累加） */
const totalCostMs = computed<number>(() => {
  const nodes = props.nodes.filter(n => n.startMillis && n.costMs)
  if (!nodes.length) return 0
  const earliest = Math.min(...nodes.map(n => n.startMillis!))
  const latest = Math.max(...nodes.map(n => n.startMillis! + n.costMs!))
  return latest - earliest
})

/** 格式化总耗时 */
const formatTotalCost = computed<string>(() => {
  const ms = totalCostMs.value
  if (ms >= 1000) {
    return `${(ms / 1000).toFixed(1)}s`
  }
  return `${ms}ms`
})

/** 是否有节点携带 data 字段 */
const hasAnyData = computed<boolean>(() => props.nodes.some((n) => n.data && Object.keys(n.data).length > 0))

/** 有 data 的节点列表 */
const nodesWithData = computed<ThinkingNodeVO[]>(() =>
  props.nodes.filter((n) => n.data && Object.keys(n.data).length > 0)
)

const showData = ref(false)
const promptModalOpen = ref(false)
const promptContent = ref('')

function openPrompt(prompt: string, systemPrompt?: string) {
  promptContent.value = systemPrompt
    ? `【System Prompt】\n${systemPrompt}\n\n【User Message】\n${prompt}`
    : prompt
  promptModalOpen.value = true
}

/** 节点样式类 */
function getNodeClass(node: ThinkingNodeVO): string {
  switch (node.status) {
    case 'done':
      return 'is-done'
    case 'running':
      return 'is-running'
    case 'failed':
      return 'is-failed'
    default:
      return 'is-pending'
  }
}

/** 节点详情（进行中显示 message，完成显示 detail） */
function getNodeDetail(node: ThinkingNodeVO): string {
  if (node.status === 'running') {
    return node.message || ''
  }
  return node.detail || ''
}

/** 悬浮提示 */
function getNodeTitle(node: ThinkingNodeVO): string {
  return `${node.stage}：${node.status || '未执行'}`
}

/** 格式化耗时 */
function formatCost(ms: number): string {
  if (ms >= 1000) {
    return `${(ms / 1000).toFixed(1)}s`
  }
  return `${ms}ms`
}

/** 格式化 data key 为可读文本 */
function formatKey(key: string): string {
  const map: Record<string, string> = {
    model: '模型',
    strategy: '策略',
    intentLabel: '意图',
    cached: '缓存命中',
    totalLatencyMs: '总耗时(ms)',
    retrievalConfig: '检索配置',
    enableReread: '启用二次检索',
    enableRerank: '启用精排',
    knowledgeBaseId: '知识库ID',
    rewritten: '重写查询',
    hyde: 'HyDE假设文档',
    subqueries: '子问题分解',
    hits: '命中数',
    sources: '来源文档',
    graphCount: '图谱命中',
    vectorCount: '向量命中',
    keywordCount: '关键词命中',
    fusedCount: '融合总数',
    fusedSources: '融合来源',
    pathLabel: '检索路径',
    promptLength: '提示词长度',
    systemPrompt: '系统提示词',
    rounds: '决策轮次',
    decidedToRetrieve: '是否检索',
    totalChunks: '收集片段数',
    toolCallLog: '决策日志',
    graphMode: '图谱模式',
    tool: '工具名称',
    input: '查询内容',
    observationLength: '结果长度',
    selected: '已选策略'
  }
  return map[key] || key
}

/** 格式化 data value 为可读文本（完整展示，不截断） */
function formatValue(value: unknown): string {
  if (value == null) return '-'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value === 'number') return String(value)
  if (Array.isArray(value)) {
    if (value.length === 0) return '（空）'
    if (typeof value[0] === 'string') {
      return value.join('、')
    }
    return `${value.length} 项`
  }
  if (typeof value === 'object') return JSON.stringify(value, null, 2)
  return String(value)
}
</script>

<style scoped>
.thinking-panel {
  background: #fafafa;
  border-radius: 8px;
  padding: 8px 12px 12px;
  margin-bottom: 6px;
}

.thinking-panel__header {
  color: #4e5969;
  font-size: 13px;
  margin-bottom: 10px;
}

.flow {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 4px;
}

.flow-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  color: #86909c;
  font-size: 12px;
}

.flow-empty__text {
  color: #86909c;
}

.flow-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 72px;
  max-width: 110px;
  padding: 8px 6px;
  border-radius: 8px;
  border: 1px solid #e5e6eb;
  background: #fff;
  text-align: center;
  transition: all 0.2s;
}

.flow-node__dot {
  font-size: 18px;
  line-height: 1;
  margin-bottom: 6px;
}

.flow-node__label {
  font-size: 12px;
  color: #4e5969;
  line-height: 1.3;
  word-break: break-word;
}

.flow-node__cost {
  font-size: 10px;
  color: #52c41a;
  margin-top: 2px;
  font-weight: 500;
}

.flow-node__detail {
  font-size: 11px;
  color: #86909c;
  margin-top: 4px;
  line-height: 1.3;
  word-break: break-all;
  max-width: 100px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.flow-arrow {
  align-self: center;
  color: #c9cdd4;
  font-size: 14px;
  padding-top: 16px;
}

/* 状态样式 */

.flow-node.is-done .flow-node__dot {
  color: #52c41a;
}

.flow-node.is-running .flow-node__dot {
  color: #1677ff;
}

.flow-node.is-running .flow-node__label {
  color: #1677ff;
  font-weight: 600;
}

.flow-node.is-failed .flow-node__dot {
  color: #ff4d4f;
}

.flow-node.is-pending .flow-node__dot {
  color: #c9cdd4;
}

/* 数据详情区 */

.data-section {
  margin-top: 8px;
  border-top: 1px solid #e5e6eb;
  padding-top: 6px;
}

.data-list {
  margin-top: 6px;
}

.data-item {
  background: #fff;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 6px;
}

.data-item__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.data-item__stage {
  font-size: 12px;
  font-weight: 600;
  color: #1d2129;
}

.data-item__body {
  font-size: 11px;
  color: #4e5969;
}

.data-kv {
  display: flex;
  margin-bottom: 2px;
  line-height: 1.5;
}

.data-kv__key {
  color: #86909c;
  min-width: 70px;
  flex-shrink: 0;
}

.data-kv__value {
  color: #1d2129;
  word-break: break-all;
  white-space: pre-wrap;
  max-height: 400px;
  overflow-y: auto;
}

.prompt-content {
  max-height: 600px;
  overflow-y: auto;
  white-space: pre-wrap;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
  background: #f7f8fa;
  padding: 12px;
  border-radius: 6px;
  color: #1d2129;
  word-break: break-all;
}
</style>

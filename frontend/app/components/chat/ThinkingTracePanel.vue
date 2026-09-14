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

    <!-- 工作流可视化 -->
    <div v-if="nodes.length > 0" class="workflow">
      <template v-for="(stage, idx) in workflowStages" :key="stage.key">
        <!-- 连接线 -->
        <div v-if="idx > 0" class="workflow-connector">
          <div class="connector-line" :class="{ 'connector-line--active': isStageActive(stage) }"></div>
        </div>
        
        <!-- 单节点阶段 -->
        <div v-if="stage.type === 'single'" class="workflow-stage">
          <div 
            class="node" 
            :class="getNodeClass(stage.node!)"
            :title="getNodeTitle(stage.node!)"
            @click="selectNode(stage.node!)"
          >
            <div class="node__icon">
              <LoadingOutlined v-if="stage.node!.status === 'running'" spin/>
              <CheckCircleFilled v-else-if="stage.node!.status === 'done'"/>
              <CloseCircleFilled v-else-if="stage.node!.status === 'failed'"/>
              <EllipsisOutlined v-else/>
            </div>
            <div class="node__content">
              <div class="node__label">{{ stage.node!.stage }}</div>
              <div v-if="stage.node!.costMs != null && stage.node!.status === 'done'" class="node__cost">
                {{ formatCost(stage.node!.costMs) }}
              </div>
              <div v-if="getNodeDetail(stage.node!)" class="node__detail">{{ getNodeDetail(stage.node!) }}</div>
            </div>
          </div>
        </div>

        <!-- 并行节点阶段 -->
        <div v-else class="workflow-stage workflow-stage--parallel">
          <div class="parallel-label">并行执行</div>
          <div class="parallel-nodes">
            <div 
              v-for="node in stage.nodes" 
              :key="node.stage ?? 'unknown'"
              class="node node--compact"
              :class="getNodeClass(node)"
              :title="getNodeTitle(node)"
              @click="selectNode(node)"
            >
              <div class="node__icon">
                <LoadingOutlined v-if="node.status === 'running'" spin/>
                <CheckCircleFilled v-else-if="node.status === 'done'"/>
                <CloseCircleFilled v-else-if="node.status === 'failed'"/>
                <EllipsisOutlined v-else/>
              </div>
              <div class="node__content">
                <div class="node__label">{{ node.stage }}</div>
                <div v-if="node.costMs != null && node.status === 'done'" class="node__cost">
                  {{ formatCost(node.costMs) }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <!-- 节点详情弹窗 -->
    <a-modal v-model:open="nodeModalOpen" :title="selectedNode?.stage || '节点详情'" :footer="null" width="600px" destroy-on-close>
      <template #extra>
        <a-tag v-if="selectedNode" :color="getStatusColor(selectedNode.status)">{{ getStatusText(selectedNode.status) }}</a-tag>
      </template>
      <div v-if="selectedNode" class="node-detail-modal">
        <div v-if="selectedNode.costMs != null" class="detail-row">
          <span class="detail-label">耗时</span>
          <span class="detail-value">{{ formatCost(selectedNode.costMs) }}</span>
        </div>
        <div v-if="selectedNode.message" class="detail-row">
          <span class="detail-label">消息</span>
          <span class="detail-value">{{ selectedNode.message }}</span>
        </div>
        <div v-if="selectedNode.detail" class="detail-row">
          <span class="detail-label">详情</span>
          <span class="detail-value">{{ selectedNode.detail }}</span>
        </div>
        <template v-if="selectedNode.data && Object.keys(selectedNode.data).length > 0">
          <a-divider style="margin: 12px 0"/>
          <div class="detail-section-title">详细数据</div>
          <div v-for="(value, key) in selectedNode.data" :key="String(key)" class="detail-row">
            <span class="detail-label">{{ formatKey(String(key)) }}</span>
            <span class="detail-value detail-value--data">
              <template v-if="String(key) === 'prompt'">
                <a-button type="link" size="small" @click="openPrompt(value as string, selectedNode.data?.systemPrompt as string | undefined)">
                  查看完整提示词 ({{ (value as string).length }} 字符)
                </a-button>
              </template>
              <template v-else-if="String(key) !== 'systemPrompt'">
                {{ formatValue(value) }}
              </template>
            </span>
          </div>
        </template>
      </div>
    </a-modal>

    <!-- 空状态 -->
    <div v-if="nodes.length === 0" class="workflow-empty">
      <div class="workflow-empty__icon">
        <SyncOutlined spin />
      </div>
      <span class="workflow-empty__text">正在初始化...</span>
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
        <div v-for="node in nodesWithData" :key="node.stage ?? 'unknown'" class="data-item">
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
 * <p>以「流程图」展示多 Agent 工作流的节点流转与状态：
 * 支持并行节点显示（如图谱/向量/关键词检索同时执行），
 * 按节点到达顺序动态显示，支持实时计时与状态着色。</p>
 */
import {
  SyncOutlined,
  BulbOutlined,
  LoadingOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  EllipsisOutlined,
  DownOutlined,
  UpOutlined,
  CloseOutlined
} from '@ant-design/icons-vue'
import type {ThinkingNodeVO} from '@/utils/api-types'

const props = defineProps<{
  /** 思考节点列表（按到达顺序） */
  nodes: ThinkingNodeVO[]
}>()

/** 并行检索节点阶段名 */
const PARALLEL_STAGES = new Set(['图谱检索', '向量检索', '关键词检索'])

/** 工作流阶段定义 */
interface WorkflowStage {
  key: string
  type: 'single' | 'parallel'
  node?: ThinkingNodeVO
  nodes?: ThinkingNodeVO[]
}

/** 将节点列表转换为工作流阶段（自动合并并行节点） */
const workflowStages = computed<WorkflowStage[]>(() => {
  const stages: WorkflowStage[] = []
  const parallelBuffer: ThinkingNodeVO[] = []
  
  for (const node of props.nodes) {
    const stageName = node.stage ?? 'unknown'
    if (PARALLEL_STAGES.has(stageName)) {
      parallelBuffer.push(node)
    } else {
      // 先处理并行缓冲区
      if (parallelBuffer.length > 0) {
        stages.push({
          key: `parallel-${parallelBuffer.map(n => n.stage ?? 'unknown').join('-')}`,
          type: 'parallel',
          nodes: [...parallelBuffer]
        })
        parallelBuffer.length = 0
      }
      stages.push({
        key: stageName,
        type: 'single',
        node
      })
    }
  }
  
  // 处理剩余的并行缓冲区
  if (parallelBuffer.length > 0) {
    stages.push({
      key: `parallel-${parallelBuffer.map(n => n.stage ?? 'unknown').join('-')}`,
      type: 'parallel',
      nodes: parallelBuffer
    })
  }
  
  return stages
})

/** 是否存在运行中的节点 */
const anyRunning = computed<boolean>(() => props.nodes.some((n) => n.status === 'running'))

/** 所有节点总耗时（并行节点取最晚结束时间） */
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
const selectedNode = ref<ThinkingNodeVO | null>(null)
const nodeModalOpen = ref(false)

/** 点击节点显示详情 */
function selectNode(node: ThinkingNodeVO) {
  selectedNode.value = node
  nodeModalOpen.value = true
}

/** 判断阶段是否处于活动状态 */
function isStageActive(stage: WorkflowStage): boolean {
  if (stage.type === 'single' && stage.node) {
    return stage.node.status === 'running' || stage.node.status === 'done'
  }
  if (stage.type === 'parallel' && stage.nodes) {
    return stage.nodes.some(n => n.status === 'running' || n.status === 'done')
  }
  return false
}

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
      return 'node--done'
    case 'running':
      return 'node--running'
    case 'failed':
      return 'node--failed'
    default:
      return 'node--pending'
  }
}

/** 节点详情 */
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

/** 状态颜色 */
function getStatusColor(status?: string): string {
  switch (status) {
    case 'done': return 'green'
    case 'running': return 'blue'
    case 'failed': return 'red'
    default: return 'default'
  }
}

/** 状态文本 */
function getStatusText(status?: string): string {
  switch (status) {
    case 'done': return '完成'
    case 'running': return '执行中'
    case 'failed': return '失败'
    default: return '等待中'
  }
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
    selected: '已选策略',
    engine: '搜索引擎'
  }
  return map[key] || key
}

/** 格式化 data value 为可读文本 */
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
  background: linear-gradient(135deg, #f5f7fa 0%, #f0f2f5 100%);
  border-radius: 12px;
  padding: 12px 16px;
  margin-bottom: 8px;
  border: 1px solid #e8e8e8;
}

.thinking-panel__header {
  color: #4e5969;
  font-size: 13px;
  margin-bottom: 12px;
}

/* 工作流容器 */
.workflow {
  display: flex;
  flex-direction: row;
  gap: 0;
  padding: 8px 0;
  overflow-x: auto;
  align-items: center;
}

/* 连接线 */
.workflow-connector {
  display: flex;
  align-items: center;
  width: 24px;
  flex-shrink: 0;
}

.connector-line {
  height: 2px;
  width: 100%;
  background: #d9d9d9;
  transition: background 0.3s ease;
}

.connector-line--active {
  background: linear-gradient(90deg, #1677ff 0%, #52c41a 100%);
  box-shadow: 0 0 8px rgba(22, 119, 255, 0.4);
}

/* 阶段容器 */
.workflow-stage {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.workflow-stage--parallel {
  position: relative;
}

.parallel-label {
  font-size: 10px;
  color: #86909c;
  background: #f0f0f0;
  padding: 2px 8px;
  border-radius: 10px;
  margin-bottom: 6px;
}

.parallel-nodes {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
}

/* 节点样式 */
.node {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 100px;
  max-width: 140px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff;
  border: 2px solid #e8e8e8;
  text-align: center;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: default;
  position: relative;
  overflow: hidden;
}

.node::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: #d9d9d9;
  transition: background 0.3s ease;
}

.node--compact {
  min-width: 80px;
  max-width: 100px;
  padding: 8px 10px;
}

.node__icon {
  font-size: 20px;
  line-height: 1;
  margin-bottom: 6px;
  transition: transform 0.3s ease;
}

.node:hover .node__icon {
  transform: scale(1.1);
}

.node__content {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.node__label {
  font-size: 12px;
  color: #4e5969;
  line-height: 1.3;
  word-break: break-word;
  font-weight: 500;
}

.node__cost {
  font-size: 11px;
  color: #52c41a;
  margin-top: 4px;
  font-weight: 600;
  background: #f6ffed;
  padding: 2px 6px;
  border-radius: 8px;
}

.node__detail {
  font-size: 11px;
  color: #86909c;
  margin-top: 4px;
  line-height: 1.3;
  word-break: break-all;
  max-width: 120px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

/* 节点状态样式 */
.node--done {
  border-color: #52c41a;
  box-shadow: 0 2px 8px rgba(82, 196, 26, 0.15);
}

.node--done::before {
  background: linear-gradient(90deg, #52c41a, #73d13d);
}

.node--done .node__icon {
  color: #52c41a;
}

.node--running {
  border-color: #1677ff;
  box-shadow: 0 0 0 4px rgba(22, 119, 255, 0.1);
  animation: node-pulse 2s ease-in-out infinite;
}

.node--running::before {
  background: linear-gradient(90deg, #1677ff, #4096ff);
}

.node--running .node__icon {
  color: #1677ff;
}

.node--running .node__label {
  color: #1677ff;
  font-weight: 600;
}

.node--failed {
  border-color: #ff4d4f;
  box-shadow: 0 2px 8px rgba(255, 77, 79, 0.15);
}

.node--failed::before {
  background: linear-gradient(90deg, #ff4d4f, #ff7875);
}

.node--failed .node__icon {
  color: #ff4d4f;
}

.node--pending {
  border-color: #d9d9d9;
  opacity: 0.6;
}

.node--pending .node__icon {
  color: #bfbfbf;
}

/* 悬停效果 */
.node:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* 脉冲动画 */
@keyframes node-pulse {
  0%, 100% {
    box-shadow: 0 0 0 4px rgba(22, 119, 255, 0.1);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(22, 119, 255, 0.05);
  }
}

/* 空状态 */
.workflow-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  gap: 12px;
}

.workflow-empty__icon {
  font-size: 24px;
  color: #1677ff;
}

.workflow-empty__text {
  font-size: 13px;
  color: #86909c;
}

/* 数据详情区 */
.data-section {
  margin-top: 12px;
  border-top: 1px solid #e8e8e8;
  padding-top: 8px;
}

.data-list {
  margin-top: 8px;
}

.data-item {
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  transition: box-shadow 0.2s ease;
}

.data-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.data-item__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.data-item__stage {
  font-size: 13px;
  font-weight: 600;
  color: #1d2129;
}

.data-item__body {
  font-size: 12px;
  color: #4e5969;
}

.data-kv {
  display: flex;
  margin-bottom: 4px;
  line-height: 1.6;
}

.data-kv__key {
  color: #86909c;
  min-width: 80px;
  flex-shrink: 0;
}

.data-kv__value {
  color: #1d2129;
  word-break: break-all;
  white-space: pre-wrap;
  max-height: 400px;
  overflow-y: auto;
}

/* 节点详情弹窗 */
.node-detail-modal {
  font-size: 13px;
}

.detail-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #1d2129;
  margin-bottom: 8px;
}

.detail-row {
  display: flex;
  margin-bottom: 6px;
  line-height: 1.5;
}

.detail-label {
  color: #86909c;
  min-width: 60px;
  flex-shrink: 0;
}

.detail-value {
  color: #1d2129;
  word-break: break-all;
}

.detail-value--data {
  max-height: 150px;
  overflow-y: auto;
  white-space: pre-wrap;
  background: #f7f8fa;
  padding: 4px 8px;
  border-radius: 4px;
  width: 100%;
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

<template>
  <div class="thinking-panel">
    <div class="thinking-panel__header">
      <a-space size="small">
        <SyncOutlined v-if="anyRunning" spin style="color: #1677ff" />
        <BulbOutlined v-else style="color: #faad14" />
        <span>Agent 工作流</span>
        <a-tag color="blue">{{ nodes.length }} 个节点</a-tag>
      </a-space>
    </div>

    <!-- 状态图流转：横向流程图 -->
    <div class="flow">
      <template v-for="(stage, i) in FLOW_STAGES" :key="stage">
        <div class="flow-node" :class="nodeClass(stage)" :title="titleOf(stage)">
          <div class="flow-node__dot">
            <LoadingOutlined v-if="isStatus(stage, 'running')" spin />
            <CheckCircleFilled v-else-if="isStatus(stage, 'done')" />
            <CloseCircleFilled v-else-if="isStatus(stage, 'failed')" />
            <EllipsisOutlined v-else />
          </div>
          <div class="flow-node__label">{{ stage }}</div>
          <div v-if="detailOf(stage)" class="flow-node__detail">{{ detailOf(stage) }}</div>
        </div>
        <div v-if="i < flowStages.length - 1" class="flow-arrow">→</div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * Agent 状态图流转可视化面板。
 *
 * <p>以「横向流程图」展示多 Agent 工作流的节点流转与状态：
 * 已执行节点按实际状态着色（完成/进行中/失败），未经过的节点显示为待执行。</p>
 */
import {
  SyncOutlined,
  BulbOutlined,
  LoadingOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  EllipsisOutlined
} from '@ant-design/icons-vue'
import type { ThinkingNodeVO } from '@/utils/api-types'

const props = defineProps<{
  /** 思考节点列表 */
  nodes: ThinkingNodeVO[]
}>()

/** 完整 Agent 工作流模板（按序流转；阶段名与后端 orchestrator 保持一致） */
const FLOW_STAGES = [
  '意图识别',
  '检索策略调度',
  '查询重写与 HyDE',
  '图谱检索',
  '向量检索',
  '融合与补全',
  '总结生成',
  '直接应答'
]

/** 是否存在运行中的节点 */
const anyRunning = computed<boolean>(() => props.nodes.some((n) => n.status === 'running'))

/** 获取某阶段的节点 */
function nodeOf(stage: string): ThinkingNodeVO | undefined {
  return props.nodes.find((n) => n.stage === stage)
}

/** 判断某阶段是否处于指定状态 */
function isStatus(stage: string, status: string): boolean {
  return nodeOf(stage)?.status === status
}

/** 节点样式类 */
function nodeClass(stage: string): string {
  const node = nodeOf(stage)
  if (!node) {
    return 'is-pending'
  }
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
function detailOf(stage: string): string {
  const node = nodeOf(stage)
  if (!node) {
    return ''
  }
  if (node.status === 'running') {
    return node.message || ''
  }
  return node.detail || ''
}

/** 悬浮提示 */
function titleOf(stage: string): string {
  const node = nodeOf(stage)
  return node ? `${stage}：${node.status || '未执行'}` : `${stage}：未执行`
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
.flow-node.is-done {
  border-color: #95de64;
}

.flow-node.is-done .flow-node__dot {
  color: #52c41a;
}

.flow-node.is-running {
  border-color: #1677ff;
  background: #e6f4ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.12);
  animation: flow-pulse 1.5s ease-in-out infinite;
}

.flow-node.is-running .flow-node__dot {
  color: #1677ff;
}

.flow-node.is-running .flow-node__label {
  color: #1677ff;
  font-weight: 600;
}

.flow-node.is-failed {
  border-color: #ff7875;
}

.flow-node.is-failed .flow-node__dot {
  color: #ff4d4f;
}

.flow-node.is-pending {
  opacity: 0.55;
}

.flow-node.is-pending .flow-node__dot {
  color: #c9cdd4;
}

@keyframes flow-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.12);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(22, 119, 255, 0.2);
  }
}
</style>

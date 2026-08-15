<template>
  <a-collapse ghost class="thinking-panel">
    <a-collapse-panel key="thinking">
      <template #header>
        <a-space size="small">
          <SyncOutlined v-if="anyRunning" spin style="color: #1677ff" />
          <BulbOutlined v-else style="color: #faad14" />
          <span>思考过程</span>
          <a-tag color="blue">{{ nodes.length }} 个节点</a-tag>
        </a-space>
      </template>
      <a-timeline>
        <a-timeline-item v-for="node in nodes" :key="`${node.stage}-${node.agent}`" :color="timelineColor(node.status)">
          <template #dot>
            <LoadingOutlined v-if="node.status === 'running'" spin style="color: #1677ff" />
            <CheckCircleFilled v-else-if="node.status === 'done'" style="color: #52c41a" />
            <CloseCircleFilled v-else-if="node.status === 'failed'" style="color: #ff4d4f" />
            <ClockCircleOutlined v-else />
          </template>
          <div class="thinking-node">
            <div class="thinking-node__title">
              <span class="thinking-node__stage">{{ node.stage }}</span>
              <a-tag size="small">{{ node.agent }}</a-tag>
              <span v-if="node.status === 'running'" class="thinking-node__running">进行中</span>
            </div>
            <div class="thinking-node__message">{{ node.message }}</div>
            <div v-if="node.detail" class="thinking-node__detail">{{ node.detail }}</div>
          </div>
        </a-timeline-item>
      </a-timeline>
    </a-collapse-panel>
  </a-collapse>
</template>

<script setup lang="ts">
/**
 * Agent 思考链路折叠面板。
 *
 * <p>根据思考节点状态动态展示（运行/完成/失败），支持「动态折叠」，
 * 节点推进时自动展开。</p>
 */
import {
  SyncOutlined,
  BulbOutlined,
  LoadingOutlined,
  CheckCircleFilled,
  CloseCircleFilled,
  ClockCircleOutlined
} from '@ant-design/icons-vue'
import type { ThinkingNodeVO } from '@/utils/api-types'

const props = defineProps<{
  /** 思考节点列表 */
  nodes: ThinkingNodeVO[]
}>()

/** 是否存在运行中的节点 */
const anyRunning = computed<boolean>(() => props.nodes.some((n) => n.status === 'running'))

/** 节点状态对应时间线颜色 */
function timelineColor(status?: string): string {
  switch (status) {
    case 'done':
      return '#52c41a'
    case 'failed':
      return '#ff4d4f'
    case 'running':
      return '#1677ff'
    default:
      return '#d9d9d9'
  }
}
</script>

<style scoped>
.thinking-panel {
  background: #fafafa;
  border-radius: 8px;
  margin-bottom: 4px;
}

.thinking-node__title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.thinking-node__stage {
  font-weight: 600;
  color: #1f2329;
}

.thinking-node__running {
  color: #1677ff;
  font-size: 12px;
}

.thinking-node__message {
  color: #4e5969;
  font-size: 13px;
  margin-top: 2px;
}

.thinking-node__detail {
  color: #86909c;
  font-size: 12px;
  margin-top: 2px;
}
</style>

<template>
  <div class="session-list">
    <a-button type="primary" block class="session-list__new" @click="emit('new')">
      <template #icon><PlusOutlined /></template>
      新建对话
    </a-button>

    <a-menu
      mode="inline"
      :selected-keys="selectedKeys"
      class="session-list__menu"
      @click="handleClick"
    >
      <a-menu-item v-for="session in sessions" :key="String(session.id)">
        <div class="session-item">
          <span class="session-item__title">
            <PushpinFilled v-if="session.pinned === 1" style="color: #faad14" />
            {{ session.title }}
          </span>
          <a-space v-if="session.id === currentSessionId" class="session-item__ops" :size="0">
            <a-tooltip :title="session.pinned === 1 ? '取消置顶' : '置顶'">
              <PushpinOutlined @click.stop="emit('pin', session)" />
            </a-tooltip>
            <a-tooltip title="删除会话">
              <DeleteOutlined @click.stop="emit('remove', session)" />
            </a-tooltip>
          </a-space>
        </div>
      </a-menu-item>
    </a-menu>

    <div v-if="sessions.length === 0" class="session-list__empty">暂无历史会话</div>
  </div>
</template>

<script setup lang="ts">
/**
 * 会话列表面板：新建对话、切换会话、置顶与逻辑删除。
 */
import { PlusOutlined, DeleteOutlined, PushpinOutlined, PushpinFilled } from '@ant-design/icons-vue'
import type { SessionVO } from '@/utils/api-types'

const props = defineProps<{
  /** 会话列表 */
  sessions: SessionVO[]
  /** 当前会话 ID */
  currentSessionId: number | null
}>()

const emit = defineEmits<{
  (e: 'new'): void
  (e: 'select', sessionId: string | number): void
  (e: 'pin', session: SessionVO): void
  (e: 'remove', session: SessionVO): void
}>()

const selectedKeys = computed<string[]>(() =>
  props.currentSessionId ? [String(props.currentSessionId)] : []
)

function handleClick({ key }: { key: string | number }): void {
  // 注意：雪花 ID 为 19 位数字，必须原样透传字符串，禁止 Number() 转换（会丢失精度）
  emit('select', key)
}
</script>

<style scoped>
.session-list {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 12px;
}

.session-list__new {
  margin-bottom: 12px;
}

.session-list__menu {
  flex: 1;
  overflow-y: auto;
  border-inline-end: none !important;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.session-item__title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item__ops {
  display: none;
}

.session-item:hover .session-item__ops {
  display: inline-flex;
}

.session-list__empty {
  color: #86909c;
  text-align: center;
  padding: 24px 0;
  font-size: 13px;
}
</style>

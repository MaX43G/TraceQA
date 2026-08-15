<template>
  <div class="markdown-body" v-html="html" @click="handleClick" />
</template>

<script setup lang="ts">
/**
 * Markdown 渲染组件。
 *
 * <p>渲染 AI 回答，并将 {@code [citation:N]} 角标绑定点击事件，
 * 点击后触发 {@code cite-click} 事件供父组件高亮对应引用来源。</p>
 */
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{
  /** 原始 Markdown 内容 */
  content: string
  /** 是否处于打字机状态（追加光标动画） */
  typing?: boolean
  /** 实际存在引用的序号集合（过滤无内容角标） */
  availableIndexes?: Set<number>
}>()

const emit = defineEmits<{
  (e: 'cite-click', index: number): void
}>()

const html = computed<string>(() => {
  const body = renderMarkdown(props.content || '', props.availableIndexes)
  // 打字机状态追加光标
  return props.typing ? `${body}<span class="typing-cursor"></span>` : body
})

/** 点击角标时通知父组件 */
function handleClick(event: MouseEvent): void {
  const target = (event.target as HTMLElement)?.closest?.('.tq-cite')
  if (target) {
    const idx = Number((target as HTMLElement).dataset.idx || '0')
    if (idx > 0) {
      emit('cite-click', idx)
    }
  }
}
</script>

<style scoped>
.markdown-body {
  font-size: 14px;
  line-height: 1.7;
  color: #1f2329;
  word-break: break-word;
}
</style>

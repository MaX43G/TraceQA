<template>
  <div class="citation-panel">
    <div class="citation-panel__header">
      <a-space size="small">
        <LinkOutlined />
        <span>引用来源</span>
        <a-tag color="blue">{{ displayReferences.length }}</a-tag>
      </a-space>
    </div>
    <div v-if="displayReferences.length === 0" class="citation-panel__empty">本次回答无引用来源</div>
    <div
      v-for="ref in displayReferences"
      :key="ref.index"
      :id="`cite-${ref.index}`"
      class="citation-item"
      :class="{ 'citation-item--active': activeIndex === ref.index }"
      @click="handleView(ref)"
    >
      <a-space class="citation-item__head" :size="6">
        <a-badge
          :count="ref.index"
          :show-zero="false"
          :color="activeIndex === ref.index ? '#1677ff' : '#86909c'"
        />
        <span class="citation-item__title">{{ ref.title || ref.filePath }}</span>
        <a-tooltip :title="ref.filePath">
          <FileOutlined style="color: #86909c" />
        </a-tooltip>
        <a-tag size="small" style="margin-left: auto">点击查看全文</a-tag>
      </a-space>
      <div class="citation-item__content">{{ ref.content }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 引用来源面板。
 *
 * <p>仅展示回答中「实际引用」的文献（未引用的一律隐藏）；
 * 点击文献项或正文角标可查看全文。</p>
 */
import { LinkOutlined, FileOutlined } from '@ant-design/icons-vue'
import type { ReferenceVO } from '@/utils/api-types'

const props = defineProps<{
  /** 引用来源列表 */
  references: ReferenceVO[]
  /** 回答中实际引用的序号集合（仅显示这些） */
  usedIndexes?: Set<number>
}>()

const emit = defineEmits<{
  (e: 'view', ref: ReferenceVO): void
}>()

/** 当前高亮的引用序号 */
const activeIndex = ref<number | null>(null)

/** 只显示实际被引用的文献（回答未引用任何文献时显示为空） */
const displayReferences = computed<ReferenceVO[]>(() => {
  if (props.usedIndexes) {
    return props.references.filter((r) => props.usedIndexes?.has(r.index))
  }
  return props.references
})

/** 高亮指定引用并滚动定位 */
function highlight(index: number): void {
  activeIndex.value = index
  nextTick(() => {
    const el = document.getElementById(`cite-${index}`)
    el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}

/** 点击文献：通知父组件打开全文 */
function handleView(ref: ReferenceVO): void {
  emit('view', ref)
}

defineExpose({ highlight })
</script>

<style scoped>
.citation-panel {
  margin-top: 12px;
  border-top: 1px dashed #e5e6eb;
  padding-top: 10px;
}

.citation-panel__header {
  color: #4e5969;
  font-size: 13px;
  margin-bottom: 8px;
}

.citation-panel__empty {
  color: #86909c;
  font-size: 12px;
}

.citation-item {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.25s;
}

.citation-item:hover {
  border-color: #1677ff;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.1);
}

.citation-item--active {
  border-color: #1677ff;
  background: #e6f4ff;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.15);
}

.citation-item__title {
  font-weight: 600;
  color: #1f2329;
  font-size: 13px;
}

.citation-item__content {
  color: #4e5969;
  font-size: 12px;
  line-height: 1.6;
  margin-top: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>

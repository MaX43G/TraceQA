<template>
  <a-popconfirm :title="title || '确定删除该记录？'" @confirm="handleConfirm">
    <a-button type="link" size="small" danger>
      <template #icon v-if="showIcon">
        <DeleteOutlined/>
      </template>
      {{ text }}
    </a-button>
  </a-popconfirm>
</template>

<script setup lang="ts">
/**
 * 通用「删除确认」按钮：封装 popconfirm + 危险按钮，减少各页重复代码。
 */
import {DeleteOutlined} from '@ant-design/icons-vue'

withDefaults(
    defineProps<{
      title?: string
      text?: string
      showIcon?: boolean
    }>(),
    {
      title: '',
      text: '删除',
      showIcon: true
    }
)

const emit = defineEmits<{
  (e: 'confirm'): void
}>()

function handleConfirm(): void {
  emit('confirm')
}
</script>

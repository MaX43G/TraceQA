<template>
  <a-modal :open="open" title="修改昵称" :confirm-loading="loading" :width="420" @ok="handleOk" @cancel="handleClose">
    <a-form layout="vertical">
      <a-form-item label="昵称" required>
        <a-input v-model:value="nickname" placeholder="请输入新昵称" :maxlength="32" @keyup.enter="handleOk" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
/**
 * 修改昵称弹窗：修改成功后同步本地用户信息。
 */
import { message } from 'ant-design-vue'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
}>()

const auth = useAuthStore()
const nickname = ref('')
const loading = ref(false)

watch(
  () => props.open,
  (v) => {
    if (v) {
      nickname.value = auth.userInfo?.nickname ?? ''
    }
  }
)

async function handleOk(): Promise<void> {
  const value = nickname.value.trim()
  if (!value) {
    message.warning('昵称不能为空')
    return
  }
  loading.value = true
  try {
    await auth.updateNickname(value)
    message.success('昵称已更新')
    emit('update:open', false)
  } catch (err) {
    message.error((err as Error).message || '修改失败')
  } finally {
    loading.value = false
  }
}

function handleClose(): void {
  emit('update:open', false)
}
</script>

<template>
  <a-modal
      v-model:open="open"
      title="修改密码"
      :confirm-loading="loading"
      ok-text="确认修改"
      cancel-text="取消"
      @ok="handleSubmit"
  >
    <a-form layout="vertical" :model="form">
      <a-form-item label="原密码" required>
        <a-input-password v-model:value="form.oldPassword" placeholder="请输入原密码"/>
      </a-form-item>
      <a-form-item label="新密码" required>
        <a-input-password v-model:value="form.newPassword" placeholder="6-32 位新密码"/>
      </a-form-item>
      <a-form-item label="确认新密码" required>
        <a-input-password v-model:value="form.confirm" placeholder="再次输入新密码"/>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
/**
 * 修改密码弹窗：校验原密码后更新为新密码（用户管理功能）。
 */
import {message} from 'ant-design-vue'
import {changePassword} from '@/api/traceqa/renzheng'

const open = defineModel<boolean>('open', {default: false})

const loading = ref(false)
const form = reactive({oldPassword: '', newPassword: '', confirm: ''})

async function handleSubmit(): Promise<void> {
  if (!form.oldPassword) {
    await message.warning('请输入原密码')
    return
  }
  if (!form.newPassword || form.newPassword.length < 6) {
    await message.warning('新密码长度需在 6-32 之间')
    return
  }
  if (form.newPassword !== form.confirm) {
    await message.warning('两次输入的新密码不一致')
    return
  }
  loading.value = true
  try {
    await changePassword({oldPassword: form.oldPassword, newPassword: form.newPassword})
    await message.success('密码修改成功，请重新登录')
    form.oldPassword = ''
    form.newPassword = ''
    form.confirm = ''
    open.value = false
    // 密码变更后强制重新登录
    const {useAuthStore} = await import('@/stores/auth')
    useAuthStore().logout()
    window.location.href = '/login'
  } catch (err) {
    await message.error((err as Error).message || '修改失败')
  } finally {
    loading.value = false
  }
}
</script>

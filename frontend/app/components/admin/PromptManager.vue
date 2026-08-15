<template>
  <div>
    <a-alert
      type="info"
      show-icon
      message="系统提示词由平台预置，管理员可编辑内容并切换启用状态，不支持新增场景。"
      class="prompt-alert"
    />
    <a-table :data-source="promptList" :columns="columns" row-key="id" :loading="loading" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'enabled'">
          <a-tag :color="record.enabled === 1 ? 'green' : 'default'">
            {{ record.enabled === 1 ? '启用中' : '已停用' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'content'">
          <a-tooltip :title="record.content">
            <span class="content-preview">{{ record.content }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button v-if="record.enabled !== 1" type="link" size="small" @click="handleEnable(record)">
              启用
            </a-button>
            <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm title="确定删除该提示词？" @confirm="handleDelete(record)">
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="modalOpen" title="编辑提示词" width="720px" @ok="handleSave">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="场景编码">
              <a-input v-model:value="form.scenario" disabled />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="名称" required>
              <a-input v-model:value="form.name" placeholder="提示词名称" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="提示词内容" required>
          <a-textarea v-model:value="form.content" :rows="10" placeholder="提示词正文" />
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="form.remark" placeholder="备注" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
/**
 * 系统提示词管理（管理员）：编辑已有提示词内容、启用切换。
 * 提示词场景由平台预置，不支持新增。
 */
import { message } from 'ant-design-vue'
import { list as fetchPrompts, update, enable, deleteUsingDelete } from '@/api/traceqa/xitongtishici'
import type { SystemPromptDTO } from '@/utils/api-types'

const columns = [
  { title: '场景', dataIndex: 'scenario', key: 'scenario' },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '内容', key: 'content' },
  { title: '状态', key: 'enabled' },
  { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime' },
  { title: '操作', key: 'action' }
]

const loading = ref(false)
const listData = ref<SystemPromptDTO[]>([])
const modalOpen = ref(false)
const editing = ref<SystemPromptDTO | null>(null)
const form = reactive({ scenario: '', name: '', content: '', remark: '' })

const promptList = computed<SystemPromptDTO[]>(() => listData.value)

async function load(): Promise<void> {
  loading.value = true
  try {
    const res = await fetchPrompts()
    listData.value = res.data ?? []
  } catch (err) {
    message.error((err as Error).message || '提示词加载失败')
  } finally {
    loading.value = false
  }
}

function openEdit(record: SystemPromptDTO): void {
  editing.value = record
  form.scenario = record.scenario ?? ''
  form.name = record.name ?? ''
  form.content = record.content ?? ''
  form.remark = record.remark ?? ''
  modalOpen.value = true
}

async function handleSave(): Promise<void> {
  if (!form.name.trim() || !form.content.trim()) {
    message.warning('请完整填写表单')
    return
  }
  if (!editing.value?.id) {
    return
  }
  try {
    await update(
      { id: editing.value.id },
      {
        scenario: form.scenario,
        name: form.name.trim(),
        content: form.content,
        remark: form.remark,
        // 保留原有启用状态，编辑不改变启用/停用
        enabled: editing.value.enabled ?? 0
      }
    )
    message.success('保存成功')
    modalOpen.value = false
    await load()
  } catch (err) {
    message.error((err as Error).message || '保存失败')
  }
}

async function handleEnable(record: SystemPromptDTO): Promise<void> {
  try {
    await enable({ id: record.id })
    message.success('已启用（同场景其他项自动停用）')
    await load()
  } catch (err) {
    message.error((err as Error).message || '操作失败')
  }
}

async function handleDelete(record: SystemPromptDTO): Promise<void> {
  try {
    await deleteUsingDelete({ id: record.id })
    message.success('已删除（重启后自动恢复预置提示词）')
    await load()
  } catch (err) {
    message.error((err as Error).message || '删除失败')
  }
}

onMounted(load)
</script>

<style scoped>
.prompt-alert {
  margin-bottom: 16px;
}

.content-preview {
  display: inline-block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  color: #4e5969;
  font-size: 12px;
}
</style>

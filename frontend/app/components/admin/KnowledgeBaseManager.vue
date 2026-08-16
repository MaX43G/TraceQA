<template>
  <div>
    <div class="toolbar">
      <a-button type="primary" @click="openCreate">
        <template #icon><PlusOutlined /></template>
        新建知识库
      </a-button>
    </div>
    <a-table
      :data-source="list"
      :columns="columns"
      row-key="id"
      :loading="loading"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'default'">
            {{ record.status === 1 ? '启用' : '停用' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
            <ConfirmDelete title="删除后其下文档将一并移除，确定？" @confirm="handleDelete(record)" />
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="modalOpen" :title="editing?.id ? '编辑知识库' : '新建知识库'" @ok="handleSave">
      <a-form layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" placeholder="如：《数据挖掘》教材" />
        </a-form-item>
        <a-form-item label="所属课程">
          <a-input v-model:value="form.course" placeholder="如：数据挖掘" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="3" placeholder="知识库描述" />
        </a-form-item>
        <a-form-item label="状态">
          <a-switch v-model:checked="enabledFlag" checked-children="启用" un-checked-children="停用" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
/**
 * 知识库管理（管理员）。
 */
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import ConfirmDelete from '@/components/common/ConfirmDelete.vue'
import { list1 as apiList, create1 as apiCreate, update1 as apiUpdate, delete1 as apiDelete } from '@/api/traceqa/zhishiku'
import type { KnowledgeBaseDTO } from '@/utils/api-types'

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '课程', dataIndex: 'course', key: 'course' },
  { title: '描述', dataIndex: 'description', key: 'description' },
  { title: '状态', key: 'status' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '操作', key: 'action' }
]

const loading = ref(false)
const list = ref<KnowledgeBaseDTO[]>([])
const modalOpen = ref(false)
const editing = ref<KnowledgeBaseDTO | null>(null)
const enabledFlag = ref(true)
const form = reactive({ name: '', course: '', description: '' })

async function load(): Promise<void> {
  loading.value = true
  try {
    const res = await apiList()
    list.value = res.data ?? []
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editing.value = null
  form.name = ''
  form.course = ''
  form.description = ''
  enabledFlag.value = true
  modalOpen.value = true
}

function openEdit(record: KnowledgeBaseDTO): void {
  editing.value = record
  form.name = record.name ?? ''
  form.course = record.course ?? ''
  form.description = record.description ?? ''
  enabledFlag.value = record.status === 1
  modalOpen.value = true
}

async function handleSave(): Promise<void> {
  if (!form.name.trim()) {
    message.warning('请输入知识库名称')
    return
  }
  try {
    if (editing.value?.id) {
      await apiUpdate(
        { id: editing.value.id },
        {
          name: form.name.trim(),
          course: form.course,
          description: form.description,
          status: enabledFlag.value ? 1 : 0
        }
      )
    } else {
      await apiCreate({
        name: form.name.trim(),
        course: form.course,
        description: form.description,
        status: enabledFlag.value ? 1 : 0
      })
    }
    message.success('保存成功')
    modalOpen.value = false
    await load()
  } catch (err) {
    message.error((err as Error).message || '保存失败')
  }
}

async function handleDelete(record: KnowledgeBaseDTO): Promise<void> {
  try {
    await apiDelete({ id: record.id })
    message.success('已删除')
    await load()
  } catch (err) {
    message.error((err as Error).message || '删除失败')
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
</style>

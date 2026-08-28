<template>
  <div class="announcement-manager">
    <a-space style="margin-bottom: 12px">
      <a-button type="primary" @click="openEdit(null)">新增公告</a-button>
    </a-space>
    <a-table :data-source="list" :columns="columns" row-key="id" size="small" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'enabled'">
          <a-tag :color="record.enabled === 1 ? 'green' : 'default'">{{ record.enabled === 1 ? '展示中' : '已停用' }}</a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button size="small" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm title="删除该公告？" @confirm="remove(record)">
              <a-button size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="editOpen" :title="form.id ? '编辑公告' : '新增公告'" :footer="null">
      <a-form layout="vertical">
        <a-form-item label="公告标题">
          <a-input v-model:value="form.title" placeholder="请输入公告标题" />
        </a-form-item>
        <a-form-item label="公告内容">
          <a-textarea v-model:value="form.content" :rows="4" placeholder="请输入公告内容" />
        </a-form-item>
        <a-form-item label="是否展示">
          <a-switch v-model:checked="form.enabled" :checked-value="1" :un-checked-value="0" />
        </a-form-item>
        <a-button type="primary" @click="save">保存</a-button>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
/**
 * 公告管理（管理员）：维护系统公告。
 */
import { message } from 'ant-design-vue'
import { getAuthHeaders } from '@/utils/request'

const list = ref<any[]>([])
const editOpen = ref(false)
const form = reactive<{ id?: number; title: string; content: string; enabled: number }>({
  id: undefined,
  title: '',
  content: '',
  enabled: 1
})

const columns = [
  { title: '标题', dataIndex: 'title' },
  { title: '内容', dataIndex: 'content', ellipsis: true },
  { title: '状态', key: 'enabled', width: 90 },
  { title: '更新时间', dataIndex: 'updateTime', width: 180 },
  { title: '操作', key: 'action', width: 140 }
]

async function load(): Promise<void> {
  const res = await fetch('/api/announcement', { headers: getAuthHeaders() })
  const json = await res.json()
  list.value = json.data ?? []
}

function openEdit(record: any): void {
  Object.assign(form, record ? { id: record.id, title: record.title, content: record.content, enabled: record.enabled } : { id: undefined, title: '', content: '', enabled: 1 })
  editOpen.value = true
}

async function save(): Promise<void> {
  if (!form.title.trim()) {
    message.warning('请输入公告标题')
    return
  }
  const res = await fetch('/api/announcement', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
    body: JSON.stringify(form)
  })
  const json = await res.json()
  if (json.code === 200) {
    message.success('公告已保存')
    editOpen.value = false
    await load()
  } else {
    message.error(json.msg || '保存失败')
  }
}

async function remove(record: any): Promise<void> {
  const res = await fetch(`/api/announcement/${record.id}`, { method: 'DELETE', headers: getAuthHeaders() })
  const json = await res.json()
  if (json.code === 200) {
    message.success('已删除')
    await load()
  }
}

onMounted(load)
</script>
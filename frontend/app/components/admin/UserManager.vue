<template>
  <div>
    <a-tabs>
      <a-tab-pane key="users" tab="用户管理">
        <a-table :data-source="users" :columns="userColumns" row-key="id" :loading="userLoading" :pagination="false">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-switch
                  :checked="record.status === 1"
                  checked-children="启用"
                  un-checked-children="禁用"
                  @change="(checked: boolean) => handleToggleStatus(record, checked)"
              />
            </template>
            <template v-else-if="column.key === 'role'">
              <a-select
                  :value="record.roleCode"
                  style="width: 120px"
                  size="small"
                  :options="roleOptions"
                  @change="(val: string) => handleChangeRole(record, val)"
              />
            </template>
          </template>
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="roles" tab="角色与权限">
        <a-table :data-source="roles" :columns="roleColumns" row-key="id" :loading="roleLoading" :pagination="false">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'permissions'">
              <a-select
                  mode="tags"
                  :value="splitPermissions(record.permissions)"
                  style="width: 100%"
                  placeholder="输入权限码后回车添加"
                  @change="(val: string[]) => handleChangePermissions(record, val)"
              />
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
/**
 * 用户与角色管理（RBAC）：启用/禁用用户、变更角色、编辑角色权限。
 */
import {message} from 'ant-design-vue'
import {pageUsers, updateStatus, updateRole, listRoles, updateRole1} from '@/api/traceqa/guanlihoutai'
import type {AdminUserVO, RoleDTO} from '@/utils/api-types'

const userColumns = [
  {title: 'ID', dataIndex: 'id', key: 'id'},
  {title: '用户名', dataIndex: 'username', key: 'username'},
  {title: '昵称', dataIndex: 'nickname', key: 'nickname'},
  {title: '角色', key: 'role'},
  {title: '状态', key: 'status'},
  {title: '注册时间', dataIndex: 'createTime', key: 'createTime'}
]

const roleColumns = [
  {title: '角色编码', dataIndex: 'code', key: 'code'},
  {title: '角色名称', dataIndex: 'name', key: 'name'},
  {title: '权限码', key: 'permissions'},
  {title: '描述', dataIndex: 'description', key: 'description'}
]

const users = ref<AdminUserVO[]>([])
const roles = ref<RoleDTO[]>([])
const userLoading = ref(false)
const roleLoading = ref(false)

const roleOptions = computed<{ value: string; label: string }[]>(() =>
    roles.value.map((r) => ({value: r.code ?? '', label: r.name ?? r.code ?? ''}))
)

/** 逗号分隔权限字符串拆为数组 */
function splitPermissions(permissions?: string): string[] {
  return (permissions || '').split(',').map((p) => p.trim()).filter(Boolean)
}

async function loadUsers(): Promise<void> {
  userLoading.value = true
  try {
    const res = await pageUsers({})
    users.value = res.data?.records ?? []
  } finally {
    userLoading.value = false
  }
}

async function loadRoles(): Promise<void> {
  roleLoading.value = true
  try {
    const res = await listRoles()
    roles.value = res.data ?? []
  } finally {
    roleLoading.value = false
  }
}

async function handleToggleStatus(record: AdminUserVO, checked: boolean): Promise<void> {
  try {
    await updateStatus({id: record.id || 0, status: checked ? 1 : 0})
    record.status = checked ? 1 : 0
    await message.success('状态已更新')
  } catch (err) {
    await message.error((err as Error).message || '操作失败')
  }
}

async function handleChangeRole(record: AdminUserVO, roleCode: string): Promise<void> {
  try {
    await updateRole({id: record.id || 0}, {roleCode})
    record.roleCode = roleCode
    await message.success('角色已变更')
  } catch (err) {
    await message.error((err as Error).message || '操作失败')
  }
}

async function handleChangePermissions(record: RoleDTO, permissions: string[]): Promise<void> {
  const normalized = permissions.join(',')
  try {
    await updateRole1(
        {id: record.id || 0},
        {
          code: record.code,
          name: record.name,
          permissions: normalized,
          description: record.description
        }
    )
    record.permissions = normalized
    await message.success('权限已更新')
  } catch (err) {
    await message.error((err as Error).message || '操作失败')
  }
}

onMounted(async () => {
  await Promise.all([loadUsers(), loadRoles()])
})
</script>

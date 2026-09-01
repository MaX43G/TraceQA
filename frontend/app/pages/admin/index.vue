<template>
  <div class="admin-page">
    <a-card :bordered="false" class="admin-card">
      <template #title>
        <a-space>
          <ToolOutlined/>
          <span>管理后台</span>
        </a-space>
      </template>
      <template #extra>
        <a-tag color="blue">仅管理员可见</a-tag>
      </template>
      <a-tabs v-model:active-key="activeKey" class="admin-tabs">
        <a-tab-pane key="kb" tab="知识库管理">
          <KnowledgeBaseManager v-if="activeKey === 'kb'"/>
        </a-tab-pane>
        <a-tab-pane key="doc" tab="文档管理">
          <DocumentManager v-if="activeKey === 'doc'"/>
        </a-tab-pane>
        <a-tab-pane key="prompt" tab="系统提示词">
          <PromptManager v-if="activeKey === 'prompt'"/>
        </a-tab-pane>
        <a-tab-pane key="user" tab="用户与权限">
          <UserManager v-if="activeKey === 'user'"/>
        </a-tab-pane>
        <a-tab-pane key="lightrag" tab="LightRAG 管理">
          <LightRagManager v-if="activeKey === 'lightrag'"/>
        </a-tab-pane>
        <a-tab-pane key="announcement" tab="公告管理">
          <AnnouncementManager v-if="activeKey === 'announcement'"/>
        </a-tab-pane>
        <a-tab-pane key="monitor" tab="系统监控">
          <MonitorPanel v-if="activeKey === 'monitor'"/>
        </a-tab-pane>
        <a-tab-pane key="resource" tab="系统资源">
          <SystemResourcePanel v-if="activeKey === 'resource'"/>
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 管理后台页面（RBAC：仅 ADMIN 角色可访问）。
 */
import {useAuthStore} from '@/stores/auth'
import {ToolOutlined} from '@ant-design/icons-vue'
import KnowledgeBaseManager from '@/components/admin/KnowledgeBaseManager.vue'
import DocumentManager from '@/components/admin/DocumentManager.vue'
import PromptManager from '@/components/admin/PromptManager.vue'
import UserManager from '@/components/admin/UserManager.vue'
import LightRagManager from '@/components/admin/LightRagManager.vue'
import MonitorPanel from '@/components/admin/MonitorPanel.vue'
import AnnouncementManager from '@/components/admin/AnnouncementManager.vue'
import SystemResourcePanel from '@/components/admin/SystemResourcePanel.vue'

useSeoMeta({
  title: '管理后台 - 溯知 · TraceQA',
  description: '溯知知识库、文档、系统提示词、用户权限与 LightRAG 引擎管理',
  robots: 'noindex,nofollow'
})

const auth = useAuthStore()
const activeKey = ref('kb')

onMounted(async () => {
  await auth.fetchMe()
  // 非管理员跳回首页
  if (!auth.isAdmin) {
    await navigateTo('/')
  }
})
</script>

<style scoped>
.admin-page {
  max-width: 1200px;
  margin: 24px auto;
  padding: 0 24px;
}

.admin-card {
  border-radius: 14px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
}

@media (max-width: 768px) {
  .admin-page {
    padding: 0 8px;
    margin: 12px auto;
  }
}
</style>

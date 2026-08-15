<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
</template>

<script setup lang="ts">
// 根组件：注入路由通知与全局布局
useSeoMeta({
  title: '溯知 · TraceQA - 数据挖掘课程智能问答平台',
  description: '基于知识图谱与向量检索的《数据挖掘》课程智能问答平台'
})
</script>

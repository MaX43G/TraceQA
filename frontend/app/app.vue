<template>
  <a-config-provider :locale="zhCN">
    <NuxtLayout>
      <NuxtPage/>
    </NuxtLayout>
    <LoadingScreen/>
  </a-config-provider>
</template>

<script setup lang="ts">
/**
 * 根组件：注入 antd 中文 locale（按钮/弹窗/分页等显示中文），路由通知与全局布局，
 * 并挂载首屏/路由切换加载遮罩。
 */
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import LoadingScreen from "~/components/common/LoadingScreen.vue";

useSeoMeta({
  title: '溯知 · TraceQA - 数据挖掘课程智能问答平台',
  description: '基于知识图谱与向量检索的《数据挖掘》课程智能问答平台'
})
</script>

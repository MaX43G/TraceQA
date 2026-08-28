<template>
  <transition name="tq-splash">
    <div v-if="visible" class="tq-splash">
      <div class="tq-splash__logo">溯</div>
      <div class="tq-splash__title">溯知 · TraceQA</div>
      <div class="tq-splash__bar">
        <div class="tq-splash__bar-inner" :style="{ width: progress + '%' }" />
      </div>
      <div class="tq-splash__tip">数据挖掘智能问答平台</div>
    </div>
  </transition>
</template>

<script setup lang="ts">
/**
 * 首屏/路由切换加载遮罩：随 Nuxt 加载指示器进度显示，避免空白等待。
 */
const loading = useLoadingIndicator()
const visible = ref(false)
const progress = ref(0)
const first = ref(true)

onMounted(() => {
  // 首次客户端挂载显示一小段品牌首屏
  visible.value = true
  setTimeout(() => {
    first.value = false
    if (!loading.isLoading) {
      visible.value = false
    }
  }, 900)
})

// 路由切换：显示遮罩并跟踪进度
watch(() => loading.isLoading, (v) => {
  if (v) visible.value = true
})
watch(() => loading.progress, (v) => {
  progress.value = v
  if (v >= 100) {
    setTimeout(() => { visible.value = false }, 250)
  }
})

onBeforeUnmount(() => {
  first.value = false
})
</script>

<style scoped>
.tq-splash {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  background: linear-gradient(180deg, #f0f5ff 0%, #f5f7fb 60%, #fff 100%);
}

.tq-splash__logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: 18px;
  background: linear-gradient(135deg, #1677ff, #06b6d4);
  color: #fff;
  font-size: 36px;
  font-weight: 700;
  box-shadow: 0 8px 24px rgba(22, 119, 255, 0.25);
}

.tq-splash__title {
  font-size: 22px;
  font-weight: 600;
  color: #1f2329;
}

.tq-splash__tip {
  font-size: 12px;
  color: #86909c;
}

.tq-splash__bar {
  width: 180px;
  height: 4px;
  border-radius: 4px;
  background: #e6f4ff;
  overflow: hidden;
}

.tq-splash__bar-inner {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #1677ff, #06b6d4);
  transition: width 0.2s ease;
}

</style>
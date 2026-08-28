<template>
  <transition name="tq-splash">
    <div v-if="visible" class="tq-splash">
      <div class="tq-splash__logo">溯</div>
      <div class="tq-splash__title">溯知 · TraceQA</div>
      <div class="tq-splash__bar">
        <div class="tq-splash__bar-inner" :style="{ width: progress + '%' }"/>
      </div>
      <div class="tq-splash__tip">数据挖掘智能问答平台</div>
    </div>
  </transition>
</template>

<script setup lang="ts">
/**
 * 首屏/路由切换加载遮罩。
 */
const loading = useLoadingIndicator()
const visible = ref(false)
const progress = ref(0)
let hideTimer: ReturnType<typeof setTimeout> | null = null

function hideAfter(delay: number): void {
  if (hideTimer) clearTimeout(hideTimer)
  hideTimer = setTimeout(() => {
    visible.value = false
    hideTimer = null
  }, delay)
}

onMounted(() => {
  visible.value = true
  if (document.readyState === 'complete') {
    hideAfter(300)
  } else {
    window.addEventListener('load', () => hideAfter(300), {once: true})
  }
})

// 路由切换（SPA）：加载开始显示，加载结束隐藏
watch(() => loading.isLoading.value, (v) => {
  if (v) {
    visible.value = true
  } else {
    hideAfter(150)
  }
})

// 进度到 100 隐藏
watch(() => loading.progress.value, (p) => {
  progress.value = p
  if (p >= 100) {
    hideAfter(200)
  }
})

onBeforeUnmount(() => {
  if (hideTimer) clearTimeout(hideTimer)
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
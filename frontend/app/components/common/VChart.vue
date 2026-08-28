<template>
  <div ref="el" class="v-chart" :style="{ height: height || '260px' }"/>
</template>

<script setup lang="ts">
/**
 * 可复用 ECharts 图表容器。
 * 传入 option 即可渲染；容器尺寸变化自动 resize，组件销毁时释放实例。
 */
import * as echarts from 'echarts'
import type {EChartsOption} from 'echarts'

const props = defineProps<{
  option: object
  height?: string
}>()

const el = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null
let observer: ResizeObserver | null = null

function render(): void {
  if (!el.value) {
    return
  }
  if (!chart) {
    chart = echarts.init(el.value)
  }
  chart.setOption(props.option as EChartsOption, true)
}

onMounted(() => {
  render()
  observer = new ResizeObserver(() => chart?.resize())
  if (el.value) {
    observer.observe(el.value)
  }
})

watch(() => props.option, render, {deep: true})

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = null
})

defineExpose({getChart: () => chart})
</script>

<style scoped>
.v-chart {
  width: 100%;
}
</style>
<template>
  <a-modal v-model:open="open" title="裁剪头像" :footer="null" :width="520" destroy-on-close>
    <a-segmented v-model:value="aspect" :options="aspectOptions" block style="margin-bottom: 10px"/>
    <div class="cropper-wrap">
      <img ref="imgEl" :src="imageUrl" alt="avatar"/>
    </div>
    <div class="cropper-controls">
      <a-tooltip title="旋转">
        <a-button type="text" @click="rotate(-90)">
          <template #icon>
            <RotateLeftOutlined/>
          </template>
        </a-button>
      </a-tooltip>
      <a-tooltip title="右旋转">
        <a-button type="text" @click="rotate(90)">
          <template #icon>
            <RotateRightOutlined/>
          </template>
        </a-button>
      </a-tooltip>
      <a-tooltip title="缩小">
        <a-button type="text" @click="zoom(-0.1)">
          <template #icon>
            <ZoomOutOutlined/>
          </template>
        </a-button>
      </a-tooltip>
      <a-tooltip title="放大">
        <a-button type="text" @click="zoom(0.1)">
          <template #icon>
            <ZoomInOutlined/>
          </template>
        </a-button>
      </a-tooltip>
    </div>
    <div class="cropper-actions">
      <a-button @click="open = false">取消</a-button>
      <a-button type="primary" @click="confirm">确定</a-button>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
/**
 * 头像裁剪弹窗：基于 cropperjs，支持参考线/网格、比例切换、旋转、缩放等高级裁剪。
 */
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'
import {RotateLeftOutlined, RotateRightOutlined, ZoomInOutlined, ZoomOutOutlined} from '@ant-design/icons-vue'

const open = defineModel<boolean>('open', {default: false})

const props = defineProps<{
  imageUrl?: string
}>()

const emit = defineEmits<{
  (e: 'crop', blob: Blob): void
}>()

const imgEl = ref<HTMLImageElement | null>(null)
let cropper: Cropper | null = null

const aspectOptions = [
  {label: '方形 1:1', value: 1},
  {label: '竖版 3:4', value: 3 / 4},
  {label: '横版 4:3', value: 4 / 3},
  {label: '自由', value: NaN}
]
const aspect = ref<number>(1)

watch(
    open,
    (isOpen) => {
      if (isOpen) {
        nextTick(() => init())
      } else {
        cropper?.destroy()
        cropper = null
      }
    },
    {deep: true}
)

watch(aspect, (val) => {
  cropper?.setAspectRatio(Number.isNaN(val) ? NaN : val)
})

function init(): void {
  if (!imgEl.value) {
    return
  }
  cropper = new Cropper(imgEl.value, {
    aspectRatio: aspect.value,
    viewMode: 1,
    guides: true,
    highlight: false,
    dragMode: 'move',
    autoCropArea: 0.8,
    center: true,
    responsive: true
  })
}

function rotate(deg: number): void {
  cropper?.rotate(deg)
}

function zoom(factor: number): void {
  cropper?.zoom(factor)
}

function confirm(): void {
  if (!cropper) {
    return
  }
  cropper.getCroppedCanvas({width: 256, height: 256}).toBlob(
      (blob) => {
        if (blob) {
          emit('crop', blob)
          open.value = false
        }
      },
      'image/jpeg',
      0.9
  )
}
</script>

<style scoped>
.cropper-wrap {
  height: 360px;
  background: #000;
}

.cropper-wrap :deep(img) {
  display: block;
  max-width: 100%;
}

.cropper-controls {
  display: flex;
  justify-content: center;
  gap: 4px;
  margin-top: 8px;
}

.cropper-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}
</style>
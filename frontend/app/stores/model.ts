/**
 * 模型选择状态（Pinia Store）。
 *
 * <p>管理服务端可用模型列表与「自定义模型」（可添加多个）。
 * 自定义模型（baseUrl/apiKey/model）仅存储在浏览器 localStorage，
 * 随聊天请求发送给自建后端做本次调用，不上传任何第三方云端。</p>
 */
import { defineStore } from 'pinia'
import { list2 as fetchModels } from '@/api/traceqa/moxing'
import type { ModelVO } from '@/utils/api-types'

/** 自定义模型配置（OpenAI 兼容格式，仅存本地） */
export interface CustomModel {
  /** 唯一标识（用于选择） */
  id: string
  /** 展示名称 */
  name: string
  /** 模型标识 */
  model: string
  /** OpenAI 兼容接口地址 */
  baseUrl: string
  /** API Key */
  apiKey: string
}

/** 自定义模型 localStorage 键 */
export const CUSTOM_MODELS_KEY = 'tq_custom_models'
/** 当前选中模型键 */
export const SELECTED_MODEL_KEY = 'tq_selected_model'

/** 生成自定义模型 ID */
function genId(): string {
  return `cm_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`
}

export const useModelStore = defineStore('model', {
  state: () => ({
    /** 服务端可用模型列表 */
    serverModels: [] as ModelVO[],
    /** 自定义模型列表（本地存储，可多个） */
    customModels: [] as CustomModel[],
    /** 当前选中：'default' 表示默认模型，'custom:<id>' 表示某个自定义模型 */
    selected: 'default' as string,
    /** 是否已加载服务端模型 */
    loaded: false
  }),

  getters: {
    /** 当前生效的自定义模型配置（未选中自定义时返回 null） */
    activeCustomConfig(state): { model: string; baseUrl: string; apiKey: string } | null {
      const custom = state.customModels.find((m) => `custom:${m.id}` === state.selected)
      if (custom) {
        return { model: custom.model, baseUrl: custom.baseUrl, apiKey: custom.apiKey }
      }
      return null
    },

    /** 当前选中的服务端模型名（选中默认模型时返回 null，由后端走默认配置） */
    activeServerModel(state): string | null {
      if (!state.selected.startsWith('server:')) {
        return null
      }
      return state.selected.slice('server:'.length)
    }
  },

  actions: {
    /** 从 localStorage 恢复自定义模型与选中状态 */
    initFromStorage(): void {
      if (typeof window === 'undefined') {
        return
      }
      const raw = window.localStorage.getItem(CUSTOM_MODELS_KEY)
      if (raw) {
        try {
          this.customModels = JSON.parse(raw) as CustomModel[]
        } catch {
          this.customModels = []
        }
      }
      const selected = window.localStorage.getItem(SELECTED_MODEL_KEY)
      if (selected === 'default' || (typeof selected === 'string' && (selected.startsWith('server:') || selected.startsWith('custom:')))) {
        this.selected = selected
      }
    },

    /** 加载服务端可用模型 */
    async loadServerModels(): Promise<void> {
      if (this.loaded) {
        return
      }
      try {
        const res = await fetchModels()
        this.serverModels = res.data ?? []
      } catch {
        this.serverModels = []
      } finally {
        this.loaded = true
      }
    },

    /** 添加自定义模型（写入 localStorage）并选中 */
    addCustomModel(config: Omit<CustomModel, 'id'>): void {
      const model: CustomModel = { ...config, id: genId() }
      this.customModels.push(model)
      this.selected = `custom:${model.id}`
      this.persist()
    },

    /** 删除自定义模型；若删除的是当前选中项则回到默认 */
    removeCustomModel(id: string): void {
      this.customModels = this.customModels.filter((m) => m.id !== id)
      if (this.selected === `custom:${id}`) {
        this.selected = 'default'
      }
      this.persist()
    },

    /** 切换选中模型 */
    select(key: string): void {
      this.selected = key
      this.persist()
    },

    /** 持久化到 localStorage */
    persist(): void {
      if (typeof window === 'undefined') {
        return
      }
      window.localStorage.setItem(CUSTOM_MODELS_KEY, JSON.stringify(this.customModels))
      window.localStorage.setItem(SELECTED_MODEL_KEY, this.selected)
    }
  }
})

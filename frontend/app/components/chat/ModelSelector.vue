<template>
  <div class="model-selector">
    <a-select
        :value="modelStore.selected"
        style="width: 240px"
        @change="handleChange"
    >
      <template #dropdownRender="{ menuNode }">
        <div>
          <component :is="menuNode" v-if="menuNode"/>
          <a-divider style="margin: 4px 0"/>
          <div class="model-dropdown-add">
            <a-button type="text" size="small" block @mousedown.prevent @click="openAdd">
              <PlusOutlined/>
              添加自定义模型…
            </a-button>
          </div>
        </div>
      </template>
      <a-select-option
          v-for="m in modelStore.serverModels"
          :key="m.isDefault ? 'default' : `server:${m.model}`"
          :value="m.isDefault ? 'default' : `server:${m.model}`"
      >
        <RobotOutlined/>
        {{ m.name || m.model }}
        <a-tag v-if="m.isDefault" size="small" color="blue" style="margin-left: 4px">默认</a-tag>
      </a-select-option>
      <a-select-option
          v-for="cm in modelStore.customModels"
          :key="`custom:${cm.id}`"
          :value="`custom:${cm.id}`"
      >
        <ApiOutlined/>
        自定义：{{ cm.name }}
      </a-select-option>
    </a-select>

    <a-modal
        v-model:open="modalOpen"
        title="自定义模型（OpenAI 兼容）"
        :confirm-loading="saving"
        ok-text="保存并使用"
        cancel-text="取消"
        @ok="handleSave"
    >
      <a-alert
          type="info"
          show-icon
          message="配置仅保存在当前浏览器本地，仅在本次问答请求中发送给你的后端调用，不会上传到任何第三方云端。"
          class="model-alert"
      />
      <a-form layout="vertical">
        <a-form-item label="接口地址 Base URL" required>
          <a-input v-model:value="form.baseUrl" placeholder="如 https://api.openai.com/v1"/>
        </a-form-item>
        <a-form-item label="API Key" required>
          <a-input-password v-model:value="form.apiKey" placeholder="OpenAI 格式 API Key"/>
        </a-form-item>
        <a-form-item label="模型名称" required>
          <a-input v-model:value="form.model" placeholder="如 gpt-4o-mini"/>
        </a-form-item>
        <a-form-item label="展示名称">
          <a-input v-model:value="form.name" placeholder="如：我的本地模型（留空用模型名）"/>
        </a-form-item>
      </a-form>

      <!-- 已保存的自定义模型管理 -->
      <a-divider plain>已保存的自定义模型</a-divider>
      <div v-if="modelStore.customModels.length === 0" class="model-empty">暂无自定义模型</div>
      <div v-for="cm in modelStore.customModels" :key="cm.id" class="model-item">
        <span class="model-item__name">{{ cm.name }}（{{ cm.model }}）</span>
        <ConfirmDelete title="确定删除该自定义模型？" @confirm="handleRemove(cm.id)"/>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
/**
 * 模型选择器：默认模型 + 多个自定义 OpenAI 兼容模型（本地存储，可增删切换）。
 */
import {RobotOutlined, ApiOutlined, PlusOutlined} from '@ant-design/icons-vue'
import {message} from 'ant-design-vue'
import ConfirmDelete from '@/components/common/ConfirmDelete.vue'
import {useModelStore} from '@/stores/model'

const modelStore = useModelStore()
const modalOpen = ref(false)
const saving = ref(false)
const form = reactive({baseUrl: '', apiKey: '', model: '', name: ''})

function handleChange(value: string): void {
  if (value === '__add__') {
    return
  }
  modelStore.select(value)
}

/** 打开添加自定义模型弹窗 */
function openAdd(): void {
  form.baseUrl = ''
  form.apiKey = ''
  form.model = ''
  form.name = ''
  modalOpen.value = true
}

async function handleSave(): Promise<void> {
  if (!form.baseUrl.trim() || !form.apiKey.trim() || !form.model.trim()) {
    await message.warning('请填写 Base URL、API Key 与模型名称')
    return
  }
  saving.value = true
  try {
    modelStore.addCustomModel({
      name: form.name.trim() || form.model.trim(),
      model: form.model.trim(),
      baseUrl: form.baseUrl.trim(),
      apiKey: form.apiKey.trim()
    })
    await message.success('已添加并选中自定义模型')
    modalOpen.value = false
  } finally {
    saving.value = false
  }
}

function handleRemove(id: string): void {
  modelStore.removeCustomModel(id)
  message.success('已删除自定义模型')
}
</script>

<style scoped>
.model-selector {
  display: inline-block;
}

.model-alert {
  margin-bottom: 12px;
}

.model-empty {
  color: #86909c;
  font-size: 12px;
  text-align: center;
  padding: 8px 0;
}

.model-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;
}

.model-item__name {
  font-size: 13px;
  color: #1f2329;
}

.model-dropdown-add {
  padding: 4px 8px;
}
</style>

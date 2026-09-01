<template>
  <div class="system-resource-panel">
    <a-alert v-if="!loading && !data" type="error" show-icon message="系统资源数据加载失败"
             style="margin-bottom: 12px"/>

    <!-- Docker socket 提示 -->
    <a-alert
        v-if="data && data.docker && !data.docker.available"
        type="warning"
        show-icon
        message="Docker socket 未挂载，无法检测/清理 Docker 空间"
        description="如需启用，请在 docker-compose 中将 /var/run/docker.sock 挂载到 admin-service 容器。"
        style="margin-bottom: 12px"
    />

    <template v-if="data">
      <!-- 指标卡片 -->
      <a-row :gutter="16">
        <a-col :xs="12" :md="6">
          <a-card size="small" class="metric-card">
            <div class="metric-label">CPU 使用率</div>
            <a-progress type="dashboard" :percent="cpuPercent" :color="usageColor(cpuPercent)" :size="110"/>
          </a-card>
        </a-col>
        <a-col :xs="12" :md="6">
          <a-card size="small" class="metric-card">
            <div class="metric-label">内存使用率</div>
            <a-progress type="dashboard" :percent="memPercent" :color="usageColor(memPercent)" :size="110"/>
            <div class="metric-sub">{{ data.memory?.usedHuman }} / {{ data.memory?.totalHuman }}</div>
          </a-card>
        </a-col>
        <a-col :xs="12" :md="6">
          <a-card size="small" class="metric-card">
            <div class="metric-label">系统负载 (1min)</div>
            <div class="metric-value">{{ data.loadAverage?.toFixed(2) ?? '-' }}</div>
            <div class="metric-sub">CPU 核数：{{ data.host?.cpuCores ?? '-' }}</div>
          </a-card>
        </a-col>
        <a-col :xs="12" :md="6">
          <a-card size="small" class="metric-card">
            <div class="metric-label">运行时长</div>
            <div class="metric-value">{{ fmtUptime(data.uptimeSeconds) }}</div>
            <div class="metric-sub">Docker：{{ data.host?.dockerVersion ?? '-' }}</div>
          </a-card>
        </a-col>
      </a-row>

      <!-- 磁盘 + 设备信息 -->
      <a-row :gutter="16" style="margin-top: 16px">
        <a-col :xs="24" :md="14">
          <a-card size="small" title="磁盘占用">
            <a-table
                :data-source="diskRows"
                :columns="diskColumns"
                row-key="mount"
                size="small"
                :pagination="false"
            >
              <template #bodyCell="{column, record}">
                <template v-if="column.key === 'size'">
                  {{ record.usedHuman }} / {{ record.totalHuman }}（剩余 {{ record.freeHuman }}）
                </template>
                <template v-if="column.key === 'bar'">
                  <a-progress :percent="record.usedPercent" :color="usageColor(record.usedPercent)" :size="[100, 14]"/>
                </template>
              </template>
            </a-table>
          </a-card>
        </a-col>
        <a-col :xs="24" :md="10">
          <a-card size="small" title="设备基本信息">
            <a-descriptions :column="1" size="small">
              <a-descriptions-item label="主机名">{{ data.host?.hostname || '-' }}</a-descriptions-item>
              <a-descriptions-item label="操作系统">{{ data.host?.osName || '-' }}</a-descriptions-item>
              <a-descriptions-item label="架构">{{ data.host?.osArch || '-' }}</a-descriptions-item>
              <a-descriptions-item label="内核版本">{{ data.host?.kernelVersion || '-' }}</a-descriptions-item>
              <a-descriptions-item label="Docker 版本">{{ data.host?.dockerVersion || '-' }}</a-descriptions-item>
              <a-descriptions-item label="CPU 核数">{{ data.host?.cpuCores ?? '-' }}</a-descriptions-item>
              <a-descriptions-item label="Java 版本">{{ data.host?.javaVersion || '-' }}</a-descriptions-item>
              <a-descriptions-item label="容器主机名">{{ data.host?.containerHostname || '-' }}</a-descriptions-item>
            </a-descriptions>
          </a-card>
        </a-col>
      </a-row>

      <!-- 清理 -->
      <a-card size="small" title="清理系统无用资源" style="margin-top: 16px">
        <a-space direction="vertical" style="width: 100%">
          <a-descriptions :column="2" size="small">
            <a-descriptions-item label="Docker 占用空间">{{ data.docker?.totalSizeHuman ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="可回收空间">
              <a-tag v-if="data.docker?.available" :color="data.docker.reclaimableBytes ? 'orange' : 'green'">
                {{ data.docker?.reclaimableHuman ?? '-' }}
              </a-tag>
              <span v-else>不可用</span>
            </a-descriptions-item>
          </a-descriptions>
          <a-space>
            <a-button type="primary" danger :loading="cleaning" :disabled="!data.docker?.available"
                      @click="confirmClean">
              <template #icon>
                <DeleteOutlined/>
              </template>
              清理 Docker 无用资源
            </a-button>
            <span class="tip">将回收：未使用镜像 / 构建缓存 / 已停止容器 / 未使用数据卷</span>
          </a-space>
        </a-space>
      </a-card>
    </template>

    <a-modal
        v-model:open="cleanOpen"
        title="确认清理"
        :confirm-loading="cleaning"
        @ok="doClean"
        @cancel="cleanOpen = false"
    >
      <p>确定要执行 Docker 无用资源清理吗？</p>
      <p>将删除：未使用镜像、构建缓存、已停止容器、未使用数据卷（不会删除正在运行的容器与使用中的数据）。</p>
      <p v-if="data?.docker?.reclaimableBytes">预计可回收约 <b>{{ data.docker.reclaimableHuman }}</b>。</p>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
/**
 * 系统资源检测面板：CPU / 内存 / 磁盘占用、设备基础信息与 Docker 无用资源清理（管理员）。
 * 每 5 秒自动刷新。
 */
import {getAuthHeaders} from '@/utils/request'
import {DeleteOutlined} from '@ant-design/icons-vue'
import {useIntervalFn} from '@vueuse/core'
import {message} from 'ant-design-vue'

interface DiskInfo {
  mount: string
  type: string
  usedPercent: number
  totalHuman: string
  usedHuman: string
  freeHuman: string
}

interface SystemData {
  cpu?: { percent?: number; processPercent?: number }
  memory?: {
    totalHuman?: string
    usedHuman?: string
    freeHuman?: string
    usedPercent?: number
  }
  disk?: DiskInfo[]
  loadAverage?: number
  uptimeSeconds?: number
  host?: {
    hostname?: string
    osName?: string
    osArch?: string
    kernelVersion?: string
    dockerVersion?: string
    cpuCores?: number
    javaVersion?: string
    containerHostname?: string
  }
  docker?: {
    available: boolean
    totalSizeHuman?: string
    reclaimableHuman?: string
    reclaimableBytes?: number
  }
}

const data = ref<SystemData | null>(null)
const loading = ref(true)
const cleaning = ref(false)
const cleanOpen = ref(false)

const cpuPercent = computed<number>(() => Math.round(data.value?.cpu?.percent ?? 0))
const memPercent = computed<number>(() => Math.round(data.value?.memory?.usedPercent ?? 0))

const diskRows = computed<DiskInfo[]>(() => data.value?.disk ?? [])
const diskColumns = [
  {title: '挂载点', dataIndex: 'mount', key: 'mount'},
  {title: '类型', dataIndex: 'type', key: 'type'},
  {title: '已用 / 总量', key: 'size'},
  {title: '占用率', key: 'bar'}
]

function usageColor(percent: number): string {
  if (percent >= 90) return '#ff4d4f'
  if (percent >= 70) return '#fa8c16'
  return '#52c41a'
}

function fmtUptime(seconds?: number): string {
  if (!seconds) {
    return '-'
  }
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  return d > 0 ? `${d}天 ${h}时 ${m}分` : `${h}时 ${m}分`
}

function confirmClean(): void {
  cleanOpen.value = true
}

async function doClean(): Promise<void> {
  cleaning.value = true
  try {
    const res = await fetch('/api/monitor/system/cleanup', {
      method: 'POST',
      headers: {...getAuthHeaders(), 'Content-Type': 'application/json'},
      body: JSON.stringify({mode: 'docker'})
    })
    const json = (await res.json()) as { code?: number; msg?: string; data?: { freedHuman?: string; message?: string } }
    cleanOpen.value = false
    if (json.code === 200 && json.data) {
      if (json.data.message) {
        await message.info(json.data.message)
      } else {
        await message.success(`清理完成，释放约 ${json.data.freedHuman ?? '0'} 空间`)
      }
      await load()
    } else {
      await message.error(json.msg || '清理失败')
    }
  } catch {
    cleanOpen.value = false
    await message.error('清理失败，请检查网络')
  } finally {
    cleaning.value = false
  }
}

async function load(): Promise<void> {
  try {
    const res = await fetch('/api/monitor/system', {headers: getAuthHeaders()})
    const json = (await res.json()) as { code?: number; data?: SystemData }
    if (json.code === 200 && json.data) {
      data.value = json.data
    }
  } catch {
    // 忽略，保留上次数据
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  useIntervalFn(() => load(), 5000)
})
</script>

<style scoped>
.metric-card {
  text-align: center;
}

.metric-label {
  color: #86909c;
  font-size: 13px;
  margin-bottom: 6px;
}

.metric-value {
  font-size: 22px;
  font-weight: 600;
  color: #1f2329;
}

.metric-sub {
  color: #86909c;
  font-size: 12px;
  margin-top: 6px;
}

.tip {
  color: #86909c;
  font-size: 12px;
}
</style>
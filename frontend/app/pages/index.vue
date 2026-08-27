<template>
  <div class="home">
    <!-- Hero 区 -->
    <section class="home__hero">
      <div class="home__blob home__blob--1" />
      <div class="home__blob home__blob--2" />
      <div class="home__hero-inner">
        <div class="home__logo tq-slide-up">溯</div>
        <h1 class="home__title tq-gradient-text tq-slide-up" style="animation-delay: 80ms">溯知 · TraceQA</h1>
        <p class="home__subtitle tq-slide-up" style="animation-delay: 140ms">《数据挖掘》课程智能问答平台</p>
        <p class="home__desc tq-slide-up" style="animation-delay: 200ms">
          基于知识图谱（LightRAG）与向量检索的增强 RAG 引擎，
          多 Agent 协同、流式思考、引用溯源，助你高效学习数据挖掘。
        </p>
        <a-space :size="16" class="home__cta tq-slide-up" style="animation-delay: 260ms">
          <a-button type="primary" size="large" @click="goChat">
            <template #icon><MessageOutlined /></template>
            开始问答
          </a-button>
          <a-button v-if="!auth.isLoggedIn" size="large" @click="goLogin">登录 / 注册</a-button>
        </a-space>
      </div>
    </section>

    <!-- 功能亮点 -->
    <section class="home__features">
      <h2 class="home__section-title tq-slide-up">核心能力</h2>
      <a-row :gutter="[24, 24]">
        <a-col v-for="(f, idx) in features" :key="f.title" :xs="24" :sm="12" :md="8">
          <a-card :bordered="false" class="home__feature-card tq-glass tq-slide-up" :style="{ animationDelay: `${idx * 60}ms` }">
            <div class="home__feature-icon" :style="{ background: f.color }">
              <component :is="f.icon" />
            </div>
            <h3 class="home__feature-title">{{ f.title }}</h3>
            <p class="home__feature-desc">{{ f.desc }}</p>
          </a-card>
        </a-col>
      </a-row>
    </section>

    <!-- 课程/工作流说明 -->
    <section class="home__workflow">
      <h2 class="home__section-title tq-slide-up">Agent 工作流</h2>
      <p class="home__workflow-desc tq-slide-up">从意图识别到答案生成，全流程可视化，检索过程实时可见。</p>
      <div class="home__workflow-flow">
        <template v-for="(step, i) in workflow" :key="step.label">
          <span class="tq-slide-up" :style="{ animationDelay: `${i * 60}ms` }">
            <component :is="step.icon" />
            {{ step.label }}
          </span>
          <i v-if="i < workflow.length - 1">→</i>
        </template>
      </div>
    </section>

    <footer class="home__footer">
      <p>溯知 · TraceQA —— 《数据挖掘》课程 RAG 智能问答平台</p>
    </footer>
  </div>
</template>

<script setup lang="ts">
/**
 * 首页（品牌介绍 + 核心能力 + 入口）。
 * 公开页面，SSR 渲染利于 SEO。
 */
import {
  MessageOutlined,
  RobotOutlined,
  ApartmentOutlined,
  FileSearchOutlined,
  ApiOutlined,
  ThunderboltOutlined,
  CommentOutlined,
  BranchesOutlined,
  SearchOutlined,
  CompressOutlined
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'

useSeoMeta({
  title: '溯知 · TraceQA - 数据挖掘课程智能问答平台',
  description: '基于知识图谱与向量检索的《数据挖掘》课程智能问答平台，多 Agent 协同、流式思考、引用溯源。'
})

const auth = useAuthStore()

const features = [
  {
    title: '多 Agent 协同',
    desc: 'Agentic 检索策略由模型动态规划，调用合适的检索工具；多 Agent 编排意图识别、检索调度、融合补全、总结生成',
    icon: RobotOutlined,
    color: '#1677ff'
  },
  {
    title: '图谱 + 向量 + 关键词三路检索',
    desc: 'LightRAG 图谱、语义向量与关键词三路并行检索，多路融合 + 二次检索补全 + 语义重排精排，查询更准',
    icon: ApartmentOutlined,
    color: '#722ed1'
  },
  {
    title: '流式思考可视化',
    desc: 'Agent 工作流状态图实时展示，检索过程分步可见，答案打字机式输出',
    icon: ThunderboltOutlined,
    color: '#fa8c16'
  },
  {
    title: '引用溯源',
    desc: '点击即可查看相关文献，学习有据可查',
    icon: FileSearchOutlined,
    color: '#13c2c2'
  },
  {
    title: '模型自由切换',
    desc: '平台内置多款模型一键切换，也支持自填 OpenAI 兼容的私有模型（仅存本地）',
    icon: ApiOutlined,
    color: '#52c41a'
  },
  {
    title: '多轮连续对话',
    desc: '连续问答自动结合上下文，能记住你之前提到的内容，追问更自然',
    icon: CommentOutlined,
    color: '#13c2c2'
  }
]

const workflow = [
  { label: '意图识别', icon: CommentOutlined },
  { label: '策略调度', icon: BranchesOutlined },
  { label: '图谱检索', icon: ApartmentOutlined },
  { label: '向量检索', icon: SearchOutlined },
  { label: '关键词检索', icon: FileSearchOutlined },
  { label: '多路融合', icon: CompressOutlined },
  { label: '二次补全', icon: FileSearchOutlined },
  { label: '语义重排', icon: SearchOutlined },
  { label: '总结生成', icon: ThunderboltOutlined }
]

function goChat(): void {
  navigateTo('/chat')
}

function goLogin(): void {
  navigateTo('/login')
}
</script>

<style scoped>
.home {
  min-height: calc(100vh - 56px);
  background: linear-gradient(180deg, #f0f5ff 0%, #f5f7fb 40%, #fff 100%);
}

.home__hero {
  position: relative;
  overflow: hidden;
  text-align: center;
  padding: 72px 24px 64px;
}

.home__hero::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 140px;
  background: linear-gradient(to bottom, rgba(245, 247, 251, 0) 0%, #f5f7fb 100%);
  pointer-events: none;
  z-index: 0;
}

.home__blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.35;
  animation: blobFloat 8s ease-in-out infinite;
}

.home__blob--1 {
  width: 360px;
  height: 360px;
  background: #69c0ff;
  top: -80px;
  left: -60px;
}

.home__blob--2 {
  width: 320px;
  height: 320px;
  background: #b37feb;
  bottom: -100px;
  right: -40px;
  animation-delay: 3s;
}

@keyframes blobFloat {
  0%,
  100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-18px) scale(1.05);
  }
}

.home__hero-inner {
  position: relative;
  z-index: 1;
}

.home__logo {
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

.home__title {
  margin: 20px 0 8px;
  font-size: 40px;
  color: #1f2329;
}

.home__subtitle {
  margin: 0 0 12px;
  font-size: 18px;
  color: #1677ff;
  font-weight: 600;
}

.home__desc {
  max-width: 560px;
  margin: 0 auto 28px;
  color: #4e5969;
  line-height: 1.8;
}

.home__features {
  max-width: 1100px;
  margin: 0 auto;
  padding: 32px 24px 8px;
}

.home__section-title {
  text-align: center;
  color: #1f2329;
  font-size: 26px;
  margin-bottom: 28px;
}

.home__feature-card {
  height: 100%;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.home__feature-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 24px rgba(22, 119, 255, 0.12);
}

.home__feature-card:hover .home__feature-icon {
  transform: scale(1.08);
}

.home__feature-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  color: #fff;
  font-size: 20px;
  margin-bottom: 12px;
  transition: transform 0.25s ease;
}

.home__feature-title {
  margin: 0 0 8px;
  color: #1f2329;
  font-size: 16px;
}

.home__feature-desc {
  margin: 0;
  color: #4e5969;
  font-size: 13px;
  line-height: 1.7;
}

.home__workflow {
  max-width: 900px;
  margin: 40px auto;
  padding: 0 24px;
  text-align: center;
}

.home__workflow-desc {
  color: #86909c;
  margin-bottom: 24px;
}

.home__workflow-flow {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
}

.home__workflow-flow span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #fff;
  border: 1px solid #d9e4ff;
  color: #1677ff;
  padding: 6px 12px;
  border-radius: 16px;
  transition: all 0.2s;
}

.home__workflow-flow span:hover {
  background: #e6f4ff;
  transform: translateY(-2px);
}

.home__workflow-flow i {
  color: #c9cdd4;
  font-style: normal;
}

.home__footer {
  text-align: center;
  color: #86909c;
  font-size: 12px;
  padding: 24px 0 32px;
}

@media (max-width: 768px) {
  .home__hero {
    padding: 40px 16px 32px;
  }

  .home__title {
    font-size: 30px;
  }
}
</style>
<template>
  <div class="home">
    <!-- Hero 区 -->
    <section class="home__hero">
      <div class="home__hero-inner">
        <div class="home__logo">溯</div>
        <h1 class="home__title">溯知 · TraceQA</h1>
        <p class="home__subtitle">《数据挖掘》课程智能问答平台</p>
        <p class="home__desc">
          基于知识图谱（LightRAG）与向量检索的增强 RAG 引擎，
          多 Agent 协同、流式思考、引用溯源，助你高效学习数据挖掘。
        </p>
        <a-space :size="16" class="home__cta">
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
      <h2 class="home__section-title">核心能力</h2>
      <a-row :gutter="[24, 24]">
        <a-col v-for="f in features" :key="f.title" :xs="24" :sm="12" :md="8">
          <a-card :bordered="false" class="home__feature-card">
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
      <h2 class="home__section-title">Agent 工作流</h2>
      <p class="home__workflow-desc">从意图识别到答案生成，全流程可视化，检索过程实时可见。</p>
      <div class="home__workflow-flow">
        <span>意图识别</span><i>→</i>
        <span>策略调度</span><i>→</i>
        <span>图谱检索</span><i>→</i>
        <span>向量检索</span><i>→</i>
        <span>融合补全</span><i>→</i>
        <span>总结生成</span>
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
  SafetyCertificateOutlined
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'

useSeoMeta({
  title: '溯知 · TraceQA - 数据挖掘课程智能问答平台',
  description: '基于知识图谱与向量检索的《数据挖掘》课程智能问答平台，多 Agent 协同、流式思考、引用溯源。',
  keywords: '数据挖掘,TraceQA,溯知,RAG,知识图谱,智能问答'
})

const auth = useAuthStore()

const features = [
  {
    title: '多 Agent 协同',
    desc: '意图识别、检索调度、重写/HyDE、图谱检索、向量检索、融合补全、总结生成，多 Agent 编排为完整工作流',
    icon: RobotOutlined,
    color: '#1677ff'
  },
  {
    title: '图谱 + 向量双路检索',
    desc: 'LightRAG 知识图谱与语义向量并行检索，RRF 融合 + ReRead 二次补全，召回更准',
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
    desc: '回答逐句标注来源，点击即可查看文献全文，学习有据可查',
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
    title: '异步文档解析',
    desc: '教材/PPT 上传后后台异步构建知识图谱，进度实时追踪',
    icon: SafetyCertificateOutlined,
    color: '#eb2f96'
  }
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
  text-align: center;
  padding: 72px 24px 48px;
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
  background: #fff;
  border: 1px solid #d9e4ff;
  color: #1677ff;
  padding: 6px 12px;
  border-radius: 16px;
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

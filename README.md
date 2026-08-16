# 溯知 · TraceQA

> 《数据挖掘》课程 RAG 智能问答平台 —— 基于知识图谱与向量检索，多 Agent 协同的智能助教系统。

溯知（TraceQA）以课程教材与 PPT 为知识源，通过 **LightRAG（图谱 + 向量双路检索）** 与 **Spring AI Alibaba 多 Agent 协同**，实现「意图识别 → 检索调度 → 检索/搜索 → 总结」的完整工作流；SSE 流式推送思考状态与打字机回答，支持引用溯源、随时中断、模型自由切换、多轮对话与移动端访问。

---

## 目录

- [架构设计](#架构设计)
- [核心特性](#核心特性)
- [模型体系](#模型体系)
- [快速开始（Docker 一键部署）](#快速开始docker-一键部署)
- [本地开发](#本地开发)
- [API 契约](#api-契约)
- [目录结构](#目录结构)
- [常见问题](#常见问题)
- [开发文档](docs/DEVELOP.md)

---

## 架构设计

```
                         ┌──────────────────────────────┐
                         │       浏览器 / 用户            │
                         └──────────────┬───────────────┘
                                        │  SSR + SSE + OpenAPI 客户端
                       ┌────────────────▼────────────────┐
                       │     前端（Nuxt 4 SSR）            │
                       │  Vue3 + TS + Pinia + Antd        │
                       │  首页/问答/登录/管理后台            │
                       │  打字机·状态图·引用·中断·移动端     │
                       │  /api 代理解决跨域                 │
                       └────────────────┬────────────────┘
                                        │  /api/**（前端代理转发）
                       ┌────────────────▼────────────────┐
                       │    后端（Spring Boot 4.1）        │
                       │  统一响应/全局异常/RBAC/熔断降级    │
                       │  MyBatis-Plus(MySQL) · SSE        │
                       │  多 Agent 编排 · 检索增强          │
                       └──────┬─────────────┬─────────────┘
                              │ 多Agent     │ 文档/检索
                    ┌─────────▼──────┐  ┌────▼─────────────┐
                    │ LLM（硅基流动）  │  │ LightRAG 官方     │
                    │ OpenAI 兼容     │  │ Server（图+向量）  │
                    └────────────────┘  └────────┬─────────┘
                                                │ 本地文件系统/图谱
```

**多 Agent 工作流（SSE 实时推送，前端状态图可视化）：**

```
意图识别 → 检索策略调度 → 查询重写与HyDE → 图谱检索(local+global) → 向量检索(多查询) → 融合与补全(RRF+ReRead) → 总结生成
```

调度节点按问题复杂度分流：**简单问题仅向量检索（更快）**，**复杂问题走完整聚合链路**。

## 核心特性

- **统一响应** `{code,msg,data,traceId}` + 全局错误码 + 全局异常处理（绝不外泄堆栈）
- **熔断降级**：LLM 失败自动熔断，逐级降级（Agent → ChatClient → 纯检索 → 友好提示）
- **检索增强**：查询重写、HyDE、图谱(local+global)+向量(多查询)双路、RRF 融合、ReRead 补全
- **LLM 复杂度调度**：简单/复杂问题智能分流，兼顾速度与准确率
- **多轮对话**：历史上下文注入意图识别、查询重写与总结
- **SSE 流式**：思考状态图实时可视化 + 打字机输出 + 随时中断
- **引用溯源**：只显示实际引用的文献，点击查看全文
- **模型自由切换**：平台内置 6 个模型 + 自定义 OpenAI 兼容模型（本地存储）
- **异步文档解析**：上传即返 202，后台构建图谱，进度实时追踪
- **RBAC 管理后台**：用户/角色/知识库/文档/系统提示词
- **移动端适配** + SSR/SEO 首页

## 模型体系

平台内置服务端模型（共享硅基流动 Base URL/API Key，前端一键切换）：

| 模型 | 名称 |
| --- | --- |
| **默认** | `THUDM/GLM-4-32B-0414` |
| GLM-4-9B | `THUDM/GLM-4-9B-0414` |
| DeepSeek-R1-0528 | `deepseek-ai/DeepSeek-R1-0528-Qwen3-8B` |
| Qwen3-8B | `Qwen/Qwen3-8B` |
| Qwen3.5-4B | `Qwen/Qwen3.5-4B` |
| Qwen2.5-7B | `Qwen/Qwen2.5-7B-Instruct` |

也支持用户填写任意 **OpenAI 兼容自定义模型**（Base URL / API Key / 模型名），配置仅存浏览器本地。

## 快速开始（Docker 一键部署）

> 前置：Docker 24+ / Docker Compose v2；需自备硅基流动 API Key（`.env` 中配置）。

```bash
# 1. 配置环境变量
cp .env.example .env
#    填入 LLM_API_KEY（硅基流动）；可调整 LLM_MODEL 等

# 2. 一键拉起（mysql + lightrag + backend + frontend）
docker compose up -d --build
```

启动后访问：

| 服务 | 地址 |
| --- | --- |
| 前端 | http://localhost:3000 |
| 后端 API / Swagger | http://localhost:8080 / `/swagger-ui.html` |
| OpenAPI 规范 | http://localhost:8080/v3/api-docs |
| LightRAG Server | http://localhost:9621/docs |
| MySQL | localhost:3306 |

**默认账号**：`admin/admin123456`（管理员）、`user/user123456`（生产环境务必修改）。

> **LightRAG 说明**：Docker 部署中 LightRAG 使用 `Qwen/Qwen3.5-4B` + `BAAI/bge-m3`（硅基流动），并已开启**低并发 + 重试退避**以缓解免费额度限流。若遇限流，可在 `.env` 调整 `LLM_MODEL`，或改用本地 Ollama 模型实现无限流。

## 本地开发

详见 [docs/DEVELOP.md](docs/DEVELOP.md)。核心命令：

```bash
.\mvnw.cmd spring-boot:run          # 后端 :8080（dev profile 连本地 MySQL root/123456）
cd frontend && pnpm install && pnpm dev   # 前端 :3000
cd frontend && pnpm gen:api         # 依据 /v3/api-docs 重新生成 TS API 客户端
```

## API 契约

- 后端基于 springdoc 自动生成 **OpenAPI 3**（`/v3/api-docs`），前端 `@umijs/openapi` 自动生成 TS 客户端（`frontend/app/api/`），禁止手写魔法字符串。
- 统一响应：`{ code, msg, data, traceId }`。
- SSE 事件（`POST /api/chat/stream`）：`thinking` / `delta` / `references` / `done` / `error`。

主要接口：

| 模块 | 接口 |
| --- | --- |
| 认证 | `/api/auth/login|register|me|password` |
| 模型 | `/api/models` |
| 对话 | `/api/chat/stream`（SSE）、会话/消息 CRUD、`/export` |
| 知识库 | `/api/kbs` |
| 文档 | `/api/documents`（202 异步）、`/{id}/progress`（SSE） |
| 系统提示词 | `/api/prompts` |
| 管理后台 | `/api/admin/users`、`/api/admin/roles` |

## 目录结构

```
TraceQA/
├── src/main/java/edu/zjut/traceqa/   # 后端（common/config/entity/mapper/dto/service/retrieval/agent/sse/controller）
├── frontend/app/                     # 前端（pages/components/composables/stores/utils/middleware/api）
├── frontend/scripts/                 # gen-api 脚本
├── docs/DEVELOP.md                   # 开发文档
├── Dockerfile / docker-compose.yml / .env.example
└── README.md
```

## 常见问题

- **LightRAG 限流上传失败？** 已内置低并发+重试；仍失败可改用本地 Ollama（无限流）或错峰上传。
- **修改了后端接口？** `cd frontend && pnpm gen:api` 重新生成客户端。

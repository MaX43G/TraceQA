# 溯知 · TraceQA

> 《数据挖掘》课程 RAG 智能问答平台 —— 基于 **LightRAG 知识图谱** 与 **多 Agent 协同** 的智能助教系统。

溯知（TraceQA）是一个面向《数据挖掘》课程的智能问答平台。它以课程教材与 PPT 为知识源，通过 **LightRAG（图谱 + 向量双路检索）** 与
**Spring AI Alibaba 多 Agent 协同**，实现「意图识别 → 检索/搜索 → 总结」的完整 RAG 工作流，并通过 **SSE 流式推送** 实时呈现
Agent 思考过程与打字机式回答，支持 **引用溯源**、异步文档解析、RBAC 权限管理、系统提示词动态管理与对话 Markdown 导出。

---

## 目录

- [架构设计](#架构设计)
- [技术栈](#技术栈)
- [核心特性](#核心特性)
- [快速开始（Docker 一键部署）](#快速开始docker-一键部署)
- [本地开发](#本地开发)
- [API 契约](#api-契约)
- [环境变量](#环境变量)
- [目录结构](#目录结构)
- [常见问题](#常见问题)

---

## 架构设计

```
                        ┌─────────────────────────────────────────────┐
                        │               浏览器 / 用户                  │
                        └───────────────────┬─────────────────────────┘
                                            │  SSR + SSE + OpenAPI 客户端
                          ┌─────────────────▼──────────────────┐
                          │        前端（Nuxt 4 SSR）            │
                          │  Vite + Vue3 + TS + Pinia + Antd    │
                          │  · 打字机流式渲染 · 思考折叠面板      │
                          │  · 引用溯源角标 · 管理后台             │
                          │  · /api 代理解决跨域                 │
                          └─────────────────┬──────────────────┘
                                            │  /api/**（前端代理转发）
                          ┌─────────────────▼──────────────────┐
                          │      后端（Spring Boot 4.1）         │
                          │  · 统一响应 code/msg/data/traceId    │
                          │  · 全局异常处理 + 熔断降级            │
                          │  · RBAC(JWT) · MyBatis-Plus(MySQL)   │
                          │  · SSE 流式编排（SseEmitter）         │
                          └──────┬──────────────────────┬───────┘
                                 │ 多 Agent 协同          │ 文档解析/检索
                          ┌──────▼─────────┐     ┌───────▼─────────┐
                          │  LLM（硅基流动   │     │ LightRAG 官方    │
                          │  OpenAI 兼容）  │     │ Server（图+向量） │
                          └────────────────┘     └───────┬─────────┘
                                                        │ 图谱/向量存储
                                                 ┌──────▼─────────┐
                                                 │  本地文件系统    │
                                                 └────────────────┘
```

**多 Agent 工作流（SSE 实时推送节点状态）：**

```
意图识别 → 查询重写/HyDE → 图谱检索 + 向量检索 → RRF 融合 → ReRead 二次检索 → 总结生成
   │            │                  │                 │              │            │
 intent-agent  rewrite/hyde     dual-path         fusion         reread      answer-agent
   │            │                  │                 │              │            │
   └────────────┴───────── 全程通过 SSE 推送 thinking 事件 ─────────┴────────────┘
```

## 技术栈

| 层次     | 技术                                                                                                  |
|----------|-------------------------------------------------------------------------------------------------------|
| 前端     | Nuxt 4（SSR）、Vite、Vue 3、TypeScript、Pinia、Ant Design Vue、markdown-it、highlight.js              |
| API 契约 | 后端 springdoc 自动生成 OpenAPI 规范 → 前端 `@umijs/openapi` 自动生成 TS 客户端（严禁手写魔法字符串） |
| 后端     | Spring Boot 4.1、Java 25、Spring AI 2.0、Spring AI Alibaba Agent Framework、springdoc-openapi         |
| ORM      | MyBatis-Plus（严禁手写原生 SQL）                                                                      |
| 数据库   | MySQL 8（单库）、文件存储使用本地文件系统（不引入 Redis / 消息队列）                                  |
| 检索增强 | LightRAG（图谱 local + 向量 naive 双路）、RRF 融合、查询重写（Query Rewriting）、HyDE、ReRead         |
| 部署     | Dockerfile + docker-compose（mysql / lightrag / backend / frontend 一键拉起）                         |

## 核心特性

- **统一响应结构**：所有接口返回 `{ code, msg, data, traceId }`，全局错误码字典（`40001` 参数错误 / `40100` 未授权 /
  `40300` 无权限 / `42900` 频繁 / `50001` AI 不可用等），前端仅依据 `code` 判断。
- **全局异常处理**：捕获所有未处理异常，严禁将底层堆栈暴露给前端。
- **熔断与降级**：LLM 连续失败自动熔断，链路按 `Alibaba Agent → ChatClient → 纯检索上下文 → 友好提示` 逐级降级，绝不返回
  500。
- **双路检索与融合**：图谱查询（local）+ 向量查询（naive）并行执行，RRF 倒数排名融合去重。
- **多 Agent 协同**：意图识别 Agent、查询增强、检索、总结生成 Agent（Spring AI Alibaba ReAct Agent）编排为完整工作流。
- **SSE 流式思考**：后端通过 SSE 实时推送 `thinking / delta / references / done / error`
  事件，前端动态折叠面板展示思考链路 + 打字机效果。
- **引用溯源**：回答以 `[citation:N]` 标注来源，前端渲染为可点击角标，点击后高亮并滚动至引用原文。
- **异步文档解析**：上传立即返回 `202 Accepted`，后台异步执行 LightRAG 抽取，前端进度面板实时追踪。
- **对话管理**：会话/消息逻辑删除、置顶、Markdown 一键导出、单条复制。
- **用户自助管理**：自助注册、登录、修改自己的密码。
- **模型选择与自定义模型**：支持模型下拉切换；用户可填写 OpenAI 兼容的
  `Base URL + API Key + 模型名` 配置自定义模型，**仅存储在浏览器本地**，
  仅在本次问答请求中发送给自建后端调用，不上传任何第三方云端。
- **RBAC 权限管理**：角色-权限码模型，方法级 `@RequirePermission` 校验，管理员后台管理用户、角色、知识库、文档、系统提示词。

## 快速开始（Docker 一键部署）

> 前置条件：Docker 24+ / Docker Compose v2。LLM 使用硅基流动（OpenAI 兼容），需自备 API Key。

```bash
# 1. 配置环境变量
cp .env.example .env
#    编辑 .env，填入 LLM_API_KEY 等

# 2. 一键拉起（mysql + lightrag + backend + frontend）
docker compose up -d --build
```

启动后访问：

| 服务            | 地址                                  |
|-----------------|---------------------------------------|
| 前端            | http://localhost:3000                 |
| 后端 API        | http://localhost:8080                 |
| Swagger UI      | http://localhost:8080/swagger-ui.html |
| OpenAPI 规范    | http://localhost:8080/v3/api-docs     |
| LightRAG Server | http://localhost:9621/webui           |
| MySQL           | localhost:3306                        |

**默认账号**（由后端启动时自动初始化）：

| 账号  | 密码        | 角色               |
|-------|-------------|--------------------|
| admin | admin123456 | 管理员（全部权限） |
| user  | user123456  | 普通用户           |

> 生产环境请务必修改默认密码与 `APP_JWT_SECRET`。

## 本地开发

前置条件：JDK 25、Node 20+、pnpm、MySQL 8（本地已建 `traceqa` 库）。

```bash
# 1. 启动后端（默认连接本地 MySQL localhost:3306/traceqa）
.\mvnw.cmd spring-boot:run
#    dev profile 默认使用 MYSQL_USER=root / MYSQL_PASSWORD=123456（可用环境变量覆盖）

# 2. 启动 LightRAG Server（可选，未启动时系统自动降级为纯 LLM 问答）
#    见下方「LightRAG 部署」

# 3. 启动前端（npm run dev）
cd frontend
pnpm install
pnpm dev            # http://localhost:3000
```

**API 契约再生成**（后端接口变更后执行）：

```bash
# 后端运行中，执行：
cd frontend
pnpm gen:api        # 依据 http://localhost:8080/v3/api-docs 重新生成 TS 客户端
```

### LightRAG 部署

官方 Server 镜像（推荐）：

```bash
docker run -d --name lightrag -e LLM_BINDING=openai -e LLM_BASE_URL=https://api.siliconflow.cn/v1 -e LLM_API_KEY=sk-xxxx -e LLM_MODEL=THUDM/GLM-Z1-9B-0414 -e EMBEDDING_BINDING=openai -e EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1 -e EMBEDDING_API_KEY=sk-xxxx -e EMBEDDING_MODEL=BAAI/bge-m3 -e EMBEDDING_DIM=1024 -p 9621:9621 ghcr.io/hkuds/lightrag:latest
```

后端通过 `LIGHTRAG_BASE_URL`（默认 `http://localhost:9621`）调用其 `/documents/upload`、`/track_status`、`/query` 接口。

## API 契约

- 后端基于 springdoc 自动生成 **OpenAPI 3 规范**（`/v3/api-docs`）。
- 前端使用 `@umijs/openapi`（`pnpm gen:api`）依据规范自动生成类型安全的 TS 客户端，存放于 `frontend/app/api/traceqa/`。
- 统一响应结构：`{ code, msg, data, traceId }`。
- SSE 事件协议（`POST /api/chat/stream`）：

```
event: thinking    data: { "stage":"意图识别","agent":"intent-agent","status":"running","message":"..." }
event: delta       data: { "content":"回答增量..." }
event: references  data: { "references":[{"index":1,"title":"xxx.pdf","filePath":"...","content":"..."}] }
event: done        data: { "sessionId":"...","messageId":"...","title":"..." }
event: error       data: { "code":50001,"msg":"..." }
```

主要接口一览（详见 OpenAPI 文档）：

| 模块       | 接口                                                                                 |
|------------|--------------------------------------------------------------------------------------|
| 认证       | `POST /api/auth/register`、`POST /api/auth/login`、`GET /api/auth/me`                |
| 对话       | `POST /api/chat/stream`（SSE）、会话/消息 CRUD、`GET /api/chat/sessions/{id}/export` |
| 知识库     | `/api/kbs` CRUD                                                                      |
| 文档       | `POST /api/documents`（202 异步）、`GET /api/documents/{id}/progress`（SSE）         |
| 系统提示词 | `/api/prompts` CRUD + 启用                                                           |
| 管理后台   | `/api/admin/users`、`/api/admin/roles`                                               |

## 环境变量

| 变量                                     | 默认值                               | 说明                     |
|------------------------------------------|--------------------------------------|--------------------------|
| `LLM_BASE_URL`                           | `https://api.siliconflow.cn/v1`      | OpenAI 兼容 LLM 地址     |
| `LLM_API_KEY`                            | -                                    | LLM API Key              |
| `LLM_MODEL`                              | `THUDM/GLM-Z1-9B-0414`               | 对话模型                 |
| `EMBEDDING_MODEL`                        | `BAAI/bge-m3`                        | 嵌入模型（LightRAG）     |
| `EMBEDDING_DIM`                          | `1024`                               | 嵌入维度                 |
| `LIGHTRAG_BASE_URL`                      | `http://localhost:9621`              | LightRAG Server 地址     |
| `MYSQL_HOST/PORT/DATABASE/USER/PASSWORD` | `localhost/3306/traceqa/root/123456` | 数据库连接               |
| `APP_JWT_SECRET`                         | 内置开发密钥                         | JWT 签名密钥（生产必改） |
| `APP_STORAGE_ROOT`                       | `./data/files`                       | 文件存储根目录           |

## 目录结构

```
TraceQA/
├── src/main/java/edu/zjut/traceqa/
│   ├── common/          # 统一响应、错误码、异常处理、traceId、JWT、RBAC 拦截器
│   ├── config/          # LightRAG 客户端、Jackson、OpenAPI、数据初始化
│   ├── entity/ mapper/  # MyBatis-Plus 实体与数据访问
│   ├── dto/             # 请求/响应 DTO
│   ├── service/         # 认证、对话、文档、知识库、提示词、熔断降级、LLM
│   ├── retrieval/       # 查询重写 / HyDE / 双路检索 / RRF 融合 / ReRead
│   ├── agent/           # 多 Agent（Alibaba ReactAgent）编排器
│   ├── sse/             # SSE 事件发布
│   └── controller/      # REST 接口
├── frontend/
│   ├── app/             # Nuxt SSR 应用
│   │   ├── pages/       # 登录 / 智能问答 / 管理后台
│   │   ├── components/  # 聊天与后台组件
│   │   ├── composables/ # SSE 流式消费
│   │   ├── stores/      # Pinia（auth / chat）
│   │   └── utils/       # request / markdown / api-types
│   ├── api/traceqa/     # OpenAPI 自动生成的 TS 客户端
│   └── scripts/         # gen-api 脚本
├── Dockerfile / docker-compose.yml / .env.example
└── README.md
```

## 常见问题

- **如何配置自定义模型？** 在问答页工具栏的「模型」下拉框选择「自定义模型…」，填写 OpenAI 兼容的 `Base URL`（如 `https://api.openai.com/v1`）、`API Key` 与模型名。配置**仅保存在当前浏览器 localStorage**，仅在本次问答请求中发送给自建后端调用，后端不持久化，也不会上传任何第三方云端。
- **修改自己的密码？** 点击右上角用户头像 → 「修改密码」，校验原密码后更新；修改成功后需重新登录。
- **前端页面很丑 / 点击任何按钮没反应？** 这是 `npm run dev` 开发模式下 dayjs（antd 依赖）的 CJS/UMD 互操作问题，导致前端 JS 全部加载失败（无样式、无事件）。项目已在 `nuxt.config.ts` 内置修复（仅开发模式将 dayjs 及其插件重定向到 ESM 构建），**重启 `pnpm dev` 即可生效**。
- **未启动 LightRAG 时能问答吗？** 能。系统自动降级：意图识别与总结仍由 LLM 完成，检索环节返回空上下文，回答会说明资料库暂无相关内容；LLM
  不可用时再降级为纯检索上下文或友好提示。
- **上传文档一直「解析中」？** 检查 LightRAG Server 是否已启动（`http://localhost:9621/docs`），以及上传文件扩展名是否在 `
  pdf/pptx/ppt/docx/doc/md/txt` 内。
- **ID 为什么是字符串？** 后端雪花主键为 19 位数字，超出 JS 安全整数范围，故统一以字符串序列化传输，避免精度丢失。
- **修改了后端接口，前端如何同步？** 保持后端运行，执行 `cd frontend && pnpm gen:api` 重新生成 API 客户端。

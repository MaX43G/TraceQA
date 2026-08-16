# 溯知 · TraceQA 开发文档

> 《数据挖掘》课程 RAG 智能问答平台 —— 开发指南

## 1. 技术栈

| 层次 | 技术 |
| --- | --- |
| 前端 | Nuxt 4（SSR）、Vue 3、TypeScript、Pinia、Ant Design Vue、markdown-it |
| API 契约 | 后端 springdoc 生成 OpenAPI → 前端 `@umijs/openapi` 生成 TS 客户端 |
| 后端 | Spring Boot 4.1、Java 25、Spring AI Alibaba Agent、springdoc-openapi |
| ORM | MyBatis-Plus |
| 数据库 | MySQL 8（单库），本地文件系统存储文件 |
| 检索 | LightRAG（图谱 + 向量），查询重写 / HyDE / 双路检索 / RRF 融合 / ReRead |
| 部署 | Docker Compose（mysql + lightrag + backend + frontend） |

## 2. 目录结构

```
TraceQA/
├── src/main/java/edu/zjut/traceqa/
│   ├── common/          # 统一响应/错误码/异常/traceId/JWT/RBAC/分页/JSON/Web 配置
│   ├── config/          # LightRAG 客户端、OpenAI 兼容客户端、Jackson、OpenAPI、提示词默认、数据初始化
│   ├── entity/          # MyBatis-Plus 实体
│   ├── mapper/          # 数据访问接口
│   ├── dto/             # 请求/响应 DTO
│   ├── service/         # 认证、对话、文档、解析 worker、知识库、提示词、LLM、熔断、管理
│   ├── retrieval/       # 检索增强：复杂度判定、重写/HyDE、图谱/向量检索、RRF、ReRead
│   ├── agent/           # 多 Agent（意图/调度/重写/图谱/向量/融合/总结）编排器
│   ├── sse/             # SSE 事件发布
│   └── controller/      # REST 接口
├── frontend/
│   ├── app/
│   │   ├── pages/       # 首页 / 智能问答(/chat) / 登录 / 管理后台
│   │   ├── components/  # 聊天与后台组件（状态图、引用、模型选择、消息等）
│   │   ├── composables/ # SSE 流式消费
│   │   ├── stores/      # Pinia（auth / chat / model）
│   │   ├── utils/       # request / markdown / api-types
│   │   ├── middleware/  # 认证路由守卫
│   │   └── api/         # OpenAPI 自动生成的 TS 客户端
│   └── scripts/         # gen-api 脚本
├── docs/                # 文档
├── docker-compose.yml   # 一键部署
└── README.md
```

## 3. 本地开发

前置：JDK 25、Node 20+、pnpm、MySQL 8（本地建 `traceqa` 库）。

```bash
# 1. 后端（默认 dev profile 连本地 MySQL root/123456，可环境变量覆盖）
.\mvnw.cmd spring-boot:run          # http://localhost:8080

# 2. 前端
cd frontend
pnpm install
pnpm dev                             # http://localhost:3000

# 3. LightRAG（可选；未启动时系统自动降级）
docker run -d --name lightrag -p 9621:9621 \
  -e LLM_BINDING=openai -e LLM_BASE_URL=https://api.siliconflow.cn/v1 \
  -e LLM_API_KEY=sk-xxx -e LLM_MODEL=Qwen/Qwen3.5-4B \
  -e EMBEDDING_BINDING=openai -e EMBEDDING_MODEL=BAAI/bge-m3 \
  ghcr.io/hkuds/lightrag:latest
```

默认账号：`admin/admin123456`（管理员）、`user/user123456`。

### 3.1 重新生成前端 API（后端接口变更后）

```bash
cd frontend && pnpm gen:api   # 依据 http://localhost:8080/v3/api-docs 生成 TS 客户端
```

## 4. 多 Agent 工作流

```
意图识别 → 检索策略调度 → 查询重写与HyDE → 图谱检索(local+global) → 向量检索(多查询) → 融合与补全(RRF+ReRead) → 总结生成
     ↳ 简单问题（LLM 判定）：仅 向量检索 → 总结生成
```

- 调度节点用 **LLM 判定**问题复杂度（跨文档聚合/关系推理/主题归纳 → 复杂；单点事实/小文档集/叙事文本 → 简单），LLM 失败时规则兜底。
- 全程 SSE 推送 `thinking` 节点状态，前端状态图实时可视化。

## 5. 检索增强链路（RetrievalService）

| 步骤 | 说明 |
| --- | --- |
| 复杂度判定 | LLM（scenario=complexity）+ 快速预检 + 规则兜底 |
| 查询重写 / HyDE | 结合多轮历史消解指代，并行生成 |
| 图谱检索 | `local`（实体局部图）+ `global`（关系全局图）并行 |
| 向量检索 | 原问题 + 重写 + HyDE 多查询并行，`naive` 模式 |
| RRF 融合 | 双路结果按倒数排名融合去重 |
| ReRead | 从片段提取关键术语二次检索补全 |

## 6. 模型体系

- **服务端模型**：`app.models` 配置（默认 GLM-4-32B + 其余 5 个），共享硅基流动 Key/URL，前端一键切换（`serverModel` 字段）。
- **自定义模型**：用户填 OpenAI 兼容 Base URL/Key/模型名，**仅存浏览器 localStorage**，随请求发送（不持久化）。
- 模型路由（`RagAgentOrchestrator.toLlmConfig`）：
  - `serverModel` → 平台默认 URL/Key + 选中模型（OpenAiCompatClient）
  - `model+baseUrl+apiKey` → 自定义（OpenAiCompatClient）
  - 默认 → Spring AI ChatClient

## 7. 统一响应与错误码

所有 REST 接口返回 `{code, msg, data, traceId}`；前端仅按 `code` 判断。

| code | 含义 |
| --- | --- |
| 200 | 成功 |
| 40001 | 参数错误 |
| 40100 / 40101 | 未登录 / Token 失效 |
| 40300 | 无权限（RBAC） |
| 40400 | 资源不存在 |
| 50000 / 50001 | 业务异常 / AI 服务不可用 |
| 50003 | 系统繁忙 |

- 全局异常处理器统一拦截，严禁外泄堆栈。
- LLM 调用带熔断（`CircuitBreakerService`），逐级降级：Agent → ChatClient → 纯检索上下文 → 友好提示。

## 8. 数据模型

```
t_role (RBAC 角色+权限码) ── t_user (role_code)
t_knowledge_base ── t_document (异步解析状态/进度)
t_chat_session ── t_chat_message (thinking_trace/references JSON)
t_system_prompt (各 Agent 场景提示词，管理员可编辑)
```

- 雪花 ID 序列化为字符串传输（避免 JS 精度丢失）。
- 逻辑删除统一 `deleted` 字段。
- 系统提示词缺省回退 `PromptDefaults`，保证始终有提示词。

## 9. 前端关键实现

- **SSE 流式**：`useChatStream` 用 fetch + ReadableStream 解析 `event/data`，打字机渲染 + 思考状态图 + 引用角标。
- **引用溯源**：仅显示回答中实际引用的文献；点击查看全文弹窗。
- **中断**：生成中可点「停止」，前端 abort + 后端取消标志（takeWhile）即时终止。
- **多轮上下文**：最近 6 轮历史注入意图识别、查询重写与总结 prompt。
- **移动端**：≤768px 会话列表抽屉化，消息/工具栏自适应。
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
| 缓存/队列 | Redis（查询与 Agent 决策缓存、文档解析任务队列 Redis Stream） |
| 检索 | LightRAG（图谱 + 向量 + 关键词），查询重写 / HyDE / 查询分解 / 三路检索 / RRF 融合 / ReRead / LLM 精排 |
| 部署 | Docker Compose（mysql + redis + lightrag + backend + frontend） |

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

前置：JDK 25、Node 20+、pnpm、MySQL 8（本地建 `traceqa` 库）、Redis（默认 localhost:6379，可环境变量 `REDIS_HOST` 覆盖）。

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
  -e LLM_TIMEOUT=900 -e EMBEDDING_TIMEOUT=60 -e OPENAI_LLM_MAX_TOKENS=9000 \
  -e MAX_ASYNC_LLM=4 -e MAX_ASYNC_EMBEDDING=8 \
  ghcr.io/hkuds/lightrag:latest
```

> 本地运行时按需调参：`LLM_TIMEOUT`（单次抽取超时，硅基流动小模型慢，900s 较稳）、`EMBEDDING_TIMEOUT`（嵌入超时，默认 30s 偏紧）、`OPENAI_LLM_MAX_TOKENS`（抽取输出上限，防无限生成触发超时）、`MAX_ASYNC_LLM`（抽取并发，越高越快但更易限流）。

> Redis 不可用时系统自动降级（无缓存、文档任务改为直接解析），不影响核心功能。

默认账号：`admin/admin123456`（管理员）、`user/user123456`。

### 3.1 重新生成前端 API（后端接口变更后）

```bash
cd frontend && pnpm gen:api   # 依据 http://localhost:8080/v3/api-docs 生成 TS 客户端
```

## 4. 多 Agent 工作流

```
意图识别 → 检索策略调度 → 查询重写与HyDE → 图谱检索(local+global) → 向量检索(多查询+分解子问题) → 关键词检索 → 融合与补全(RRF+ReRead+LLM精排) → 总结生成
     ↳ 简单问题（LLM 判定）：仅 向量检索 → 总结生成
```

- 调度节点用 **LLM 判定**问题复杂度（跨文档聚合/关系推理/主题归纳 → 复杂；单点事实/小文档集/叙事文本 → 简单），LLM 失败时规则兜底。
- 全程 SSE 推送 `thinking` 节点状态，前端状态图实时可视化（含「关键词检索」节点）。

## 5. 检索增强链路（RetrievalService）

| 步骤 | 说明 |
| --- | --- |
| 复杂度判定 | LLM（scenario=complexity）+ 快速预检 + 规则兜底，结果缓存 |
| 查询重写 / HyDE | 结合多轮历史消解指代，并行生成；结果缓存 |
| 查询分解 | 对比/比较类问题按连接词拆分子问题，并入向量多查询 |
| 图谱检索 | `local`（实体局部图）+ `global`（关系全局图）并行，结果缓存 |
| 向量检索 | 原问题 + 重写 + HyDE + 子问题多查询并行，`naive` 模式，结果缓存 |
| 关键词检索 | scenario=keyword 提取术语，`hl_keywords` 检索（术语/编号类问题更准） |
| RRF 融合 | 三路结果按倒数排名融合去重 |
| ReRead | 从片段提取关键术语二次检索补全 |
| LLM 精排 | scenario=rerank 筛除无关片段并按相关度重排 |

> 查询/决策结果经 `RedisCacheService` 短 TTL 缓存；Redis 不可用时自动降级。
>
> **知识库粒度**：检索基于单一 LightRAG 全局索引，**不区分知识库、不做按库选择或隔离**——所有已入库文档都会参与检索。上传时指定知识库仅用于归档与管理。

### 5.1 用户禁用

- `AdminService.updateUserStatus` 禁用（status=0）时调用 `StpUtil.logout(userId)` 立即踢出全部会话。
- `WebConfig.checkUserEnabled()` 拦截器每请求校验（40100 强制登出）；`AuthService.currentUser()` 对禁用账号拒绝并注销，形成双重防线。

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

### 8.1 文档切分与入库（DocumentParseWorker）

- 文本（md/txt）仅当文件 **> 1MB** 才切块（`TEXT_SPLIT_THRESHOLD_BYTES`），小文档整体提交为一个 LightRAG 文档——避免按 100KB 无脑切块导致 LLM 抽取调用成倍放大（700KB 文档曾因切 7 块导致上百次抽取，单次超时即失败）。
- PDF 仅 > 2MB 才切块；其余格式整体上传。
- 块间隔 `PART_INTERVAL_MS=1000` 限速；单块轮询 `MAX_POLL_TIMES×POLL_INTERVAL_MS≈30 分钟`。
- 大文档用 `MAX_PARALLEL_INSERT`（Docker 默认 2）限制并行入库，避免瞬时打爆 LLM 限流。

## 9. 前端关键实现

- **SSE 流式**：`useChatStream` 用 fetch + ReadableStream 解析 `event/data`，打字机渲染 + 思考状态图 + 引用角标。
- **引用溯源**：仅显示回答中实际引用的文献；点击查看全文弹窗。
- **中断**：生成中可点「停止」，前端 abort + 后端取消标志（takeWhile）即时终止。
- **多轮上下文**：最近 6 轮历史注入意图识别、查询重写与总结 prompt。
- **移动端**：≤768px 会话列表抽屉化，消息/工具栏自适应。
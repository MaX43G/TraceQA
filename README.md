# 溯知 · TraceQA

> 《数据挖掘》智能问答平台 —— 基于知识图谱与向量检索，多 Agent 协同的智能助教系统（微服务架构）。

溯知（TraceQA）以课程教材与 PPT 为知识源，通过 **LightRAG（图谱 + 向量双路检索）** 与 **Spring AI Alibaba 多 Agent 协同**
，实现「意图识别 → 检索调度 → 检索/搜索 → 总结」的完整工作流；SSE 流式推送思考状态与打字机回答，支持引用溯源、随时中断、模型自由切换、多轮对话与移动端访问。平台进一步支持
**语音输入**（Web Speech API）与**「猜你想问」智能追问**，并接入 **MinIO 对象存储**统一管理用户文件（头像）。

后端已由单体重构为 **Spring Cloud 微服务**：以 **Nacos** 服务注册发现 + **Spring Cloud Gateway** 网关（负载均衡 + Sa-Token 统一鉴权 + OpenAPI 聚合），
按业务边界拆分为 **用户 / 文件 / 知识库 / 问答 / 管理** 五个高内聚、低耦合的微服务，各服务间通过 **OpenFeign** 完成 RPC 调用，共享一个 `common` 通用库。

---

## 目录

- [架构设计](#架构设计)
- [核心特性](#核心特性)
- [微服务划分](#微服务划分)
- [端口规划](#端口规划)
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
                         │       浏览器 / 用户          │
                         └──────────────┬───────────────┘
                                        │  HTTPS（80 / 443 / 6115）
                        ┌───────────────▼────────────────┐
                        │   Caddy 反向代理（前端入口）   │
                        └───────────────┬────────────────┘
                        ┌───────────────▼────────────────┐
                        │     前端（Nuxt 4 SSR）         │
                        │  /api/** 经 Nitro 代理 → 网关  │
                        └───────────────┬────────────────┘
                                        │  /api/**、/lightrag-webui/**、/grafana/**…
                        ┌───────────────▼─────────────────┐
                        │   traceqa-gateway（Spring Cloud │
                        │   Gateway · WebFlux）           │
                        │   Nacos 负载均衡 + Sa-Token 鉴权│
                        │   OpenAPI 聚合（接口文档）      │
                        └───┬────┬────┬────┬────┬─────────┘
              ┌─────────────┘    │    │    │    └──────────────┐
        ┌─────▼──────┐ ┌────────▼───┐ ┌─▼────────┐ ┌─────────▼──┐ ┌────────▼──────┐
        │user-service│ │file-service│ │kb-service│ │ qa-service │ │ admin-service │
        │ 用户·认证  │ │ MinIO 文件 │ │ 知识库·  │ │对话·多Agent│ │ 公告·监控·    │
        │ ·RBAC 管理 │ │ 上传下载   │ │ 文档解析 │ │ ·检索·LLM  │ │健康·可观测代理│
        └─────┬──────┘ └─────┬──────┘ └─┬────────┘ └──────┬─────┘ └───────┬───────┘
              │  OpenFeign    │         │  OpenFeign      │               │
              │◄──────────────►│        │◄────────────────┘◄──────────────┘ (指标聚合)
              │  (头像字节上传) │
        ┌─────▼────────────────▼───────┐ ┌────▼─────────────┐ ┌──────────────┐
        │ MySQL（独立库 per 服务）     │ │ LightRAG 官方    │ │ Redis（缓存  │
        │ traceqa_user/kb/qa/admin     │ │ Server（图+向量+ │ │ ·队列·熔断·  │
        └──────────────────────────────┘ │  关键词）        │ │ sa-token）   │
                                         └──────────────────┘ └──────────────┘
                        ┌──────────── Nacos 注册中心 / 配置中心 ──────────────┐
                        └─────────────────────────────────────────────────────┘
        ┌────────────────────────── 可观测性（仅管理员）──────────────────────────┐
        │ admin-service Actuator ──Prometheus──▶ Grafana ── 经网关/管理服务代理   │
        └─────────────────────────────────────────────────────────────────────────┘
```

**多 Agent 工作流（SSE 实时推送，前端状态图可视化）：**

```
意图识别 → 检索策略调度 → 查询重写与HyDE → 图谱检索(local+global) → 向量检索(多查询) → 关键词检索 → 融合与补全(RRF+ReRead+LLM精排) → 总结生成
```

调度节点按问题复杂度分流：**简单问题仅向量检索（更快）**，**复杂问题走完整聚合链路**。

## 核心特性

- **微服务架构**：Spring Cloud Gateway 网关 + Nacos 注册发现 + OpenFeign RPC，用户/文件/知识库/问答/管理五个高内聚服务
- **统一鉴权**：网关基于 Sa-Token（Redis 共享登录态）统一登录校验，并将用户信息以请求头透传下游；方法级 RBAC 注解二次鉴权
- **统一响应** `{code,msg,data,traceId}` + 全局错误码 + 全局异常处理（绝不外泄堆栈）
- **熔断降级**：LLM 失败自动熔断（Redis 状态机），逐级降级（Agent → ChatClient → 纯检索 → 友好提示）
- **三路混合检索**：查询重写、HyDE、图谱 (local+global)+向量 (多查询)+关键词 (hl_keywords)、RRF 融合、ReRead 补全、语义重排
- **Agentic 检索策略** + **查询分解**（对比类问题拆分）
- **Redis 缓存**：查询/决策短 TTL 缓存，降低 LLM 调用与延迟
- **多轮对话**、**SSE 流式**、**引用溯源**、**模型自由切换**
- **异步文档解析**：.md/.txt 上传与 zip 批量导入，内容指纹去重，Redis Stream 任务队列 + LightRAG 入库
- **RBAC 管理后台**、**用户禁用即时生效**
- **可观测性**：管理服务 Actuator + Prometheus + Grafana，经代理统一鉴权访问
- **语音输入**（Web Speech API）、**猜你想问**、**头像与个人信息**（MinIO + cropperjs）、**公告栏**

## 微服务划分

| 模块 | 职责 | 独立数据表 | 端口 |
|------|----------------|------------|------|
| `traceqa-common` | 通用库：统一响应/异常/实体/DTO/VO/RBAC/Feign 契约/LightRAG 客户端 | — | 库 |
| `traceqa-gateway` | 网关：Nacos 负载均衡路由、Sa-Token 鉴权、OpenAPI 聚合 | — | 8080 |
| `traceqa-user-service` | 用户注册登录、用户/角色 RBAC 管理、头像（经文件服务） | t_user, t_role | 8081 |
| `traceqa-file-service` | MinIO 对象存储，统一文件上传下载 | — | 8083 |
| `traceqa-kb-service` | 知识库与文档管理、异步解析入库（LightRAG） | t_knowledge_base, t_document | 8084 |
| `traceqa-qa-service` | 会话消息、多 Agent 检索编排、LLM、系统提示词、模型 | t_chat_session, t_chat_message, t_system_prompt | 8085 |
| `traceqa-admin-service` | 系统公告、监控聚合、健康检查、可观测性反向代理 | t_announcement | 8086 |

微服务间通过 **OpenFeign** 完成 RPC：用户服务调用文件服务上传头像字节；管理服务拉取知识库服务队列统计与问答服务熔断状态。

## 端口规划

服务器对外 **仅开放 80 / 443 / 6115 / 6116** 四个端口（Caddy 前端 HTTPS 与 MinIO 头像直链）；其余端口在 Docker 容器内开放供微服务间通信，
需在服务器防火墙阻断外网访问。

| 服务 | 容器内端口 | 宿主端口 | 对外 |
|------|-----------|----------|------|
| Caddy（前端 HTTPS） | 80/443 | 80 / 443 / 6115 | ✅ 公开 |
| MinIO（S3 头像直链） | 9000 | 6116 | ✅ 公开 |
| 网关 gateway | 8080 | 6114 | 内网 |
| 用户服务 | 8081 | 6122 | 内网 |
| 文件服务 | 8083 | 6123 | 内网 |
| 知识库服务 | 8084 | 6124 | 内网 |
| 问答服务 | 8085 | 6125 | 内网 |
| 管理服务 | 8086 | 6126 | 内网 |
| Nacos | 8848/9848 | 6120/6121 | 内网 |
| MySQL | 3306 | 6118 | 内网 |
| Redis | 6379 | 6117 | 内网 |
| LightRAG | 9621 | 6119 | 内网 |
| Prometheus | 9090 | 6127 | 内网 |
| Grafana | 3000 | 6128 | 内网 |

## 模型体系

平台内置服务端模型（共享硅基流动 Base URL/API Key，前端一键切换），也支持用户填写任意 **OpenAI 兼容自定义模型**。

| 模型             | 名称                                    |
|------------------|-----------------------------------------|
| **默认**         | `THUDM/GLM-4-9B-0414`                   |
| GLM-4-9B         | `THUDM/GLM-4-9B-0414`                   |
| DeepSeek-R1-0528 | `deepseek-ai/DeepSeek-R1-0528-Qwen3-8B` |
| Qwen3-8B         | `Qwen/Qwen3-8B`                         |
| Qwen3.5-4B       | `Qwen/Qwen3.5-4B`                       |
| Qwen2.5-7B       | `Qwen/Qwen2.5-7B-Instruct`              |

## 快速开始（Docker 一键部署）

> 前置：Docker 24+ / Docker Compose v2；需自备硅基流动 API Key（`.env` 中配置）。

```bash
# 1. 配置环境变量
cp .env.example .env
#    填入 LLM_API_KEY（硅基流动）；可调整 LLM_MODEL 等

# 2. 一键拉起（mysql + redis + nacos + lightrag + minio + gateway + 5 微服务 + frontend + prometheus + grafana）
docker compose up -d --build
```

> **已有 MySQL 数据卷需迁移**：`docker-entrypoint-initdb.d` 仅在 MySQL 数据卷首次初始化时执行。若本机此前已运行过单体版 TraceQA（已存在 `traceqa` 库），
> 首次启动微服务会报 `Access denied for user 'traceqa'@'%' to database 'traceqa_user'`。补建独立库并授权即可（表结构由各服务 Flyway 自动创建）：
> ```bash
> docker compose exec mysql bash /docker-entrypoint-initdb.d/migrate-existing.sh
> docker compose up -d --build   # 启动服务，Flyway 自动建表
> ```
启动后访问：

| 服务                           | 地址                                                                                                     |
|--------------------------------|----------------------------------------------------------------------------------------------------------|
| 前端（HTTPS）                  | https://localhost:6115                                                                                   |
| 网关 / 后端 API                | http://localhost:6114                                                                                    |
| OpenAPI 聚合文档（Swagger UI） | http://localhost:6114/swagger-ui.html                                                                    |
| MinIO 对象存储（S3，对外公开） | http://localhost:6116                                                                                    |
| Nacos 控制台                   | http://localhost:6120/nacos（默认 nacos/nacos）                                                          |
| MySQL                          | localhost:6118                                                                                           |
| Redis                          | localhost:6117                                                                                           |

**默认账号**：`admin` / `user`（密码由环境变量 `DEFAULT_ADMIN_PASSWORD` / `DEFAULT_USER_PASSWORD` 注入，见 `.env`；未配置则不创建默认账号。生产环境务必修改）。

> **HTTPS / 麦克风（重要）**：浏览器要求页面为 **安全上下文**才允许调用麦克风（语音输入）。前端经 **Caddy** 在 `:6115` 提供
> HTTPS（`tls internal` 自签证书）。无公网域名时可将 Caddy 内部 CA 安装为受信根证书获得绿锁：
> ```bash
> docker compose exec caddy cat /data/caddy/pki/authorities/local/root.crt   # 导出根证书并导入受信任根
> ```
> 若配置了公网域名，改 `frontend/Caddyfile` 的 `tls` 即可自动申请 Let's Encrypt 证书。
>
> **MinIO（HTTPS）**：S3 API 经 Caddy 在 `:6116` 提供 HTTPS，`MINIO_PUBLIC_URL` 设为服务器公网 HTTPS 地址（如
> `https://121.41.72.189:6116`）。后端自动建桶并设置 **公共只读策略**。
>
> **文档格式**：仅支持 `.md` / `.txt`（zip 批量导入）；PDF/PPT/Word 请先转换为 Markdown。
>
> **LightRAG**：使用 `Qwen/Qwen3.5-4B` + `BAAI/bge-m3`，低并发 + 重试退避 + 超时调优以缓解限流。
>
> **知识库**：系统使用 **全部知识库**检索，不做按库隔离；删除知识库不会清除 LightRAG 图谱索引中的旧内容。

## 本地开发

详见 [docs/DEVELOP.md](docs/DEVELOP.md)。核心命令：

```bash
# 1. 基础设施（Nacos + MySQL + Redis）
docker compose up -d mysql redis nacos lightrag minio

# 2. 编译后端（多模块）
.\mvnw.cmd clean package -DskipTests

# 3. 逐个启动微服务（IDEA 运行各模块 Application，或 java -jar 对应 jar）
java -jar traceqa-gateway/target/*.jar
java -jar traceqa-user-service/target/*.jar
# ... file / kb / qa / admin

# 4. 前端
cd frontend && pnpm install && pnpm dev   # :3000（/api 代理到 localhost:8080 网关）

# 5. 重新生成前端 API 客户端（接口变更后）
cd frontend && pnpm gen:api
```

## API 契约

- 网关聚合各微服务 springdoc 生成的 **OpenAPI 3**，统一入口 `/swagger-ui.html`（Swagger UI）与 `/v3/api-docs`。
- 前端基于 `/v3/api-docs` 用 `@umijs/openapi` 自动生成 TS 客户端。
- 统一响应：`{ code, msg, data, traceId, detail? }`。
- SSE 事件（`POST /api/chat/stream`）：`thinking` / `delta` / `references` / `stats` / `done` / `error`。

主要接口（网关按路径路由到对应微服务）：

| 模块       | 接口                                                               | 路由到            |
|------------|--------------------------------------------------------------------|-------------------|
| 认证/管理   | `/api/auth/*`、`/api/admin/*`                                      | user-service      |
| 文件       | `/api/files/*`                                                     | file-service      |
| 知识库/文档 | `/api/kbs/*`、`/api/documents/*`                                   | kb-service        |
| 对话/模型/提示词 | `/api/chat/*`、`/api/models`、`/api/prompts/*`                 | qa-service        |
| 公告/监控/健康 | `/api/announcement/*`、`/api/monitor/*`、`/api/health`         | admin-service     |
| 代理       | `/lightrag-webui/**`、`/grafana/**`、`/prometheus/**`              | admin-service     |

## 目录结构

```
TraceQA/
├── traceqa-common/             # 通用库（api/exception/enums/model/context/rbac/config/client/convert/util）
├── traceqa-gateway/            # 网关（Spring Cloud Gateway + Nacos + Sa-Token + OpenAPI 聚合）
├── traceqa-user-service/       # 用户服务（auth + RBAC）
├── traceqa-file-service/       # 文件服务（MinIO）
├── traceqa-kb-service/         # 知识库服务（kb + document + 解析 worker）
├── traceqa-qa-service/         # 问答服务（chat + agent/retrieval + llm + prompts + models）
├── traceqa-admin-service/      # 管理服务（announcement + monitor + health + 可观测代理）
├── frontend/app/               # 前端（pages/components/composables/stores/utils/middleware/api）
├── db/                         # 各微服务独立数据库初始化脚本与表结构
├── docker/                     # 各服务 Dockerfile + Prometheus / Grafana 配置
├── docs/DEVELOP.md             # 开发文档
├── pom.xml                     # 父工程（多模块聚合）
├── docker-compose.yml          # 一键部署
└── README.md
```

## 常见问题

- **服务如何被发现？** 各微服务与网关注册到 Nacos，网关经 `lb://服务名` 负载均衡路由。
- **登录态如何跨服务生效？** sa-token 登录态存于共享 Redis；网关校验后将用户信息以 `X-User-Id` 等请求头透传下游，下游解析为 `UserContext`。
- **如何查看接口文档？** 访问 `http://localhost:6114/swagger-ui.html`（网关聚合所有微服务）。
- **LightRAG 限流上传失败 / 上传慢？** 已内置低并发 + 重试 + 超时调优，小文档不再切块；仍慢可改本地 Ollama。
- **禁用用户还能继续对话？** 网关校验登录 + 各服务二次鉴权，禁用即踢下线并阻止再次登录。
- **修改了后端接口？** `cd frontend && pnpm gen:api` 重新生成客户端。
- **只开放 80/443/6115/6116？** 微服务各端口在容器内开放供内部通信；请在服务器防火墙阻断 6114、6122-6128、6117-6121 等端口的外网访问。
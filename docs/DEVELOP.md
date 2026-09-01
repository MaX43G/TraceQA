# 溯知 · TraceQA 开发文档

> 《数据挖掘》课程 RAG 智能问答平台 —— 微服务架构开发指南

## 1. 技术栈

| 层次       | 技术                                                                             |
|------------|----------------------------------------------------------------------------------|
| 前端       | Nuxt 4（SSR）、Vue 3、TypeScript、Pinia、Ant Design Vue、markdown-it             |
| 网关       | Spring Cloud Gateway 2025.1（WebFlux）、Sa-Token（响应式）、springdoc 聚合       |
| 微服务框架 | Spring Cloud Alibaba 2025.1（Nacos 注册发现/配置）、OpenFeign（服务间 RPC）      |
| 后端       | Spring Boot 4.1、Java 25（虚拟线程）、Spring AI Alibaba Agent、springdoc-openapi |
| 数据迁移   | Flyway（各服务独立库版本化迁移）                                                 |
| 容错       | Resilience4j（LLM 熔断）、OpenFeign（服务间 RPC）                                |
| 鉴权       | Sa-Token（Redis 共享登录态）+ 网关统一校验 + 方法级 RBAC 注解                    |
| ORM        | MyBatis-Plus（各服务独立库）                                                     |
| 数据库     | MySQL 8（traceqa_user / kb / qa / admin 四库）                                   |
| 对象存储   | MinIO（统一管理用户文件，如头像；S3 兼容，公开端口 6116）                        |
| 缓存/队列  | Redis（查询与决策缓存、文档解析任务队列、sa-token、熔断状态）                    |
| 注册中心   | Nacos（服务发现/配置）                                                           |
| 检索       | LightRAG（图谱 + 向量 + 关键词），Agentic 策略/重写/HyDE/分解/RRF/ReRead/重排    |
| 可观测性   | Actuator + Micrometer/Prometheus + Prometheus + Grafana（管理员）                |
| 部署       | Docker Compose（微服务 + 基础设施 + 前端 + Caddy）                               |

## 2. 微服务架构

```
浏览器 → Caddy(:80/443/6115) → 前端 Nuxt(:3000) → 网关 gateway(:8080)
   → user-service / file-service / kb-service / qa-service / admin-service
注册中心 Nacos；服务间 OpenFeign；登录态 Redis 共享；数据库各服务独立库
```

| 模块                  | 端口 | 数据表                                          | 职责                                                      |
|-----------------------|------|-------------------------------------------------|-----------------------------------------------------------|
| traceqa-common        | 库   | —                                               | 统一响应/异常/实体/DTO/VO/RBAC/Feign 契约/LightRAG 客户端 |
| traceqa-gateway       | 8080 | —                                               | Nacos 路由、Sa-Token 鉴权、OpenAPI 聚合                   |
| traceqa-user-service  | 8081 | t_user, t_role                                  | 注册登录、RBAC 用户/角色管理、头像                        |
| traceqa-file-service  | 8083 | —                                               | MinIO 文件上传下载                                        |
| traceqa-kb-service    | 8084 | t_knowledge_base, t_document                    | 知识库、文档解析入库                                      |
| traceqa-qa-service    | 8085 | t_chat_session, t_chat_message, t_system_prompt | 会话、多 Agent 检索、LLM、提示词、模型                    |
| traceqa-admin-service | 8086 | t_announcement                                  | 公告、监控、健康、可观测代理                              |

### 2.1 网关与鉴权链路

- 网关通过 `SaReactorFilter` 对 `/api/**` 统一登录校验（白名单：`/api/auth/login`、`/api/auth/register`、`/api/health`、
  `/api/announcement/active`）。
- 校验通过后，`AuthHeaderGlobalFilter` 将当前用户信息写入请求头（`X-User-Id`、`X-Username`、`X-User-Role`、
  `X-User-Permissions`、`X-Trace-Id`）透传下游。
- 各服务的 `UserContextFilter` 解析请求头为 `UserContext`；`RbacAspect` 依据 `@RequirePermission / @RequireRole` 注解完成方法级
  RBAC 二次鉴权。
- 登录态由用户服务写入共享 Redis（`SaTokenDaoRedis`），网关与各服务共用同一套 Redis 实现跨服务登录态。

### 2.2 服务间 RPC（OpenFeign）

- `common/client/FileClient`（→ file-service）：上传头像字节，返回 MinIO URL，被用户服务调用。
- `common/client/KbClient`（→ kb-service）：获取文档解析队列统计，被管理服务调用。
- `common/client/QaClient`（→ qa-service）：获取 LLM 熔断状态，被管理服务调用。

### 2.3 端口规划

服务器对外仅开放 **80 / 443 / 6115**（前端 HTTPS）与 **6116**（MinIO 头像直链）；其余微服务/中间件端口在容器内开放供内部通信，需在服务器防火墙阻断外网访问。

## 3. 本地开发

前置：JDK 25、Node 20+、pnpm、Docker（起 Nacos + MySQL + Redis + LightRAG + MinIO）、硅基流动 API Key。

```bash
# 1. 基础设施
docker compose up -d mysql redis nacos lightrag minio

# 2. 编译后端（多模块聚合）
.\mvnw.cmd clean package -DskipTests

# 3. 启动各微服务（可分别运行各模块的 *Application）
#    gateway(8080) → user(8081) / file(8083) / kb(8084) / qa(8085) / admin(8086)
#    IDEA 中为每个模块添加 Spring Boot Run Configuration 并逐个运行

# 4. 前端（/api 代理到 localhost:8080 网关）
cd frontend && pnpm install && pnpm dev   # http://localhost:3000
```

> 各服务 `application.yaml` 数据库连接默认使用 `MYSQL_HOST/MYSQL_PORT/MYSQL_DATABASE_xxx` 环境变量，本地可用环境变量覆盖；Nacos
> 地址 `NACOS_SERVER_ADDR` 默认 `localhost:8848`。

默认账号：`admin/admin123456`（管理员）、`user/user123456`。

### 3.1 重新生成前端 API（接口变更后）

```bash
cd frontend && pnpm gen:api   # 依据 http://localhost:8080/v3/api-docs 生成 TS 客户端
```

## 4. 数据库（各服务独立库）

MySQL 容器初始化脚本位于 `db/`：

- `01-init-databases.sh`：创建 `traceqa_user / traceqa_kb / traceqa_qa / traceqa_admin` 四库并授权。
- 各服务的数据库表通过Flyway版本化迁移

## 5. 多 Agent 工作流（qa-service）

```
意图识别 → 检索策略调度 → 查询重写与HyDE → 图谱检索(local+global) → 向量检索(多查询+分解子问题) → 关键词检索 → 融合与补全(RRF+ReRead+LLM精排) → 总结生成
     ↳ 简单问题（LLM 判定）：仅 向量检索 → 总结生成
```

- 调度节点用 LLM 判定问题复杂度；全程 SSE 推送 `thinking` 节点状态。
- 完整检索/Agent 实现见 `RagAgentOrchestrator`、`RetrievalService`、`IntentAgent`、`AnswerAgent`、`RagAgents`。
- SSE 事件：`thinking` / `delta` / `references` / `stats` / `done` / `error`。

## 6. 文档解析与入库（kb-service）

- 支持 `.md/.txt` 上传与 zip 批量导入，SHA-256 内容指纹去重。
- 文档经 Redis Stream 队列（`doc:queue`）异步消费，`DocumentParseWorker` 切块（大文档）后逐块写入 LightRAG；重试耗尽进入死信
  `doc:queue:dead`。
- 解析进度由用户触发刷新时查询 LightRAG 聚合（不主动轮询）。

## 7. 统一响应与错误码

所有 REST 接口返回 `{code, msg, data, traceId, detail?}`；前端仅按 `code` 判断。

| code          | 含义                     |
|---------------|--------------------------|
| 200           | 成功                     |
| 40001         | 参数错误                 |
| 40100 / 40101 | 未登录 / Token 失效      |
| 40300         | 无权限（RBAC）           |
| 40400         | 资源不存在               |
| 50000 / 50001 | 业务异常 / AI 服务不可用 |
| 50002 / 50003 | 文件处理失败 / 系统繁忙  |

## 8. 数据模型

```
traceqa_user : t_role ── t_user(role_code, avatar)
traceqa_kb   : t_knowledge_base ── t_document(异步解析状态/进度)
traceqa_qa   : t_chat_session ── t_chat_message(thinking_trace/references JSON)；t_system_prompt
traceqa_admin: t_announcement
```

- 雪花 ID 序列化为字符串（避免 JS 精度丢失）；逻辑删除统一 `deleted` 字段。

### 8.1 头像与 MinIO（file-service）

- `MinioConfig` 提供 `MinioClient`；`FileStorageService` 负责建桶、上传并返回公开 URL，自动设置桶公共只读策略。
- 用户服务经 `FileClient` 调用文件服务上传头像字节，回写 `t_user.avatar`；`MINIO_PUBLIC_URL` 配置对外公开地址。

## 9. 可观测性（admin-service，仅管理员）

- 管理服务暴露 Actuator 指标（`/actuator/prometheus`），Prometheus 抓取，Grafana 可视化。
- `MonitorService` 经 OpenFeign 聚合 kb-service 队列统计与 qa-service 熔断状态，本地采集 JVM 运行时与活跃会话。
- `/grafana/**`、`/prometheus/**`、`/lightrag-webui/**` 由管理服务反向代理（管理员 Cookie 鉴权）访问。

## 10. 部署

```bash
cp .env.example .env   # 填入 LLM_API_KEY 等
docker compose up -d --build
```

镜像构建采用多阶段：`docker/*.Dockerfile` 基于根构建上下文，`-pl <module> -am` 编译对应模块并打包运行镜像； Maven 依赖经
BuildKit 缓存挂载（`--mount=type=cache,target=/root/.m2`）避免每次重建重复下载。 生产务必修改默认密码与密钥。
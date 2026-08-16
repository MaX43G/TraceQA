-- ============================================================
-- 溯知 / TraceQA 数据库结构（MySQL 8.x）
-- 用于 docker-compose 中 MySQL 容器首次启动初始化（docker-entrypoint-initdb.d）
-- 本地环境已由 DBA 预建表，无需再执行本脚本
-- ============================================================

CREATE DATABASE IF NOT EXISTS traceqa DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE traceqa;

-- 角色表（RBAC）
CREATE TABLE IF NOT EXISTS t_role (
    id          BIGINT       NOT NULL PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    permissions VARCHAR(1024) DEFAULT '',
    description VARCHAR(255) DEFAULT '',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      DEFAULT 0
);

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT       NOT NULL PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(128) NOT NULL,
    nickname    VARCHAR(64)  DEFAULT '',
    role_code   VARCHAR(32)  DEFAULT 'USER',
    status      TINYINT      DEFAULT 1,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      DEFAULT 0
);

-- 知识库表
CREATE TABLE IF NOT EXISTS t_knowledge_base (
    id          BIGINT       NOT NULL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512) DEFAULT '',
    course      VARCHAR(128) DEFAULT '',
    status      TINYINT      DEFAULT 1,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      DEFAULT 0
);

-- 文档表（异步解析进度）
CREATE TABLE IF NOT EXISTS t_document (
    id                BIGINT       NOT NULL PRIMARY KEY,
    knowledge_base_id BIGINT       NOT NULL,
    original_name     VARCHAR(255) NOT NULL,
    stored_path       VARCHAR(512) DEFAULT '',
    file_type         VARCHAR(16)  DEFAULT '',
    file_size         BIGINT       DEFAULT 0,
    status            VARCHAR(16)  DEFAULT 'PENDING',
    track_id          VARCHAR(128) DEFAULT '',
    chunk_count       INT          DEFAULT 0,
    entity_count      INT          DEFAULT 0,
    relation_count    INT          DEFAULT 0,
    error_msg         VARCHAR(512) DEFAULT '',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted           TINYINT      DEFAULT 0
);

-- 聊天会话表
CREATE TABLE IF NOT EXISTS t_chat_session (
    id                BIGINT       NOT NULL PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    title             VARCHAR(128) DEFAULT '新对话',
    knowledge_base_id BIGINT       DEFAULT NULL,
    pinned            TINYINT      DEFAULT 0,
    status            TINYINT      DEFAULT 1,
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted           TINYINT      DEFAULT 0
);

-- 聊天消息表
CREATE TABLE IF NOT EXISTS t_chat_message (
    id             BIGINT   NOT NULL PRIMARY KEY,
    session_id     BIGINT   NOT NULL,
    role           VARCHAR(16) NOT NULL,
    content        TEXT,
    thinking_trace TEXT,
    `references`     TEXT,
    latency_ms     BIGINT   DEFAULT 0,
    status         TINYINT  DEFAULT 1,
    create_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted        TINYINT  DEFAULT 0
);

-- 系统提示词表
CREATE TABLE IF NOT EXISTS t_system_prompt (
    id          BIGINT       NOT NULL PRIMARY KEY,
    scenario    VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) DEFAULT '',
    content     TEXT,
    enabled     TINYINT      DEFAULT 1,
    remark      VARCHAR(255) DEFAULT '',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      DEFAULT 0
);

-- 索引（首次初始化时表为空，无需 IF NOT EXISTS）
CREATE INDEX idx_doc_kb ON t_document (knowledge_base_id);
CREATE INDEX idx_msg_session ON t_chat_message (session_id);
CREATE INDEX idx_session_user ON t_chat_session (user_id);
CREATE INDEX idx_prompt_scenario ON t_system_prompt (scenario);

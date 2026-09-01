-- 溯知 / TraceQA 知识库服务初始表结构（Flyway V1）
-- t_knowledge_base、t_document

CREATE TABLE IF NOT EXISTS t_knowledge_base
(
    id          BIGINT       NOT NULL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512) DEFAULT '',
    course      VARCHAR(128) DEFAULT '',
    status      TINYINT      DEFAULT 1,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      DEFAULT 0
);

CREATE TABLE IF NOT EXISTS t_document
(
    id                BIGINT       NOT NULL PRIMARY KEY,
    knowledge_base_id BIGINT       NOT NULL,
    original_name     VARCHAR(255) NOT NULL,
    stored_path       VARCHAR(512) DEFAULT '',
    file_type         VARCHAR(16)  DEFAULT '',
    file_size         BIGINT       DEFAULT 0,
    status            VARCHAR(16)  DEFAULT 'PENDING',
    track_id          VARCHAR(128) DEFAULT '',
    content_hash      VARCHAR(64)  DEFAULT '',
    part_total        INT          DEFAULT 1,
    part_done         INT          DEFAULT 0,
    chunk_count       INT          DEFAULT 0,
    entity_count      INT          DEFAULT 0,
    relation_count    INT          DEFAULT 0,
    error_msg         VARCHAR(512) DEFAULT '',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted           TINYINT      DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_doc_kb ON t_document (knowledge_base_id);